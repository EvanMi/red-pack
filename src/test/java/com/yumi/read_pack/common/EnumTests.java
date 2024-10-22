package com.yumi.read_pack.common;

import com.yumi.read_pack.domain.po.RedPackStatus;

public class EnumTests {


    public static void main(String[] args) {
        System.out.println(RedPackStatus.FINISH_WITH_REMAIN.getCode());
        System.out.println(CodeDescEnum.parseFromCode(RedPackStatus.class, 2).getDesc());
    }
}
