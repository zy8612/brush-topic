package com.ey.topic.system.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.exception.TopicException;
import com.ey.model.entity.system.SysMenu;
import com.ey.model.entity.system.SysRoleMenu;
import com.ey.model.vo.system.SysMenuListVo;
import com.ey.model.vo.system.SysMenuVo;
import com.ey.topic.system.mapper.SysMenuMapper;
import com.ey.topic.system.mapper.SysRoleMenuMapper;
import com.ey.topic.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;

    /**
     * 获取用户权限能访问的菜单
     *
     * @param roleId
     * @return
     */
    @Override
    public List<SysMenuVo> getUserMenus(Long roleId) {
        if (roleId == null) {
            throw new TopicException(ResultCodeEnum.NO_ROLE_FAIL);
        }
        //查询用户权限能访问的菜单
        LambdaQueryWrapper<SysRoleMenu> roleMenuQueryWrapper = new LambdaQueryWrapper<>();
        roleMenuQueryWrapper.eq(SysRoleMenu::getRoleId, roleId);
        List<SysRoleMenu> sysRoleMenus = sysRoleMenuMapper.selectList(roleMenuQueryWrapper);
        if (CollectionUtil.isEmpty(sysRoleMenus)) {
            throw new TopicException(ResultCodeEnum.NO_ROLE_FAIL);
        }
        // 提取到菜单id
        List<Long> menuIdList = sysRoleMenus.stream().map(SysRoleMenu::getMenuId).toList();
        if (CollectionUtils.isEmpty(menuIdList)) {
            throw new TopicException(ResultCodeEnum.NO_MENU_FAIL);
        }
        // 批量查询到菜单信息
        List<SysMenu> sysMenus = sysMenuMapper.selectBatchIds(menuIdList);
        if (CollectionUtils.isEmpty(sysMenus)) {
            throw new TopicException(ResultCodeEnum.NO_MENU_FAIL);
        }
        // 排序
        sysMenus.sort(Comparator.comparing(SysMenu::getSorted));
        // 创建返回菜单
        List<SysMenuVo> sysMenuVoList = new ArrayList<>();
        for (SysMenu sysMenu : sysMenus) {
            // 判断层级
            if (sysMenu.getParentId() == 0) {
                SysMenuVo sysMenuVo = new SysMenuVo();
                sysMenuVo.setId(sysMenu.getId());
                sysMenuVo.setKey(sysMenu.getRoute());
                sysMenuVo.setIcon(sysMenu.getIcon());
                sysMenuVo.setLabel(sysMenu.getMenuName());
                // 查询子菜单
                sysMenuVo.setChildren(getUserMenuChildren(sysMenu, sysMenus));
                sysMenuVoList.add(sysMenuVo);
            }
        }
        //如果有父级菜单，但父级菜单不存在
        for (SysMenu sysMenu : sysMenus) {
            //是否存在该菜单的父级菜单
            boolean hasParent = sysMenus.stream()
                    .anyMatch(sysMenu1 -> sysMenu1.getId().equals(sysMenu.getParentId()));
            if (!hasParent && sysMenu.getParentId() != 0) {
                //直接当根菜单处理
                SysMenuVo sysMenuVo = new SysMenuVo();
                sysMenuVo.setId(sysMenu.getId());
                sysMenuVo.setKey(sysMenu.getRoute());
                sysMenuVo.setIcon(sysMenu.getIcon());
                sysMenuVo.setLabel(sysMenu.getMenuName());
                sysMenuVoList.add(sysMenuVo);
            }
        }
        log.info("菜单信息：{}", sysMenuVoList.toArray());
        return sysMenuVoList;
    }

    /**
     * 查询菜单列表
     *
     * @param sysMenu
     * @return
     */
    @Override
    public List<SysMenuListVo> menuList(SysMenu sysMenu) {
        // 构造查询条件
        LambdaQueryWrapper<SysMenu> sysMenuQueryWrapper = new LambdaQueryWrapper<>();
        sysMenuQueryWrapper.orderByAsc(SysMenu::getSorted);

        if (StrUtil.isNotBlank(sysMenu.getMenuName())) {
            sysMenuQueryWrapper.like(SysMenu::getMenuName, sysMenu.getMenuName());
        }
        List<SysMenu> sysMenus = sysMenuMapper.selectList(sysMenuQueryWrapper);
        if (CollectionUtils.isEmpty(sysMenus)) {
            return new ArrayList<>();
        }
        // 封装返回结果
        List<SysMenuListVo> sysMenuListVoList = new ArrayList<>();
        for (SysMenu sysMenuDb : sysMenus) {
            // 如果是根菜单
            if (sysMenuDb.getParentId() == 0) {
                SysMenuListVo sysMenuListVo = new SysMenuListVo();
                BeanUtils.copyProperties(sysMenuDb, sysMenuListVo);
                // 如果有查询条件就查询子菜单
                if (sysMenuDb.getMenuName() != null) {
                    LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<>();
                    queryWrapper.eq(SysMenu::getParentId, sysMenuDb.getId());
                    // 查询的就是子菜单
                    List<SysMenu> childMenus = sysMenuMapper.selectList(queryWrapper);
                    sysMenuListVo.setChildren(getSysMenuChildren(sysMenuListVo, childMenus));
                } else {
                    sysMenuListVo.setChildren(getSysMenuChildren(sysMenuListVo, sysMenus));
                }
                sysMenuListVoList.add(sysMenuListVo);
            }
        }
        return sysMenuListVoList;
    }

    /**
     * 新增菜单
     *
     * @param sysMenu
     */
    @Override
    public void addMenu(SysMenu sysMenu) {
        sysMenuMapper.insert(sysMenu);
    }

    /**
     * 根据id删除菜单
     *
     * @param id
     */
    @Override
    @Transactional
    public void deleteByMenuId(Long id) {
        SysMenu sysMenu = sysMenuMapper.selectById(id);
        if (ObjectUtil.isEmpty(sysMenu)) {
            throw new TopicException(ResultCodeEnum.MENU_ID_NOT_EXIST);
        }
        // 查询是否有子集
        LambdaQueryWrapper<SysMenu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysMenu::getParentId, id);
        if (sysMenuMapper.selectCount(queryWrapper) > 0) {
            throw new TopicException(ResultCodeEnum.MENU_HAS_CHILDREN);
        }
        sysMenuMapper.deleteById(sysMenu.getId());
    }

    @Override
    public void update(SysMenu sysMenu) {
        // 校验id
        if(sysMenu.getId() == null){
            throw new TopicException(ResultCodeEnum.MENU_ID_NOT_EXIST);
        }
        SysMenu sysMenuDb = sysMenuMapper.selectById(sysMenu.getId());
        if(ObjectUtil.isEmpty(sysMenuDb)){
            throw new TopicException(ResultCodeEnum.MENU_ID_NOT_EXIST);
        }
        sysMenuMapper.updateById(sysMenu);
    }

    /**
     * 递归查询子集菜单
     *
     * @param rootMenuListVo
     * @param childMenus
     * @return
     */
    private List<SysMenuListVo> getSysMenuChildren(SysMenuListVo rootMenuListVo, List<SysMenu> childMenus) {
        List<SysMenuListVo> sysMenuListVoList = childMenus.stream()
                .filter(menu -> menu.getParentId().equals(rootMenuListVo.getId()))
                .sorted(Comparator.comparingInt(SysMenu::getSorted))
                .map(item -> {
                    SysMenuListVo sysMenuListVo = new SysMenuListVo();
                    BeanUtils.copyProperties(item, sysMenuListVo);
                    sysMenuListVo.setChildren(getSysMenuChildren(sysMenuListVo, childMenus));
                    return sysMenuListVo;
                }).toList();
        return sysMenuListVoList.isEmpty() ? null : sysMenuListVoList;
    }

    /**
     * 递归查询子集菜单
     *
     * @param sysMenu
     * @param sysMenus
     * @return
     */
    private List<SysMenuVo> getUserMenuChildren(SysMenu sysMenu, List<SysMenu> sysMenus) {
        List<SysMenuVo> children = sysMenus.stream()
                .filter(menu -> menu.getParentId().equals(sysMenu.getId()))
                .sorted(Comparator.comparingInt(SysMenu::getSorted)) // 子菜单排序
                .map(item -> {
                    SysMenuVo sysMenuVo = new SysMenuVo();
                    sysMenuVo.setId(item.getId());
                    sysMenuVo.setKey(item.getRoute());
                    sysMenuVo.setIcon(item.getIcon());
                    sysMenuVo.setLabel(item.getMenuName());
                    sysMenuVo.setChildren(getUserMenuChildren(item, sysMenus));
                    return sysMenuVo;
                }).toList();
        return children.isEmpty() ? null : children;
    }
}
