package com.ey.topic.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ey.model.entity.ai.AiUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiUserManageMapper extends BaseMapper<AiUser> {
}
