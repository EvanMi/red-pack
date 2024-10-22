package com.yumi.read_pack.domain.po;

import com.yumi.read_pack.common.CodeDescEnum;

public enum PayType implements CodeDescEnum<PayType> {
    ALI_PAY(0, "支付宝"),
    WEI_PAY(1, "微信支付"),
    JD_PAY(2, "京东支付"),
    YUMI_PAY(3, "模拟支付")
    ;

    PayType(Integer code, String desc) {
        addCodeDesc(code, desc);
    }

    @Override
    public PayType self() {
        return this;
    }
}
