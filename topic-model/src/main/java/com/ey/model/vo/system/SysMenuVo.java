package com.ey.model.vo.system;

import lombok.Data;

import java.util.List;

@Data
public class SysMenuVo {
    // 路由
    private String key;
    // 图标
    private String icon;
    // 菜单名称
    private String label;
    // id
    private Long id;
    // 子菜单
    private List<SysMenuVo> children;
}
