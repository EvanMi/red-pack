package com.yumi.read_pack.domain.po;

import com.yumi.read_pack.common.CodeDescEnum;


public enum RedPackOrderStatus implements CodeDescEnum<RedPackOrderStatus> {
    CREATED(0, "订单创建"),
    CANCELED(1, "订单取消"),
    PAYED(2, "订单已支付"),
    TIMEOUT(3, "订单支付超时"),
    FINISH(4, "订单完成");

    RedPackOrderStatus(Integer code, String desc) {
        addCodeDesc(code, desc);
    }

    @Override
    public RedPackOrderStatus self() {
        return this;
    }
}
