package com.yumi.read_pack.domain.po;

import com.yumi.read_pack.common.CodeDescEnum;

public enum RedPackRecordStatus implements CodeDescEnum<RedPackRecordStatus> {
    NOT_PAYED(0, "未完成转账"),
    PAYED(1,"完成转账")
    ;

    RedPackRecordStatus(Integer code, String desc) {
        addCodeDesc(code, desc);
    }
    @Override
    public RedPackRecordStatus self() {
        return this;
    }
}
