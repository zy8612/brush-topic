package com.ey.topic.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ey.model.entity.system.SysMenu;
import com.ey.model.vo.system.SysMenuListVo;
import com.ey.model.vo.system.SysMenuVo;

import java.util.List;

public interface SysMenuService extends IService<SysMenu> {
    List<SysMenuVo> getUserMenus(Long roleId);

    List<SysMenuListVo> menuList(SysMenu sysMenu);

    void addMenu(SysMenu sysMenu);

    void deleteByMenuId(Long id);

    void update(SysMenu sysMenu);
}
