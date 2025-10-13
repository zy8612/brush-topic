package com.ey.model.entity.system;

import com.ey.model.entity.BaseEntity;
import lombok.Data;

@Data
public class SysMenu extends BaseEntity {
    private String menuName;
    private Integer sorted;
    private String route;
    private String icon;
    private Long parentId;
}
