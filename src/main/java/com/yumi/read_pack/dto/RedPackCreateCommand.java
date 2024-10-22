package com.yumi.read_pack.dto;

import lombok.Data;

@Data
public class RedPackCreateCommand {
    private Long ownerId;
    private Long targetId;
    private Integer redPackTypeCode;
    private Long totalMoney;
    private Long perMoney;
    private Integer totalRedPackNum;
}
