package com.yumi.read_pack.domain.po;

import com.yumi.read_pack.common.CodeDescEnum;


public enum RedPackStatus implements CodeDescEnum<RedPackStatus> {
    CREATED(0, "红包创建"),
    FINISH_WITH_ZERO(1, "全部抢光"),
    FINISH_WITH_REMAIN(2, "没有全部抢光");

    RedPackStatus(Integer code, String desc) {
        addCodeDesc(code, desc);
    }

    @Override
    public RedPackStatus self() {
        return this;
    }
}
