package com.ey.topic.system.controller;

import com.ey.common.result.Result;
import com.ey.model.entity.system.SysMenu;
import com.ey.model.vo.system.SysMenuListVo;
import com.ey.model.vo.system.SysMenuVo;
import com.ey.topic.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Description: 菜单管理
 */
@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService sysMenuService;

    /**
     * 查询用户权限能访问的菜单
     */
    @GetMapping("/userMenu/{roleId}")
    public List<SysMenuVo> userMenus(@PathVariable Long roleId) {
        return sysMenuService.getUserMenus(roleId);
    }

    /**
     * 查询菜单列表
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('admin')") // 只有admin权限能访问
    public Result<List<SysMenuListVo>> listMenus(SysMenu sysMenu) {
        List<SysMenuListVo> menuList = sysMenuService.menuList(sysMenu);
        return Result.success(menuList);
    }

    /**
     * 新增菜单
     */
    @PostMapping("/add")
    @PreAuthorize("hasAuthority('admin')")
    public Result<String> addMenu(@RequestBody SysMenu sysMenu) {
        sysMenuService.addMenu(sysMenu);
        return Result.success();
    }

    /**
     * 删除菜单
     */
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('admin')")
    public Result<String> deleteMenu(@PathVariable Long id) {
        sysMenuService.deleteByMenuId(id);
        return Result.success();
    }

    /**
     * 修改菜单数据
     */
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('admin')")
    public Result<String> updateMenu(@RequestBody SysMenu sysMenu) {
        sysMenuService.update(sysMenu);
        return Result.success();
    }

}
