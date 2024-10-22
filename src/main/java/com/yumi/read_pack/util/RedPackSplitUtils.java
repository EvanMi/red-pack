package com.yumi.read_pack.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class RedPackSplitUtils {

    /**
     * 预拆分
     * @param money 钱 单位分
     * @param n 份数
     * @return 拆分结果
     */
    public static List<Long> preSplit(long money, int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("wrong red pack num");
        }
        if (money < n) {
            throw new IllegalArgumentException("money " + money + "don't match num " + n);
        }
        List<Long> result = new ArrayList<>();
        Random random = new Random((System.currentTimeMillis() + money) / (n + 1));
        // 创建一个包含 n-1 个随机位置的列表
        Set<Long> positions = new HashSet<>();
        while (positions.size() < n - 1) {
            positions.add(random.nextLong(money - 1) + 1);
        }
        // 将总金额分成 n 份
        positions.add(0L);
        positions.add(money);
        ArrayList<Long> arrPositions = new ArrayList<>(positions);
        Collections.sort(arrPositions);

        for (int i = 1; i < arrPositions.size(); i++) {
            result.add(arrPositions.get(i) - arrPositions.get(i - 1));
        }
        Collections.shuffle(result);
        return result;
    }

    /**
     * 二倍均值法实时获取金额
     * 使用该算法 money刚开始的时候就要减去人数，每人留一分钱，在减总money的时候要少减1
     * 可以理解为其中的一分钱从小金库出
     * @param leftMoney 剩余钱
     * @param leftN 剩余红包数
     * @return 本次获得的红包数
     */
    public static long doubledAvgSplit(long leftMoney, int leftN) {
        if (leftN <= 0) {
            //在实时算法中这种情况发生代表红包抢完了
            return 0;
        }
        if (leftN == 1) {
            return leftMoney + 1;
        }
        long avgAmount = leftMoney / leftN;
        Random random = new Random(System.currentTimeMillis() + leftMoney / (leftN + 1));
        long max = avgAmount > 0 ? 2 * avgAmount - 1 : 0;
        return random.nextLong(max + 1) + 1;
    }


    public static void main(String[] args) {
            testDoubledAvg(800, 10);
    }

    public static void testDoubledAvg(long totalMoney, int n) {
        if (totalMoney < n) {
            throw new IllegalArgumentException("wrong money");
        }
        totalMoney = totalMoney - n;
        List<Long> list = new ArrayList<>();
        for (int i = n; i > 0; i--) {
            long res = doubledAvgSplit(totalMoney, i);
            list.add(res);
            totalMoney = totalMoney - res + 1; //少减1分钱
        }
        assert list.stream().mapToLong(Long::longValue).sum() == totalMoney;
        System.out.println(list);
    }

}
