package com.yumi.read_pack.dto;

import lombok.Data;

@Data
public class RedPackGrabCommand {
    //要抢的红包id
    private Long redPackId;
    //当前的用户id
    private Long userId;
    //当前的群id
    private Long groupId;
}
