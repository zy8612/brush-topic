package com.ey.model.entity.order;

import com.ey.model.entity.BaseEntity;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MemberOrder extends BaseEntity {
    private Long userId;
    private BigDecimal price;
    private Integer status;
}
