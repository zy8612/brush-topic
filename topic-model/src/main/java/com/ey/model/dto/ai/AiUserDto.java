package com.ey.model.dto.ai;

import lombok.Data;

@Data
public class AiUserDto {
    private String account;
    private Integer pageNum;
    private Integer pageSize;
}
