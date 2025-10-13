package com.ey.model.vo.order;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MemberOrderVo {
    private Long id;
    private Long userId;
    private BigDecimal price;
    private Integer status;
    private String createTime;

}
