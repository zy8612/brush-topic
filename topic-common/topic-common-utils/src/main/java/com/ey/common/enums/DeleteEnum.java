package com.ey.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum DeleteEnum {
    IS_DELETED(1),
    NOT_DELETED(0);

    private Integer status;
}
