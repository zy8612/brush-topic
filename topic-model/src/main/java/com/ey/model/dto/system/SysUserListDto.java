package com.ey.model.dto.system;

import lombok.Data;

@Data
public class SysUserListDto {
    private String account;
    private String roleName;
    private String beginCreateTime;
    private String endCreateTime;
    private String beginMemberTime;
    private String endMemberTime;
    private Integer pageNum;
    private Integer pageSize;
}
