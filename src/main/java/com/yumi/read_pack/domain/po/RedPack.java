package com.yumi.read_pack.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("red_pack")
public class RedPack {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long redPackOrderId;
    private Long ownerId;
    //红包要发给谁 群id或者用户id
    private Long targetId;
    private RedPackType redPackType;
    private Long totalMoney;
    private Integer totalRedPackNum;
    private RedPackStatus redPackStatus;
    private LocalDateTime created;
    private LocalDateTime modified;
}
