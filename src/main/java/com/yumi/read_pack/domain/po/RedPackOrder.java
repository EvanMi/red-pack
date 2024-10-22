package com.yumi.read_pack.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("red_pack_order")
public class RedPackOrder implements Serializable {
    /**
     * 红包唯一ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    /**
     * 红包所有者
     */
    private Long ownerId;
    /**
     * 红包目标ID 可以是某个用户、可以是某个群
     */
    private Long targetId;
    /**
     * 红包类型 {@link RedPackType}
     */
    private RedPackType redPackType;
    /**
     * 总钱数，单位为分
     */
    private Long totalMoney;
    /**
     * 红包总数
     */
    private Integer totalRedPackNum;

    /**
     * 红包订单状态
     */
    private RedPackOrderStatus redPackOrderStatus;
    private LocalDateTime created;
    private LocalDateTime modified;

    public RedPackOrder() {

    }

    private RedPackOrder(long ownerId, long targetId, RedPackType redPackType, long totalMoney, int totalRedPackNum) {
        this.ownerId = ownerId;
        this.targetId = targetId;
        this.redPackType = redPackType;
        this.totalMoney = totalMoney;
        this.totalRedPackNum = totalRedPackNum;
        this.redPackOrderStatus = RedPackOrderStatus.CREATED;
        this.created = this.modified = LocalDateTime.now();
    }

    /**
     * 创建用户发给用户的红包订单
     */
    public static RedPackOrder createUer2UerRedPackOrder(long ownerId, long targetId, long totalMoney) {
        return new RedPackOrder(ownerId, targetId, RedPackType.USER, totalMoney, 1);
    }

    /**
     * 创建平分群红包订单
     * */
    public static RedPackOrder createUser2GroupNormalRedPackOrder(long ownerId, long targetId, long perMoney, int totalRedPackNum) {
        return new RedPackOrder(ownerId, targetId, RedPackType.GROUP_NORMAL, perMoney * totalRedPackNum, totalRedPackNum);
    }

    /**
     * 创建群拼手气红包订单
     * */
    public static RedPackOrder createUser2GroupRandomRedPackOrder(long ownerId, long targetId, long totalMoney, int totalRedPackNum,
                                                                  RedPackType redPackType) {
        return new RedPackOrder(ownerId, targetId, redPackType, totalMoney, totalRedPackNum);
    }



}
