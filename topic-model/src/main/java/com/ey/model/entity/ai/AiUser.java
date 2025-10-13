package com.ey.model.entity.ai;

import com.ey.model.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiUser extends BaseEntity {
    // 账户
    private String account;
    // 用户id
    private Long userId;
    // ai使用次数
    private Long aiCount;
    // ai总次数
    private Long count;
    // 最近使用时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime recentlyUsedTime;
    // 状态
    private Integer status;
    private String roleName;
}
