package com.yumi.read_pack.mq;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.yumi.read_pack.service.RedPackService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class MqManager {
    private volatile boolean active = true;
    private final ExecutorService POOL = Executors.newCachedThreadPool();
    private final DelayQueue<OrderTimeoutDelayedTask> ORDER_TIMEOUT_DELAYED_TASK_DELAY_QUEUE = new DelayQueue<>();
    private final DelayQueue<RedPackCheckTask> RED_PACK_CHECK_DELAYED_TASK_DELAY_QUEUE = new DelayQueue<>();
    private final BlockingQueue<RedPackOrder2RedPackTask> RED_PACK_ORDER_2_RED_PACK_QUEUE = new ArrayBlockingQueue<>(100);

    private final BlockingQueue<RedPackRecordTask> RED_PACK_RECORD_TASK_QUEUE = new ArrayBlockingQueue<>(100);
    @Value("${red.pack.cache.timeout.seconds}")
    private Integer redPackCacheTimeoutSeconds;
    @PostConstruct
    public void init() {
        POOL.submit(
                () -> {
                    while (active) {
                        try {
                            RedPackRecordTask task = RED_PACK_RECORD_TASK_QUEUE.poll(2, TimeUnit.SECONDS);
                            if (null != task) {
                                Long redPackRecordId = task.getRedPackRecordId();
                                RedPackRecordMapper redPackRecordMapper = task.getRedPackRecordMapper();

                                RedPackRecord redPackRecord = redPackRecordMapper.selectById(redPackRecordId);
                                if (null == redPackRecord) {
                                    log.info("出大事拉~~ id为 {} 的红包记录没有找到", redPackRecordId);
                                }
                                redPackRecord.setModified(LocalDateTime.now());
                                redPackRecord.setRedPackRecordStatus(RedPackRecordStatus.PAYED);
                                redPackRecordMapper.updateById(redPackRecord);
                                log.info("已经完成红包转账 {}", JSON.toJSONString(redPackRecord));
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
        );
        POOL.submit(
                () -> {
                    while (active) {
                        try {
                            RedPackCheckTask task = RED_PACK_CHECK_DELAYED_TASK_DELAY_QUEUE.poll(2, TimeUnit.SECONDS);
                            if (null != task) {
                                task.check();
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
        );
        POOL.submit(
                () -> {
                    while (active) {
                        try {
                            OrderTimeoutDelayedTask task = ORDER_TIMEOUT_DELAYED_TASK_DELAY_QUEUE.poll(2, TimeUnit.SECONDS);
                            if (null != task) {
                                task.tryToTimeoutRedPackOrder();
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
        );
        POOL.submit(() -> {
           while (active) {
               try {
                   RedPackOrder2RedPackTask task = RED_PACK_ORDER_2_RED_PACK_QUEUE.poll(2, TimeUnit.SECONDS);
                   if (null != task) {
                       task.getRedPackService().createRedPack(task.getRedPackOrderId());
                   }
               } catch (InterruptedException e) {
                   throw new RuntimeException(e);
               }
           }
        });
    }
    @PreDestroy
    public void destroy() {
        active = false;
        POOL.shutdown();
    }

    public void addRedPackOrderTimeoutTask(long redPackOrderId, RedPackOrderMapper redPackOrderMapper) {
        ORDER_TIMEOUT_DELAYED_TASK_DELAY_QUEUE.add(new OrderTimeoutDelayedTask(
                (redPackCacheTimeoutSeconds + 10) * 1000, redPackOrderId, redPackOrderMapper
        ));
    }

    public void redPackOrder2RedPack(RedPackService redPackService, Long redPackOrderId) throws InterruptedException {
        RedPackOrder2RedPackTask task = new RedPackOrder2RedPackTask(redPackService, redPackOrderId);
        RED_PACK_ORDER_2_RED_PACK_QUEUE.put(task);
    }

    public void redPackCheck(Long redPackId, RedPackMapper redPackMapper,
                             RedPackRecordMapper redPackRecordMapper, StringRedisTemplate stringRedisTemplate,
                             TransactionTemplate transactionTemplate) throws InterruptedException {
        RED_PACK_CHECK_DELAYED_TASK_DELAY_QUEUE.put(new RedPackCheckTask(
                5 * 60 * 1000, redPackId, redPackMapper, redPackRecordMapper, stringRedisTemplate,
                transactionTemplate
        ));
    }

    public void redPackRecordProcess(Long redPackRecordId, RedPackRecordMapper redPackRecordMapper) throws InterruptedException {
        RedPackRecordTask redPackRecordTask = new RedPackRecordTask(redPackRecordId, redPackRecordMapper);
        RED_PACK_RECORD_TASK_QUEUE.put(redPackRecordTask);
    }

    @Data
    public class RedPackOrder2RedPackTask {
        private final RedPackService redPackService;
        private final  Long redPackOrderId;

    }

    @Data
    public class RedPackRecordTask {
        private final Long redPackRecordId;
        private final RedPackRecordMapper redPackRecordMapper;
    }

    private class OrderTimeoutDelayedTask implements Delayed {
        private final long delayTime;
        private final long expire;
        private final long redPackOrderId;
        private final RedPackOrderMapper redPackOrderMapper;

        public OrderTimeoutDelayedTask(long delayTime, long redPackOrderId, RedPackOrderMapper redPackOrderMapper) {
            this.redPackOrderId = redPackOrderId;
            this.delayTime = delayTime;
            this.expire = System.currentTimeMillis() + delayTime;
            this.redPackOrderMapper = redPackOrderMapper;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = expire - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            if (this.expire < ((OrderTimeoutDelayedTask) o).expire) {
                return -1;
            }
            if (this.expire > ((OrderTimeoutDelayedTask) o).expire) {
                return 1;
            }
            return 0;
        }

        public void tryToTimeoutRedPackOrder() {
            LambdaUpdateWrapper<RedPackOrder> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(RedPackOrder::getId, this.redPackOrderId);
            wrapper.eq(RedPackOrder::getRedPackOrderStatus, RedPackOrderStatus.CREATED);
            //更新为超时
            wrapper.set(RedPackOrder::getRedPackOrderStatus, RedPackOrderStatus.TIMEOUT);
            wrapper.set(RedPackOrder::getModified, LocalDateTime.now());
            this.redPackOrderMapper.update(wrapper);
        }
    }



    private class RedPackCheckTask implements Delayed {
        private  long delayTime;
        private  long expire;
        private final long redPackId;
        private final RedPackMapper redPackMapper;
        private final RedPackRecordMapper redPackRecordMapper;
        private final StringRedisTemplate stringRedisTemplate;
        private final TransactionTemplate transactionTemplate;


        public  RedPackCheckTask(long delayTime, long redPackId, RedPackMapper redPackMapper,
                                RedPackRecordMapper redPackRecordMapper, StringRedisTemplate stringRedisTemplate,
                                TransactionTemplate transactionTemplate) {
            this.delayTime = delayTime;
            this.expire = System.currentTimeMillis() + delayTime;
            this.redPackId = redPackId;
            this.redPackMapper = redPackMapper;
            this.redPackRecordMapper = redPackRecordMapper;
            this.stringRedisTemplate = stringRedisTemplate;
            this.transactionTemplate = transactionTemplate;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = expire - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            if (this.expire < ((RedPackCheckTask) o).expire) {
                return -1;
            }
            if (this.expire > ((RedPackCheckTask) o).expire) {
                return 1;
            }
            return 0;
        }

        public void check() {
            log.info("check start !");
            Boolean hasKey = this.stringRedisTemplate.hasKey(RedisKeyConstants.redPackRemainPrefix + this.redPackId);
            if (hasKey) {
                log.info("check restart !");
                //红包还没过期，延迟5分钟执行 (不应该发生，真实mq抛出异常，基于mq重试即可)
                this.delayTime = 5 * 60 * 1000;
                this.expire = System.currentTimeMillis() + delayTime;
                MqManager.this.RED_PACK_CHECK_DELAYED_TASK_DELAY_QUEUE.add(this);
                return;
            }
            //到这里，用户已经不能再抢红包了，可以安心结算了
            RedPack redPack = this.redPackMapper.selectById(this.redPackId);
            LambdaQueryWrapper<RedPackRecord> redPackRecordQueryWrapper = new LambdaQueryWrapper<>();
            redPackRecordQueryWrapper.eq(RedPackRecord::getRedPackId, this.redPackId);
            //在预分配场景下，排除掉无效数据
            redPackRecordQueryWrapper.ne(RedPackRecord::getOwnerId, -1);
            List<RedPackRecord> redPackRecords = redPackRecordMapper.selectList(redPackRecordQueryWrapper);
            long takenMoney = 0;
            for (RedPackRecord redPackRecord : redPackRecords) {
                takenMoney += redPackRecord.getMoney();
            }
            LambdaUpdateWrapper<RedPack> redPackLambdaUpdateWrapper = new LambdaUpdateWrapper<>();
            redPackLambdaUpdateWrapper.eq(RedPack::getId, redPack.getId());
            redPackLambdaUpdateWrapper.set(RedPack::getModified, LocalDateTime.now());
            long remainMoney = redPack.getTotalMoney() - takenMoney;
            if (remainMoney == 0) {
                if (redPackRecords.size() != redPack.getTotalRedPackNum()) {
                    //这种是大事故，提交到人工流程赶紧处理
                    log.info("出大事故了，提交到人工流程介入处理");
                } else {
                    //不需要退款
                    redPackLambdaUpdateWrapper.set(RedPack::getRedPackStatus, RedPackStatus.FINISH_WITH_ZERO);
                }
            } else {
                //没有抢光
                log.info("剩余红包金额 {} , 剩余红包数量 {}", remainMoney, redPack.getTotalRedPackNum() - redPackRecords.size());
                redPackLambdaUpdateWrapper.set(RedPack::getRedPackStatus, RedPackStatus.FINISH_WITH_REMAIN);
            }

            transactionTemplate.executeWithoutResult((status) -> {
                try {
                    //更新红包信息
                    redPackMapper.update(redPackLambdaUpdateWrapper);
                    //模拟发起退款
                    doRefund(redPack.getId(), redPack.getOwnerId(), remainMoney);
                } catch (Exception e) {
                    status.setRollbackOnly();
                    throw new RuntimeException(e);
                }
            });
        }

        private void doRefund(Long redPackId, Long ownerId, long remainMoney) {
            //退款流程~简单打印个日志吧
            if (remainMoney == 0) {
                log.info("用户 {} 的 红包 {} 未发生退款！~", ownerId, redPackId);
            } else {
                log.info("用户 {} 的 红包 {} 发生退款，退款金额为 {}", ownerId, redPackId, remainMoney);
            }
        }
    }
}
