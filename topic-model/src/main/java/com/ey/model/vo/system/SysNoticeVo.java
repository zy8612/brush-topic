package com.ey.model.vo.system;

import lombok.Data;

@Data
public class SysNoticeVo {
    private Long id;
    private String account;
    private Integer status;
    private String content;
    // 时间描述
    private String timeDesc;
}
