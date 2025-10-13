package com.ey.topic.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ey.model.dto.ai.AiUserDto;
import com.ey.model.entity.ai.AiUser;

import java.util.Map;

public interface AiUserManageService extends IService<AiUser> {
    Map<String, Object> list(AiUserDto aiUserDto);

    void update(AiUser aiUser);
}
