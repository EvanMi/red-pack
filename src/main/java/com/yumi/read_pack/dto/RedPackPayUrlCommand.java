package com.yumi.read_pack.dto;

import lombok.Data;

@Data
public class RedPackPayUrlCommand {
    private Integer payTypeCode;
    private Long redPackOrderId;
}
