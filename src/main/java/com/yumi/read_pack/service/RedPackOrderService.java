package com.yumi.read_pack.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yumi.read_pack.common.CodeDescEnum;
import com.yumi.read_pack.db.mapper.RedPackOrderMapper;
import com.yumi.read_pack.domain.po.PayType;
import com.yumi.read_pack.domain.po.RedPackOrder;
import com.yumi.read_pack.domain.po.RedPackOrderStatus;
import com.yumi.read_pack.domain.po.RedPackType;
import com.yumi.read_pack.dto.RedPackCreateCommand;
import com.yumi.read_pack.dto.RedPackPayUrlCommand;
import com.yumi.read_pack.mq.MqManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URLEncoder;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedPackOrderService {
    private final RedPackOrderMapper redPackOrderMapper;
    private final MqManager mqManager;
    private final TransactionTemplate transactionTemplate;
    private final RedPackService redPackService;

    public long createRedPackOrder(RedPackCreateCommand redPackCreateCommand) {
        int redPackTypeCode = redPackCreateCommand.getRedPackTypeCode();
        log.info("redPackTypeCode: {}", redPackTypeCode);
        RedPackType redPackType = CodeDescEnum.parseFromCode(RedPackType.class, redPackTypeCode);
        RedPackOrder redPackOrder = null;
        switch (redPackType) {
            case USER -> redPackOrder = RedPackOrder.createUer2UerRedPackOrder(
                    redPackCreateCommand.getOwnerId(), redPackCreateCommand.getTargetId(), redPackCreateCommand.getTotalMoney()
            );
            case GROUP_NORMAL -> redPackOrder = RedPackOrder.createUser2GroupNormalRedPackOrder(
                    redPackCreateCommand.getOwnerId(), redPackCreateCommand.getTargetId(), redPackCreateCommand.getPerMoney(),
                    redPackCreateCommand.getTotalRedPackNum()
            );
            case GROUP_RANDOM_PRE_SPLIT, GROUP_RANDOM_REALTIME_SPLIT -> redPackOrder = RedPackOrder.createUser2GroupRandomRedPackOrder(
                    redPackCreateCommand.getOwnerId(), redPackCreateCommand.getTargetId(), redPackCreateCommand.getTotalMoney(),
                    redPackCreateCommand.getTotalRedPackNum(), redPackType
            );
        }
        redPackOrderMapper.insert(redPackOrder);
        Long redPackOrderId = redPackOrder.getId();
        if (null == redPackOrderId) {
            throw new RuntimeException("failed to submit red-pack order");
        }
        //这里的mq可能会失败，假设有一个全局兜底任务在处理，通过兜底任务虽然不及时，但任然能够让订单超时
        mqManager.addRedPackOrderTimeoutTask(redPackOrderId, redPackOrderMapper);
        return redPackOrderId;
    }

    public boolean cancelRedPackOrder(Long redPackOrderId) {
        RedPackOrder redPackOrder = redPackOrderMapper.selectById(redPackOrderId);
        if (null == redPackOrderId || !redPackOrder.getRedPackOrderStatus().equals(RedPackOrderStatus.CREATED)) {
            return false;
        }
        LambdaUpdateWrapper<RedPackOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(RedPackOrder::getId, redPackOrderId);
        wrapper.eq(RedPackOrder::getRedPackOrderStatus, RedPackOrderStatus.CREATED);

        wrapper.set(RedPackOrder::getRedPackOrderStatus, RedPackOrderStatus.CANCELED);
        wrapper.set(RedPackOrder::getModified, LocalDateTime.now());

        int update = redPackOrderMapper.update(wrapper);
        return update == 1;
    }

    public String payUrl(RedPackPayUrlCommand redPackPayUrlCommand) {
        PayType payType = CodeDescEnum.parseFromCode(PayType.class, redPackPayUrlCommand.getPayTypeCode());
        if (!payType.equals(PayType.YUMI_PAY)) {
            throw new IllegalArgumentException("not supported pay type");
        }
        RedPackOrder redPackOrder = redPackOrderMapper.selectById(redPackPayUrlCommand.getRedPackOrderId());
        if (null == redPackOrder || !RedPackOrderStatus.CREATED.equals(redPackOrder.getRedPackOrderStatus())) {
            throw new IllegalStateException("can't pay");
        }
        return "http://127.0.0.1/yumiPay/" + redPackOrder.getId() + "/" + redPackOrder.getTotalMoney()
                + "?callback=" + URLEncoder.encode("http://127.0.0.1/redPackOrder/payCallback");
    }

    public boolean payCallback(Long redPackOrderId) {
        RedPackOrder redPackOrder = redPackOrderMapper.selectById(redPackOrderId);
        if (null == redPackOrder || !RedPackOrderStatus.CREATED.equals(redPackOrder.getRedPackOrderStatus())) {
            throw new IllegalStateException("can't pay");
        }
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            try {
                LambdaUpdateWrapper<RedPackOrder> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(RedPackOrder::getId, redPackOrderId);
                wrapper.eq(RedPackOrder::getRedPackOrderStatus, RedPackOrderStatus.CREATED);

                wrapper.set(RedPackOrder::getRedPackOrderStatus, RedPackOrderStatus.PAYED);
                wrapper.set(RedPackOrder::getModified, LocalDateTime.now());

                int update = redPackOrderMapper.update(wrapper);
                if (update > 0) {
                    mqManager.redPackOrder2RedPack(redPackService, redPackOrderId);
                    return true;
                }
                return false;
            } catch (Exception e) {
                e.printStackTrace();
                status.setRollbackOnly();
                return false;
            }
        }));
    }
}
