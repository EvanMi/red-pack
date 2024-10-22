package com.yumi.read_pack.rest;

import com.alibaba.fastjson2.JSON;
import com.yumi.read_pack.dto.RedPackCreateCommand;
import com.yumi.read_pack.dto.RedPackPayUrlCommand;
import com.yumi.read_pack.service.RedPackOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/redPackOrder")
@RequiredArgsConstructor
@Slf4j
public class RedPackOrderController {
    private final RedPackOrderService redPackOrderService;

    @PostMapping
    public ResponseEntity<Long> create(@RequestBody RedPackCreateCommand redPackCreateCommand) {
        log.info("redPackCreateCommand: {}", JSON.toJSONString(redPackCreateCommand));
        long redPackOrder = redPackOrderService.createRedPackOrder(redPackCreateCommand);
        return ResponseEntity.ok(redPackOrder);
    }

    @GetMapping("/cancel/{redPackOrderId}")
    public ResponseEntity<Boolean> cancel(@PathVariable("redPackOrderId") Long redPackOrderId) {
        log.info("redPackOrderId: {}", redPackOrderId);
        boolean cancelResult = redPackOrderService.cancelRedPackOrder(redPackOrderId);
        return ResponseEntity.ok(cancelResult);
    }

    @GetMapping("/payUrl/{payTypeCode}/{redPackOrderId}")
    public ResponseEntity<String> payUrl(@PathVariable("payTypeCode") Integer payTypeCode, @PathVariable("redPackOrderId") Long redPackOrderId) {
        RedPackPayUrlCommand redPackPayUrlCommand = new RedPackPayUrlCommand();
        redPackPayUrlCommand.setPayTypeCode(payTypeCode);
        redPackPayUrlCommand.setRedPackOrderId(redPackOrderId);
        log.info("payUrl.redPackPayUrlCommand: {}", JSON.toJSONString(redPackPayUrlCommand));
        String url = redPackOrderService.payUrl(redPackPayUrlCommand);
        log.info("url: {}", url);
        return ResponseEntity.ok(url);
    }

    @GetMapping("/payCallback/{redPackOrderId}")
    public ResponseEntity<Boolean> payCallback(@PathVariable("redPackOrderId") Long redPackOrderId) {
        log.info("payCallback.redPackOrderId: {}", redPackOrderId);
        boolean res = redPackOrderService.payCallback(redPackOrderId);
        return ResponseEntity.ok(res);
    }

}
