package com.ey.security.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Description: 读取配置白名单
 */
@Configuration
@ConfigurationProperties(prefix = "security.auth")
public class AuthProperties {

    /**
     * 登录超时时间
     */
    private int timeout;

    /**
     * 记住我时间
     */
    private int rememberMe;

    /**
     * h5登录超时时间
     * @return
     */
    private int h5Timeout;

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(int rememberMe) {
        this.rememberMe = rememberMe;
    }

    public int getH5Timeout() {
        return h5Timeout;
    }

    public void setH5Timeout(int h5Timeout) {
        this.h5Timeout = h5Timeout;
    }
}
