package com.yumi.read_pack.dto;

import com.yumi.read_pack.domain.po.RedPack;
import com.yumi.read_pack.domain.po.RedPackRecord;
import lombok.Data;

import java.util.List;

@Data
public class RedPackViewResult {
    private Integer code;
    private RedPack redPack;
    private List<RedPackRecord> redPackRecords;
}
