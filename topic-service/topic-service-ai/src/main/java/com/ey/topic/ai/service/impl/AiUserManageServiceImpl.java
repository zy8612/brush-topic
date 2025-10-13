package com.ey.topic.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ey.model.dto.ai.AiUserDto;
import com.ey.model.entity.ai.AiUser;
import com.ey.topic.ai.mapper.AiUserManageMapper;
import com.ey.topic.ai.service.AiUserManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiUserManageServiceImpl extends ServiceImpl<AiUserManageMapper, AiUser> implements AiUserManageService {

    private final AiUserManageMapper aiUserManageMapper;

    @Override
    public Map<String, Object> list(AiUserDto aiUserDto) {
        // 设置分页参数
        Page<AiUser> aiUserPage = new Page<>(aiUserDto.getPageNum(), aiUserDto.getPageSize());
        // 设置分页条件
        LambdaQueryWrapper<AiUser> aiUserLambdaQueryWrapper = new LambdaQueryWrapper<>();
        if (!StrUtil.isEmpty(aiUserDto.getAccount())) {
            aiUserLambdaQueryWrapper.like(AiUser::getAccount, aiUserDto.getAccount());
        }
        aiUserLambdaQueryWrapper.orderByDesc(AiUser::getRecentlyUsedTime);
        // 开始查询
        Page<AiUser> aiUserPageDb = aiUserManageMapper.selectPage(aiUserPage, aiUserLambdaQueryWrapper);

        return Map.of(
                "total", aiUserPageDb.getTotal(),
                "rows", aiUserPageDb.getRecords()
        );
    }

    @Override
    public void update(AiUser aiUser) {
        aiUserManageMapper.updateById(aiUser);
    }
}
