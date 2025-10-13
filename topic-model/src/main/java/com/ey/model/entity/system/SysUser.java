package com.ey.model.entity.system;

import com.ey.model.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysUser extends BaseEntity {
    private String account;
    private String avatar;
    private String password;
    private String email;
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime memberTime;
    private String nickname;
}
