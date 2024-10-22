package com.yumi.read_pack.rest;

import com.yumi.read_pack.domain.po.RedPackRecord;
import com.yumi.read_pack.dto.RedPackGrabCommand;
import com.yumi.read_pack.dto.RedPackGrabResult;
import com.yumi.read_pack.dto.RedPackViewCommand;
import com.yumi.read_pack.dto.RedPackViewResult;
import com.yumi.read_pack.service.RedPackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/redPack")
@RequiredArgsConstructor
@Slf4j
public class RedPackController {

    private final RedPackService redPackService;

    @PostMapping("/grab")
    public RedPackGrabResult grabRedPack(@RequestBody RedPackGrabCommand redPackGrabCommand) {
        return redPackService.grabRedPack(redPackGrabCommand);
    }

    @PostMapping("/info")
    public RedPackViewResult viewRedPack(@RequestBody RedPackViewCommand redPackViewCommand) {
        return redPackService.viewRedPack(redPackViewCommand);
    }

    @GetMapping("/records/{userId}")
    public List<RedPackRecord> listRedPackRecordsByUserId(@PathVariable("userId") Long userId) {
        return redPackService.listRedPackRecordByUserId(userId);
    }
}
