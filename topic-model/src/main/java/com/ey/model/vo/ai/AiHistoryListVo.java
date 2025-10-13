package com.ey.model.vo.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiHistoryListVo {
    // 日期年月日形式
    private String date;
    // 标题和对话id
    List<AiHistoryVo> aiHistoryVos;
}
