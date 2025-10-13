package com.ey.topic.system.service.impl;

import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.generator.RandomGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ey.common.constant.RedisConstant;
import com.ey.model.entity.system.WebConfig;
import com.ey.topic.system.constant.SystemConstant;
import com.ey.topic.system.mapper.WebConfigMapper;
import com.ey.topic.system.service.SystemService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SystemServiceImpl implements SystemService {

    private final StringRedisTemplate stringRedisTemplate;
    private final WebConfigMapper webConfigMapper;

    public void getCode(HttpServletResponse response) {
        // 创建随机数生成器
        RandomGenerator randomGenerator = new RandomGenerator("0123456789", 4);
        // Hutool工具包的LineCaptcha创建一个指定宽高的图形验证码，并将随机生成的字符串设置为验证码内容
        LineCaptcha lineCaptcha = new LineCaptcha(SystemConstant.IMAGE_WIDTH,  SystemConstant.IMAGE_HEIGHT);
        // 绑定随机生成器
        lineCaptcha.setGenerator(randomGenerator);
        // 调用 createCode() 生成带干扰线的图片
        lineCaptcha.createCode();
        // 设置返回数据类型
        response.setContentType("image/jpeg");
        // 禁止使用缓存,如果被浏览器缓存，用户刷新页面时可能会重复使用旧的验证码图片，导致验证逻辑失效
        response.setHeader("Pragma", "No-cache");
        try {
            // 输出到页面
            lineCaptcha.write(response.getOutputStream());
            // 关闭流
            response.getOutputStream().close();
            // 写入redis缓存
            stringRedisTemplate.opsForValue().set(RedisConstant.VERIFICATION_CODE,
                    lineCaptcha.getCode(),
                    SystemConstant.CODE_EXPIRE_TIME,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public WebConfig getConfig(Integer status) {
        LambdaQueryWrapper<WebConfig> webConfigLambdaQueryWrapper = new LambdaQueryWrapper<>();
        webConfigLambdaQueryWrapper.eq(WebConfig::getStatus, status);
        return webConfigMapper.selectOne(webConfigLambdaQueryWrapper);
    }
}
