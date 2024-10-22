package com.yumi.read_pack.common;

public interface RedisKeyConstants {
    /**
     * 红包余额redis key
     */
    String redPackRemainPrefix = "com#yumi#red#pack#remain#";
    String moneyField = "money";
    String totalField = "total";
    String redPackTypeField = "type";
    String redPackTargetField = "targetId";

    /**预分配红包id列表 key*/
    String redPackListPrefix = "com#yumi#red#pack#list#";
    /**已抢红包列表*/
    String redPackRecordListPrefix = "com#yumi#red#pack#record#list#";
    /**
     * 红包信息缓存
     * */
    String redPackInfoPrefix = "com#yumi#red#pack#info#";
    /**
     * 抢红包锁
     */
    String redPackGrabUserLockPrefix = "com#yumi#red#pack#user#lock#";
}
