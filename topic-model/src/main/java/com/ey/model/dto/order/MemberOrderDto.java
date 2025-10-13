package com.ey.model.dto.order;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MemberOrderDto {
    private Long userId;
    private Long id;
    private BigDecimal price;
}
