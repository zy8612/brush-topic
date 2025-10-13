package com.ey.model.dto.system;

import lombok.Data;

@Data
public class SysNoticeDto {
    private Long userId;
    private String content;
    private Integer status;
    private Long recipientsId;
}
