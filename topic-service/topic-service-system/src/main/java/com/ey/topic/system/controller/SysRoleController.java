package com.ey.topic.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ey.common.result.Result;
import com.ey.model.dto.system.SysRoleDto;
import com.ey.model.entity.system.SysRole;
import com.ey.model.vo.system.SysMenuVo;
import com.ey.topic.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Description: 权限管理
 */
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;

    /**
     * 根据id获取权限信息
     */
    @GetMapping("/{id}")
    public SysRole getSysRoleById(@PathVariable Long id) {
        return sysRoleService.getById(id);
    }

    /**
     * 获取权限信息列表
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('admin')")
    public Result<Map<String, Object>> getSysRoleList(SysRole sysRole) {
        Map<String, Object> roleList = sysRoleService.roleList(sysRole);
        return Result.success(roleList);
    }

    /**
     * 新增权限
     */
    @PostMapping("/add")
    @PreAuthorize("hasAuthority('admin')")
    public Result<String> addRole(@RequestBody SysRoleDto sysRoleDto) {
        sysRoleService.addRole(sysRoleDto);
        return Result.success();
    }

    /**
     * 删除权限
     */
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public Result<String> deleteRole(@PathVariable Long id) {
        sysRoleService.deleteRole(id);
        return Result.success();
    }

    /**
     * 修改权限
     */
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('admin')")
    public Result<String> updateRole(@RequestBody SysRoleDto sysRoleDto) {
        sysRoleService.updateRole(sysRoleDto);
        return Result.success();
    }

    /**
     * 根据角色权限key查询用户角色细腻系
     */
    @GetMapping("/key/{role}")
    public SysRole getByRoleKey(@PathVariable String role) {
        return sysRoleService.getRoleKey(role);
    }

    /**
     * 根据id获取权限信息
     */
    @GetMapping("/menu/{roleId}")
    @PreAuthorize("hasAuthority('admin')")
    public Result<List<SysMenuVo>> getRoleMenu(@PathVariable Long roleId) {
        List<SysMenuVo> sysMenuVoList = sysRoleService.getRoleMenu(roleId);
        return Result.success(sysMenuVoList);
    }

    /**
     * 根据角色identify查询角色信息
     */
    @GetMapping("/identify/{identify}")
    public SysRole getByRoleIdentify(@PathVariable Long identify) {
        LambdaQueryWrapper<SysRole> sysRoleLambdaQueryWrapper = new LambdaQueryWrapper<>();
        sysRoleLambdaQueryWrapper.eq(SysRole::getIdentify, identify);
        return sysRoleService.getOne(sysRoleLambdaQueryWrapper);
    }
}
