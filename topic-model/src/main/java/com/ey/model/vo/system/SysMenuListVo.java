package com.ey.model.vo.system;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SysMenuListVo {
    private Long id;
    private String menuName;
    private Integer sorted;
    private String route;
    private String icon;
    private Long parentId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    // 子菜单
    private List<SysMenuListVo> children;
}
