package com.yumi.read_pack.domain.po;

import com.yumi.read_pack.common.CodeDescEnum;

public enum RedPackType implements CodeDescEnum<RedPackType> {
    GROUP_RANDOM_REALTIME_SPLIT(0, "群拼手气实时拆分"),
    GROUP_RANDOM_PRE_SPLIT(1, "群拼手气预拆分"),
    GROUP_NORMAL(2, "群平均分配"),
    USER(3, "个人单发");
    RedPackType(Integer code, String desc) {
        addCodeDesc(code, desc);
    }

    @Override
    public RedPackType self() {
        return this;
    }
}
