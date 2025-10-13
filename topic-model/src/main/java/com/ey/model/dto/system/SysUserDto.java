package com.ey.model.dto.system;

import com.ey.model.entity.system.SysUser;
import lombok.Data;

@Data
public class SysUserDto extends SysUser {
    private String roleName;
    private Long roleId;
}
