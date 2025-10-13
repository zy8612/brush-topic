package com.ey.topic.system.controller;

import com.ey.common.result.Result;
import com.ey.model.entity.system.WebConfig;
import com.ey.topic.system.service.SystemService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Description: 系统相关接口
 */
@RestController
@RequestMapping("/system")
public class SystemController {
    @Autowired
    private SystemService systemService;

    /**
     * 获取验证码
     * @param response
     * @return
     */
    @GetMapping("/captchaImage")
    public void getCode(HttpServletResponse response) {
        systemService.getCode(response);
    }

    /**
     * 获取前端配置
     */
    @GetMapping("/config/{status}")
    public Result<WebConfig> getConfig(@PathVariable Integer status) {
        WebConfig config = systemService.getConfig(status);
        return Result.success(config);
    }
}
