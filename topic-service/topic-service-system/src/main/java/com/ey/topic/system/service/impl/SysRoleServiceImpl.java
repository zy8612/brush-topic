package com.ey.topic.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ey.client.security.SecurityFeignClient;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.exception.TopicException;
import com.ey.model.dto.system.SysRoleDto;
import com.ey.model.entity.system.SysRole;
import com.ey.model.entity.system.SysRoleMenu;
import com.ey.model.vo.system.SysMenuVo;
import com.ey.topic.system.mapper.SysRoleMapper;
import com.ey.topic.system.service.SysMenuService;
import com.ey.topic.system.service.SysRoleMenuService;
import com.ey.topic.system.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuService sysRoleMenuService;
    private final SecurityFeignClient securityFeignClient;
    private final SysMenuService sysMenuService;

    /**
     * 获取权限列表
     *
     * @return
     */
    @Override
    public Map<String, Object> roleList(SysRole sysRole) {
        Page<SysRole> page = new Page<>(sysRole.getPageNum(), sysRole.getPageSize());
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<SysRole>();
        // 权限名称校验
        if (StrUtil.isNotBlank(sysRole.getName())) {
            queryWrapper.like(SysRole::getName, sysRole.getName());
        }
        // 分页查询
        Page<SysRole> pageResult = sysRoleMapper.selectPage(page, queryWrapper);
        return Map.of(
                "total", pageResult.getTotal(),
                "rows", pageResult.getRecords()
        );
    }

    @Override
    @Transactional
    public void addRole(SysRoleDto sysRoleDto) {
        SysRole sysRole = new SysRole();
        BeanUtils.copyProperties(sysRoleDto, sysRole);
        // 新增权限
        sysRoleMapper.insert(sysRole);
        // 权限能访问的菜单
        if (CollectionUtil.isNotEmpty(sysRoleDto.getMenuIds())) {
            // 获取要插入的权限-菜单关系
            List<SysRoleMenu> relations = addRelations(sysRoleDto, sysRole.getId());
            // 一次插入所有权限-菜单对应关系
            sysRoleMenuService.insertBatch(relations);
        }
    }

    /**
     * 删除权限
     *
     * @param roelId
     */
    @Override
    @Transactional
    public void deleteRole(Long roelId) {
        if (roelId == null) {
            throw new TopicException(ResultCodeEnum.DEL_ROLE_ERROR);
        }
        if (securityFeignClient.getByRoleId(roelId)) {
            throw new TopicException(ResultCodeEnum.ROLE_USER_ERROR);
        }
        // 删除权限-菜单关系
        sysRoleMenuService.deleteByRoleId(roelId);
        // 删除权限
        sysRoleMapper.deleteById(roelId);
    }

    @Override
    @Transactional
    public void updateRole(SysRoleDto sysRoleDto) {
        SysRole sysRole = sysRoleMapper.selectById(sysRoleDto.getId());
        if (ObjectUtil.isEmpty(sysRole)) {
            throw new TopicException(ResultCodeEnum.ROLE_NOT_EXIST);
        }
        BeanUtils.copyProperties(sysRoleDto, sysRole);
        // 修改权限信息
        sysRoleMapper.updateById(sysRole);
        // 修改菜单关系表
        if (CollectionUtil.isNotEmpty(sysRoleDto.getMenuIds())) {
            // 先删除原本的
            sysRoleMenuService.deleteByRoleId(sysRole.getId());
            // 再添加新的
            List<SysRoleMenu> relations = addRelations(sysRoleDto, sysRoleDto.getId());
            sysRoleMenuService.insertBatch(relations);
        } else {
            sysRoleMenuService.deleteByRoleId(sysRole.getId());
        }
    }

    // 添加权限-菜单通用方法
    private List<SysRoleMenu> addRelations(SysRoleDto sysRoleDto, Long roleId) {
        // 封装角色菜单数据
        return sysRoleDto.getMenuIds().stream().map(id -> {
            SysRoleMenu sysRoleMenu = new SysRoleMenu();
            sysRoleMenu.setRoleId(roleId);
            sysRoleMenu.setMenuId(id);
            return sysRoleMenu;
        }).toList();
    }

    @Override
    public List<SysMenuVo> getRoleMenu(Long roleId) {
        if(roleId == null){
            return null;
        }
        try {
            // 查询角色能访问的菜单
            return sysMenuService.getUserMenus(roleId);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public SysRole getRoleKey(String roleKey) {
        LambdaQueryWrapper<SysRole> sysRoleLambdaQueryWrapper = new LambdaQueryWrapper<>();
        sysRoleLambdaQueryWrapper.eq(SysRole::getRoleKey, roleKey);
        SysRole sysRole = sysRoleMapper.selectOne(sysRoleLambdaQueryWrapper);
        if (ObjectUtil.isEmpty(sysRole)) {
            return null;
        }
        return sysRole;
    }
}
