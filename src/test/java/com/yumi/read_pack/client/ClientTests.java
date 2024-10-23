package com.yumi.read_pack.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONWriter;
import com.yumi.read_pack.domain.po.RedPack;
import com.yumi.read_pack.domain.po.RedPackRecord;
import com.yumi.read_pack.dto.RedPackGrabResult;
import com.yumi.read_pack.dto.RedPackViewResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class ClientTests {
    /***
     * 单人红包发送
     */
    @Test
    public void testCreateU2URedPack() {
        createRedPack(0L, 1L, 3, 55L, null, null);
    }
    /***
     * 单人红包收取
     */
    @Test
    public void testGrabU2URedPack() {
        grabRedPack(1846108114496540674L, 1L, null);
    }

    //***************************************************************************************************************//

    /***
     * 平分群红包发送
     */
    @Test
    public void testCreateU2GNormalRedPack() {
        createRedPack(0L, 1L, 2, null, 10, 20L);
    }
    /***
     * 预创建随机群红包发送
     */
    @Test
    public void testCreateU2GPreRandomRedPack() {
        createRedPack(0L, 1L, 1, 53L, 10, null);
    }
    /***
     * 实时随机群红包发送
     */
    @Test
    public void testCreateU2GRealtimeRandomRedPack() {
        createRedPack(0L, 1L, 0, 53L, 20, null);
    }

    /***
     * 群红包抢夺
     */
    @Test
    public void testGrabU2GRedPack() throws InterruptedException {
        CyclicBarrier cyclicBarrier = new CyclicBarrier(16);
        ExecutorService executorService = Executors.newFixedThreadPool(16);
        AtomicLong totalMoney = new AtomicLong(0);
        for (int i = 0; i < 16; i++) {
            int finalI = (i % 11) + 1 ;
            executorService.submit(() -> {
                try {
                    cyclicBarrier.await();
                    RedPackGrabResult redPackGrabResult = grabRedPack(1848990814479863810L, (long) finalI, 1L);
                    totalMoney.addAndGet(redPackGrabResult.getMoney());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (BrokenBarrierException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        executorService.shutdown();
        TimeUnit.SECONDS.sleep(10);
        log.info("totalMoney {}", totalMoney.get());
    }

    /***
     * 查看红包详情
     */
    @Test
    public void testViewRedPack() {
        RestTemplate restTemplate = new RestTemplate();
        String viewUrl = "http://127.0.0.1/redPack/info";
        long redPackId = 1843990144966209537L;
        Map<String, Object> body = new HashMap<>();
        body.put("redPackId", redPackId);
        RedPackViewResult redPackViewResult = restTemplate.postForObject(viewUrl, body, RedPackViewResult.class);
        log.info("红包查询结果: {}", JSON.toJSONString(redPackViewResult));

        if (redPackViewResult.getCode() != 0) {
            log.info("红包有效期已过，如果您确实抢过该红包，请前往个人红包领取记录中查看");
            return;
        }
        log.info("--------------------------");
        RedPack redPack = redPackViewResult.getRedPack();
        List<RedPackRecord> redPackRecords = redPackViewResult.getRedPackRecords();
        int grabbedRedPackNum = redPackRecords == null ? 0 : redPackRecords.size();
        log.info("用户 {} 的红包，红包金额为 {}，红包剩余 {} / {}", redPack.getOwnerId(), redPack.getTotalMoney(),
                redPack.getTotalRedPackNum() - grabbedRedPackNum, redPack.getTotalRedPackNum());
        log.info("--------------------------");
        if (null != redPackRecords) {
            log.info("明细信息:");
            for (RedPackRecord redPackRecord : redPackRecords) {
                log.info("用户 {} 抢到 {}", redPackRecord.getOwnerId(), redPackRecord.getMoney());
            }
            log.info("--------------------------");
        }
    }

    /***
     * 查询红包领取记录
     */
    @Test
    public void listRedPackRecordByUserId() {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://127.0.0.1/redPack/records/" + 1L;
        List<?> records = restTemplate.getForObject(url, List.class);
        log.info("records: {}", JSONArray.toJSONString(records, JSONWriter.Feature.PrettyFormat));
    }

    private RedPackGrabResult grabRedPack(Long redPackId, Long userId, Long groupId) {
        RestTemplate restTemplate = new RestTemplate();
        String grabUrl = "http://127.0.0.1/redPack/grab";

        Map<String, Object> body = new HashMap<>();
        body.put("redPackId", redPackId);
        body.put("userId", userId);
        if (null != groupId) {
            body.put("groupId", groupId);
        }
        RedPackGrabResult redPackGrabResult = restTemplate.postForObject(grabUrl, body, RedPackGrabResult.class);
        log.info("用户: {} 抢红包结果: {}", userId, JSON.toJSONString(redPackGrabResult));
        return redPackGrabResult;
    }

    private void createRedPack(Long ownerId, Long targetId, Integer redPackTypeCode, Long money, Integer num, Long perMoney) {
        RestTemplate restTemplate = new RestTemplate();
        String createUrl = "http://127.0.0.1/redPackOrder";
        Map<String, Object> body = new HashMap<>();
        body.put("ownerId", ownerId);
        body.put("targetId", targetId);
        body.put("redPackTypeCode", redPackTypeCode);
        if (null != perMoney) {
            body.put("perMoney", perMoney);
        }
        if (null != money) {
            body.put("totalMoney", money);
        }
        if (null != num) {
            body.put("totalRedPackNum", num);
        }

        String createResp = restTemplate.postForObject(createUrl, body, String.class);
        log.info("createResp: {}", createResp);
        String payUrl = "http://127.0.0.1/redPackOrder/payUrl/3/" + createResp;
        ResponseEntity<String> payUrlEntity = restTemplate.getForEntity(payUrl, String.class);
        String decodeUrl = URLDecoder.decode(payUrlEntity.getBody());
        log.info("payUrl: {}", decodeUrl);
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        ResponseEntity<String> redirectUrlEntity = restTemplate.getForEntity(decodeUrl, String.class);
        log.info("payedUrl: {}", redirectUrlEntity);
    }
}
