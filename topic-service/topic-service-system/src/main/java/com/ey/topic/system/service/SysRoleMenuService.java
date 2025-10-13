package com.ey.topic.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ey.model.entity.system.SysRoleMenu;

import java.util.List;

public interface SysRoleMenuService extends IService<SysRoleMenu> {
    void insertBatch(List<SysRoleMenu> relations);

    void deleteByRoleId(Long roelId);
}
