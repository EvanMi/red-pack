package com.yumi.read_pack.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/yumiPay")
@Slf4j
public class YumiPayController {

    @GetMapping("/{outOrderId}/{payAmount}")
    public RedirectView pay(@PathVariable("outOrderId") Long outOrderId,
                            @PathVariable("payAmount") Long payAmount,
                            @RequestParam("callback") String callBackUrl) {
        //假装完成了支付
        log.info("user payed，pay amount is: {}，out order id is: {}", payAmount, outOrderId);
        return new RedirectView(callBackUrl + "/" + outOrderId);
    }
}
