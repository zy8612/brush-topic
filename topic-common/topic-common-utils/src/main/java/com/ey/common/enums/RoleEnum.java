package com.ey.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public enum RoleEnum {
    MEMBER(1, "member"),
    ADMIN(2, "admin"),
    USER(0, "user");

    private Integer identify;
    private String roleKey;

}
