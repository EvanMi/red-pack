package com.yumi.read_pack.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("red_pack_record")
public class RedPackRecord {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    /**所属红包id*/
    private Long redPackId;
    /**抢到的金额*/
    private Long money;
    /**被谁抢到了*/
    private Long ownerId;
    /**是否完成转账*/
    private RedPackRecordStatus redPackRecordStatus;
    private LocalDateTime created;
    private LocalDateTime modified;

}
