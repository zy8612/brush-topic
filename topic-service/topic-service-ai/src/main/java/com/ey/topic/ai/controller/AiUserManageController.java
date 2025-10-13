package com.ey.topic.ai.controller;

import com.ey.common.result.Result;
import com.ey.model.dto.ai.AiUserDto;
import com.ey.model.entity.ai.AiUser;
import com.ey.topic.ai.service.AiUserManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Description: 用户ai管理
 */
@RestController
@RequestMapping("/ai/manage")
@RequiredArgsConstructor
public class AiUserManageController {

    private final AiUserManageService aiUserManageService;

    /**
     * 查询用户AI列表
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('admin')")
    public Result<Map<String, Object>> list(AiUserDto aiUserDto) {
        return Result.success(aiUserManageService.list(aiUserDto));
    }


    /**
     * 修改用户AI
     */
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('admin')")
    public Result<String> update(@RequestBody AiUser aiUser) {
        aiUserManageService.update(aiUser);
        return Result.success();
    }
}
