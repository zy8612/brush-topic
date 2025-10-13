package com.ey.model.entity.system;

import com.ey.model.entity.BaseEntity;
import lombok.Data;

@Data
public class SysUserRole extends BaseEntity {
    private Long userId;
    private Long roleId;
}
