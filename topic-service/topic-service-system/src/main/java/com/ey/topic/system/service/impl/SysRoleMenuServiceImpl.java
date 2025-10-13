package com.ey.topic.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ey.model.entity.system.SysRoleMenu;
import com.ey.topic.system.mapper.SysRoleMenuMapper;
import com.ey.topic.system.service.SysRoleMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SysRoleMenuServiceImpl extends ServiceImpl<SysRoleMenuMapper, SysRoleMenu> implements SysRoleMenuService {

    private final SysRoleMenuMapper sysRoleMenuMapper;
    /**
     * 批量插入关系
     * @param relations
     */
    @Override
    public void insertBatch(List<SysRoleMenu> relations) {
        sysRoleMenuMapper.insertBatch(relations);
    }

    /**
     * 删除权限对应的菜单关系
     * @param roelId
     */
    @Override
    public void deleteByRoleId(Long roelId) {
        LambdaQueryWrapper<SysRoleMenu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysRoleMenu::getRoleId,roelId);
        sysRoleMenuMapper.delete(queryWrapper);
    }
}
