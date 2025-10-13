package com.ey.model.entity.system;

import com.ey.model.entity.BaseEntity;
import lombok.Data;

@Data
public class SysRole extends BaseEntity {
    private String name;
    private Integer identify;
    private String remark;
    private String roleKey;
}
