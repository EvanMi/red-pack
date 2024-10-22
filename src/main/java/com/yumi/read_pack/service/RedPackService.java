package com.yumi.read_pack.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.yumi.read_pack.common.CodeDescEnum;
import com.yumi.read_pack.common.RedisKeyConstants;
import com.yumi.read_pack.db.mapper.RedPackMapper;
import com.yumi.read_pack.db.mapper.RedPackOrderMapper;
import com.yumi.read_pack.db.mapper.RedPackRecordMapper;
import com.yumi.read_pack.domain.po.RedPack;
import com.yumi.read_pack.domain.po.RedPackOrder;
import com.yumi.read_pack.domain.po.RedPackOrderStatus;
import com.yumi.read_pack.domain.po.RedPackRecord;
import com.yumi.read_pack.domain.po.RedPackRecordStatus;
import com.yumi.read_pack.domain.po.RedPackStatus;
import com.yumi.read_pack.domain.po.RedPackType;
import com.yumi.read_pack.dto.RedPackGrabCommand;
import com.yumi.read_pack.dto.RedPackGrabResult;
import com.yumi.read_pack.dto.RedPackViewCommand;
import com.yumi.read_pack.dto.RedPackViewResult;
import com.yumi.read_pack.mq.MqManager;
import com.yumi.read_pack.util.RedPackSplitUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedPackService {
    private final RedPackMapper redPackMapper;
    private final RedPackRecordMapper redPackRecordMapper;
    private final RedPackOrderMapper redPackOrderMapper;

    private final StringRedisTemplate stringRedisTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MqManager mqManager;

    private final Cache<String, Object> caffeineCache;;

    @Value("${red.pack.cache.timeout.seconds}")
    private Integer redPackCacheTimeoutSeconds;

    @Value("${red.pack.info.timeout.seconds}")
    private Integer redPackInfoTimeoutSeconds;


    public RedPackGrabResult grabRedPack(RedPackGrabCommand redPackGrabCommand) {
        log.info("redPackGrabCommand: {}", redPackGrabCommand);
        //简单判断一下用户是不是在该群里
        if (null != redPackGrabCommand.getGroupId()) {
            //模拟判断完成
            log.info("当前用户: {} 属于群组: {}", redPackGrabCommand.getUserId(), redPackGrabCommand.getGroupId());
        }
        String redPackRedisMapKey = RedisKeyConstants.redPackRemainPrefix + redPackGrabCommand.getRedPackId();
        RedPackGrabResult redPackGrabResult = new RedPackGrabResult();
        redPackGrabResult.setGrabbed(false);
        redPackGrabResult.setMoney(0L);
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(redPackRedisMapKey))) {
            return redPackGrabResult;
        }

        //加个简单限流+锁 （看起来不安全，但是一般都是足够使用了）
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(RedisKeyConstants.redPackGrabUserLockPrefix + redPackGrabCommand.getUserId(),
                redPackGrabCommand.getUserId().toString(), Duration.ofSeconds(1));
        if (Boolean.FALSE.equals(locked)) {
            return redPackGrabResult;
        }

        //如果已经抢过了，不能重复抢
        List<String> strRange = stringRedisTemplate.opsForList().range(RedisKeyConstants.redPackRecordListPrefix
                + redPackGrabCommand.getRedPackId(), 0, -1);
        if (null != strRange && strRange.size() > 0) {
            Optional<RedPackRecord> anyRecord = strRange.stream().map(str -> JSON.parseObject(str, RedPackRecord.class))
                    .filter(record -> record.getOwnerId().equals(redPackGrabCommand.getUserId()))
                    .findAny();
            if (anyRecord.isPresent()) {
                redPackGrabResult.setGrabbed(true);
                redPackGrabResult.setMoney(anyRecord.get().getMoney());
                return redPackGrabResult;
            }
        }

        String redPackTypeCode = stringRedisTemplate.<String, String>opsForHash()
                .get(redPackRedisMapKey, RedisKeyConstants.redPackTypeField);
        if (null == redPackTypeCode) {
            throw new IllegalStateException("红包类型缺失");
        }
        RedPackType redPackType = CodeDescEnum.parseFromCode(RedPackType.class, Integer.parseInt(redPackTypeCode));

        //这个逻辑是用户直接获取自己的红包
        if (null == redPackGrabCommand.getGroupId()) {
            if (!RedPackType.USER.equals(redPackType)) {
                throw new IllegalStateException("红包类型错误~, 正确红包类型为: " + redPackType.getDesc());
            }
            String targetId = stringRedisTemplate.<String, String>opsForHash()
                    .get(redPackRedisMapKey, RedisKeyConstants.redPackTargetField);
            if (null == targetId || !redPackGrabCommand.getUserId().equals(Long.valueOf(targetId))) {
                throw new IllegalStateException("该红包不属于你!");
            }
            String total = stringRedisTemplate.<String, String>opsForHash()
                    .get(redPackRedisMapKey, RedisKeyConstants.totalField);
            if (null == total || !Integer.valueOf(1).equals(Integer.valueOf(total))) {
                throw new IllegalStateException("红包数量只能为一");
            }
            String totalMoney = stringRedisTemplate.<String, String>opsForHash()
                    .get(redPackRedisMapKey, RedisKeyConstants.moneyField);
            if (null == totalMoney || Long.parseLong(totalMoney) <= 0) {
                return redPackGrabResult;
            }
            Long remainMoney = stringRedisTemplate.opsForHash().increment(redPackRedisMapKey,
                    RedisKeyConstants.moneyField, -1 * Long.parseLong(totalMoney));
            if (remainMoney != 0) {
                return redPackGrabResult;
            }
            Long remainTotal = stringRedisTemplate.opsForHash().increment(redPackRedisMapKey,
                    RedisKeyConstants.totalField, -1);
            log.info("remainTotal: {} 应该为0， 不为0也不是致命错误", remainTotal);
            createRecordAndProcessRecordPay(redPackGrabCommand, Long.parseLong(totalMoney));
            redPackGrabResult.setMoney(Long.parseLong(totalMoney));
            redPackGrabResult.setGrabbed(true);
            return redPackGrabResult;
        }
        else {
            String targetId = stringRedisTemplate.<String, String>opsForHash()
                    .get(redPackRedisMapKey, RedisKeyConstants.redPackTargetField);
            if (null == targetId || !redPackGrabCommand.getGroupId().equals(Long.valueOf(targetId))) {
                throw new IllegalStateException("该红包不属于该群!");
            }
            //这个逻辑是抢群里的红包
            if (RedPackType.GROUP_NORMAL.equals(redPackType)) {
                String script = """
                        local key = KEYS[1]
                        local moneyField = ARGV[1]
                        local totalField = ARGV[2]
                        local m = tonumber(redis.call("HGET", key, moneyField))
                        local c = tonumber(redis.call("HGET", key, totalField))
                        if m <= 0 or c <= 0 then
                            return 0
                        end
                        local res = m / c
                        redis.call("HSET", key, moneyField, (m - res))
                        redis.call("HSET", key, totalField, (c - 1))
                        return res
                                """;
                Long res = stringRedisTemplate.execute(RedisScript.of(script, Long.class), List.of(redPackRedisMapKey),
                        RedisKeyConstants.moneyField, RedisKeyConstants.totalField);
                if (null != res && res > 0) {
                    createRecordAndProcessRecordPay(redPackGrabCommand, res);
                    redPackGrabResult.setMoney(res);
                    redPackGrabResult.setGrabbed(true);
                    return redPackGrabResult;
                }
            }
            else if (RedPackType.GROUP_RANDOM_REALTIME_SPLIT.equals(redPackType)) {
                String total = stringRedisTemplate.<String, String>opsForHash()
                        .get(redPackRedisMapKey, RedisKeyConstants.totalField);
                if (null == total || Integer.parseInt(total) <= 0) {
                    return redPackGrabResult;
                }
                String totalMoney = stringRedisTemplate.<String, String>opsForHash()
                        .get(redPackRedisMapKey, RedisKeyConstants.moneyField);
                if (null == totalMoney || Long.parseLong(totalMoney) <= 0) {
                    return redPackGrabResult;
                }

                Long curMoney = Long.parseLong(totalMoney);
                Integer curNum = Integer.parseInt(total);
                while (curMoney > 0 && curNum > 0) {
                    long grabMoney = RedPackSplitUtils.doubledAvgSplit(curMoney, curNum);
                    String script = """
                        local key = KEYS[1]
                        local moneyField = ARGV[1]
                        local totalField = ARGV[2]
                        local grabMoney = tonumber(ARGV[3])
                        local curMoney = tonumber(ARGV[4])
                        local curNum = tonumber(ARGV[5])
                        local m = tonumber(redis.call("HGET", key, moneyField))
                        local c = tonumber(redis.call("HGET", key, totalField))
                        if m ~= curMoney or c ~= curNum then
                            return 0
                        end
                        redis.call("HSET", key, moneyField, (m - grabMoney + 1))
                        redis.call("HSET", key, totalField, (c - 1))
                        return grabMoney
                                """;
                    Long res = stringRedisTemplate.execute(RedisScript.of(script, Long.class), List.of(redPackRedisMapKey),
                            RedisKeyConstants.moneyField, RedisKeyConstants.totalField,
                            String.valueOf(grabMoney), String.valueOf(curMoney), String.valueOf(curNum));
                    if (null != res && res > 0) {
                        createRecordAndProcessRecordPay(redPackGrabCommand, res);
                        redPackGrabResult.setMoney(res);
                        redPackGrabResult.setGrabbed(true);
                        return redPackGrabResult;
                    }
                    total = stringRedisTemplate.<String, String>opsForHash()
                            .get(redPackRedisMapKey, RedisKeyConstants.totalField);
                    if (null == total || Integer.parseInt(total) <= 0) {
                        return redPackGrabResult;
                    }
                    totalMoney = stringRedisTemplate.<String, String>opsForHash()
                            .get(redPackRedisMapKey, RedisKeyConstants.moneyField);
                    if (null == totalMoney || Long.parseLong(totalMoney) <= 0) {
                        return redPackGrabResult;
                    }
                    curMoney = Long.parseLong(totalMoney);
                    curNum = Integer.parseInt(total);
                }
            }
            else if (RedPackType.GROUP_RANDOM_PRE_SPLIT.equals(redPackType)) {
                String total = stringRedisTemplate.<String, String>opsForHash()
                        .get(redPackRedisMapKey, RedisKeyConstants.totalField);
                if (null == total || Integer.parseInt(total) <= 0) {
                    return redPackGrabResult;
                }
                String script = """
                        local key = KEYS[1]
                        local listKey = KEYS[2]
                        local totalField = ARGV[1]
                        local c = tonumber(redis.call("HGET", key, totalField))
                        if c <= 0 then
                            return '0'
                        end
                        local res = redis.call("LPOP", listKey)
                        if res == nil then
                          return '0'
                        end
                        redis.call("HSET", key, totalField, (c - 1))
                        return res
                                """;
                String strRedPackRecordId = stringRedisTemplate.execute(RedisScript.of(script, String.class), List.of(redPackRedisMapKey,
                                RedisKeyConstants.redPackListPrefix + redPackGrabCommand.getRedPackId()),
                        RedisKeyConstants.totalField);
                log.info("redPackRecordId: {}", strRedPackRecordId);
                Long redPackRecordId = Long.parseLong(strRedPackRecordId);
                if (redPackRecordId > 0) {
                    RedPackRecord redPackRecord = redPackRecordMapper.selectById(redPackRecordId);
                    if (null == redPackRecord) {
                        log.info("不应该发生的链路");
                        return redPackGrabResult;
                    }
                    redPackRecord.setOwnerId(redPackGrabCommand.getUserId());
                    redPackRecord.setModified(LocalDateTime.now());
                    redPackRecordMapper.updateById(redPackRecord);
                    processRedPackRecordPay(redPackRecord);
                    redPackGrabResult.setMoney(redPackRecord.getMoney());
                    redPackGrabResult.setGrabbed(true);
                    return redPackGrabResult;
                }
            }
        }
        return redPackGrabResult;
    }

    private void createRecordAndProcessRecordPay(RedPackGrabCommand redPackGrabCommand, long recordMoney) {
        RedPackRecord redPackRecord = new RedPackRecord();
        redPackRecord.setRedPackId(redPackGrabCommand.getRedPackId());
        redPackRecord.setRedPackRecordStatus(RedPackRecordStatus.NOT_PAYED);
        redPackRecord.setOwnerId(redPackGrabCommand.getUserId());
        redPackRecord.setMoney(recordMoney);
        redPackRecord.setCreated(LocalDateTime.now());
        redPackRecord.setModified(redPackRecord.getCreated());
        redPackRecordMapper.insert(redPackRecord);
        processRedPackRecordPay(redPackRecord);
    }

    /**
     * 根据id进行后续转账服务
     */
    private void processRedPackRecordPay(RedPackRecord redPackRecord) {
        //缓存红包抢夺结果
        String key = RedisKeyConstants.redPackRecordListPrefix + redPackRecord.getRedPackId();
        stringRedisTemplate.opsForList().leftPush(key, JSON.toJSONString(redPackRecord));
        stringRedisTemplate.expire(key, Duration.ofSeconds(redPackInfoTimeoutSeconds));
        try {
            mqManager.redPackRecordProcess(redPackRecord.getId(), redPackRecordMapper);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void createRedPack(Long redPackOrderId) {
        transactionTemplate.executeWithoutResult((status) -> {
            try {
                RedPackOrder redPackOrder = redPackOrderMapper.selectById(redPackOrderId);
                if (null == redPackOrder || !redPackOrder.getRedPackOrderStatus().equals(RedPackOrderStatus.PAYED)) {
                    throw new IllegalStateException("非法红包订单状态");
                }
                //1.创建red pack
                RedPack redPack = new RedPack();
                redPack.setCreated(LocalDateTime.now());
                redPack.setModified(redPack.getCreated());

                redPack.setRedPackStatus(RedPackStatus.CREATED);
                redPack.setRedPackType(redPackOrder.getRedPackType());
                redPack.setRedPackOrderId(redPackOrder.getId());
                redPack.setOwnerId(redPackOrder.getOwnerId());
                redPack.setTargetId(redPackOrder.getTargetId());
                redPack.setTotalRedPackNum(redPackOrder.getTotalRedPackNum());
                redPack.setTotalMoney(redPackOrder.getTotalMoney());
                redPackMapper.insert(redPack);

                //2.更新订单状态
                LambdaUpdateWrapper<RedPackOrder> redPackOrderWrapper = new LambdaUpdateWrapper<>();
                redPackOrderWrapper.eq(RedPackOrder::getId, redPackOrder.getId());
                redPackOrderWrapper.eq(RedPackOrder::getRedPackOrderStatus, RedPackOrderStatus.PAYED);
                redPackOrderWrapper.set(RedPackOrder::getRedPackOrderStatus, RedPackOrderStatus.FINISH);
                redPackOrderWrapper.set(RedPackOrder::getModified, LocalDateTime.now());
                redPackOrderMapper.update(redPackOrderWrapper);

                //3.判断红包类型做不同的处理
                processRedPackSplit(redPack);
                // 缓存红包信息
                stringRedisTemplate.opsForValue()
                        .set(RedisKeyConstants.redPackInfoPrefix + redPack.getId(), JSON.toJSONString(redPack),
                                Duration.ofSeconds(redPackInfoTimeoutSeconds));
                //4.发送把红包推给目标 可以是群/人
                log.info("已经把红包发给了对应的目标: {}", redPack.getTargetId());
                //5.启动定时任务，24小时后对该红包对账，如果需要退款完成退款
                mqManager.redPackCheck(redPack.getId(), redPackMapper, redPackRecordMapper,
                        stringRedisTemplate, transactionTemplate);

                log.info("redPack: {}", JSON.toJSONString(redPack));

            } catch (Exception e) {
                log.error("事务异常", e);
                status.setRollbackOnly();
                throw new RuntimeException(e);
            }
        });
    }

    private void processRedPackSplit(RedPack redPack) {
        String script = """
                local key = KEYS[1]
                local moneyField = ARGV[1]
                local moneyValue = ARGV[2]
                local totalField = ARGV[3]
                local totalValue = ARGV[4]
                local typeField = ARGV[5]
                local typeValue = ARGV[6]
                local targetField = ARGV[7]
                local targetValue = ARGV[8]
                local redPackCacheTimeoutSeconds = tonumber(ARGV[9])
                redis.call("HSET", key, moneyField, moneyValue)
                redis.call("HSET", key, totalField, totalValue)
                redis.call("HSET", key, typeField, typeValue)
                redis.call("HSET", key, targetField, targetValue)
                redis.call("EXPIRE", key, redPackCacheTimeoutSeconds)
                """;
        if (redPack.getRedPackType().equals(RedPackType.USER) || redPack.getRedPackType().equals(RedPackType.GROUP_NORMAL)) {
            stringRedisTemplate.execute(RedisScript.of(script), List.of(RedisKeyConstants.redPackRemainPrefix + redPack.getId()),
                    RedisKeyConstants.moneyField, String.valueOf(redPack.getTotalMoney()),
                    RedisKeyConstants.totalField, String.valueOf(redPack.getTotalRedPackNum()),
                    RedisKeyConstants.redPackTypeField, String.valueOf(redPack.getRedPackType().getCode()),
                    RedisKeyConstants.redPackTargetField, String.valueOf(redPack.getTargetId()),
                    String.valueOf(redPackCacheTimeoutSeconds));
        }
        else if (redPack.getRedPackType().equals(RedPackType.GROUP_RANDOM_PRE_SPLIT)) {
            //预先拆分逻辑
            List<Long> moneys = RedPackSplitUtils.preSplit(redPack.getTotalMoney(), redPack.getTotalRedPackNum());
            if (!redPack.getTotalRedPackNum().equals(moneys.size())) {
                throw new IllegalStateException("拆分失败");
            }
            for (Long money : moneys) {
                RedPackRecord redPackRecord = new RedPackRecord();
                redPackRecord.setRedPackId(redPack.getId());
                redPackRecord.setRedPackRecordStatus(RedPackRecordStatus.NOT_PAYED);
                redPackRecord.setMoney(money);
                //预生成的红包，不属于任何用户
                redPackRecord.setOwnerId(-1L);
                redPackRecord.setCreated(LocalDateTime.now());
                redPackRecord.setModified(redPackRecord.getCreated());
                    redPackRecordMapper.insert(redPackRecord);
                stringRedisTemplate.opsForList().leftPush(RedisKeyConstants.redPackListPrefix + redPack.getId(),
                        String.valueOf(redPackRecord.getId()));
                stringRedisTemplate.expire(RedisKeyConstants.redPackListPrefix + redPack.getId(),
                        Duration.ofSeconds(redPackCacheTimeoutSeconds));
            }
            log.info("预拆分完成!!");
            stringRedisTemplate.execute(RedisScript.of(script), List.of(RedisKeyConstants.redPackRemainPrefix + redPack.getId()),
                    RedisKeyConstants.moneyField, String.valueOf(redPack.getTotalMoney()),
                    RedisKeyConstants.totalField, String.valueOf(redPack.getTotalRedPackNum()),
                    RedisKeyConstants.redPackTypeField, String.valueOf(RedPackType.GROUP_RANDOM_PRE_SPLIT.getCode()),
                    RedisKeyConstants.redPackTargetField, String.valueOf(redPack.getTargetId()),  String.valueOf(redPackCacheTimeoutSeconds));

        }
        else if (redPack.getRedPackType().equals(RedPackType.GROUP_RANDOM_REALTIME_SPLIT)) {
            //实时拆分
            stringRedisTemplate.execute(RedisScript.of(script), List.of(RedisKeyConstants.redPackRemainPrefix + redPack.getId()),
                    RedisKeyConstants.moneyField, String.valueOf(redPack.getTotalMoney() - redPack.getTotalRedPackNum()),
                    RedisKeyConstants.totalField, String.valueOf(redPack.getTotalRedPackNum()),
                    RedisKeyConstants.redPackTypeField, String.valueOf(RedPackType.GROUP_RANDOM_REALTIME_SPLIT.getCode()),
                    RedisKeyConstants.redPackTargetField, String.valueOf(redPack.getTargetId()),  String.valueOf(redPackCacheTimeoutSeconds));
        }
    }


    public RedPackViewResult viewRedPack(RedPackViewCommand redPackViewCommand) {
        RedPackViewResult redPackViewResult = new RedPackViewResult();
        log.info("redPackViewCommand: {}", JSON.toJSONString(redPackViewCommand));
        Object cachedRedPack = caffeineCache.getIfPresent(RedisKeyConstants.redPackInfoPrefix + redPackViewCommand.getRedPackId());
        RedPack redPack;
        if (null != cachedRedPack) {
            log.info("红包信息命中缓存~~~");
            redPack = (RedPack) cachedRedPack;

        } else {
            String strRedPackInfo = stringRedisTemplate.opsForValue().get(RedisKeyConstants.redPackInfoPrefix + redPackViewCommand.getRedPackId());
            if (null == strRedPackInfo) {
                //没有红包信息，说明红包信息已经过期，提示用户到红包领取记录查看
                redPackViewResult.setCode(1);
                return redPackViewResult;
            }
            redPack = JSON.parseObject(strRedPackInfo, RedPack.class);
            caffeineCache.put(RedisKeyConstants.redPackInfoPrefix + redPackViewCommand.getRedPackId(), redPack);
        }
        redPackViewResult.setCode(0);
        redPackViewResult.setRedPack(redPack);

        Object cachedRecords = caffeineCache.getIfPresent(RedisKeyConstants.redPackRecordListPrefix + redPackViewCommand.getRedPackId());
        List<RedPackRecord> redPackRecordList = null;
        if (null != cachedRecords) {
            log.info("红包记录命中缓存~~~");
            redPackRecordList = (List<RedPackRecord>) cachedRecords;
        } else {
            List<String> strRange = stringRedisTemplate.opsForList().range(RedisKeyConstants.redPackRecordListPrefix + redPackViewCommand.getRedPackId(), 0, -1);
            if (null != strRange && strRange.size() > 0) {
                redPackRecordList = strRange.stream().map(str -> JSON.parseObject(str, RedPackRecord.class))
                        .toList();
                if (redPackRecordList.size() == redPack.getTotalRedPackNum()) {
                    //只有全部红包抢完以后才会加入缓存
                    caffeineCache.put(RedisKeyConstants.redPackRecordListPrefix + redPackViewCommand.getRedPackId(), redPackRecordList);
                }
            }
        }
        redPackViewResult.setRedPackRecords(redPackRecordList);
        return redPackViewResult;
    }


    public List<RedPackRecord> listRedPackRecordByUserId(Long userId) {
        log.info("userId: {}", userId);
        LambdaQueryWrapper<RedPackRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RedPackRecord::getOwnerId, userId);
        return redPackRecordMapper.selectList(wrapper);
    }
}
