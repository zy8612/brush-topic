package com.ey.model.vo.system;

import lombok.Data;

import java.util.List;

@Data
public class UserInfoVo {
    private String account;
    private String nickname;
    private String avatar;
    private Integer identity;
    private Long id;
    // 菜单权限
    private List<SysMenuVo> menuList;
}
