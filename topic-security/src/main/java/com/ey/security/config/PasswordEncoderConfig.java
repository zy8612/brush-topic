package com.ey.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;

/**
 * 覆盖默认密码加密方式
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 创建一个委托密码编码器，指定默认编码器为 BCrypt
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        //设置 BCrypt 强度为 12
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));

        DelegatingPasswordEncoder delegatingPasswordEncoder =
                new DelegatingPasswordEncoder("bcrypt", encoders);
        // 设置默认的密码编码器
        delegatingPasswordEncoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder(12));

        return delegatingPasswordEncoder;
    }
}
