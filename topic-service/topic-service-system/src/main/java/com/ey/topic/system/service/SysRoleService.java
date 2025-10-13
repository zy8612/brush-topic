package com.ey.topic.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ey.model.dto.system.SysRoleDto;
import com.ey.model.entity.system.SysRole;
import com.ey.model.vo.system.SysMenuVo;

import java.util.List;
import java.util.Map;

public interface SysRoleService extends IService<SysRole> {
    Map<String, Object> roleList(SysRole sysRole);

    void addRole(SysRoleDto sysRoleDto);

    void deleteRole(Long roleId);

    void updateRole(SysRoleDto sysRoleDto);

    List<SysMenuVo> getRoleMenu(Long roleId);

    SysRole getRoleKey(String roleKey);
}
