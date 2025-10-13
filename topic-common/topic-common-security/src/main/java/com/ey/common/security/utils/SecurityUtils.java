package com.ey.common.security.utils;


import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.exception.TopicException;
import com.ey.common.utils.JWTUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 认证工具类
 */
public class SecurityUtils {
    /**
     * 获取当前登录id
     */
    public static Long getCurrentId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String token = attributes.getRequest().getHeader("Authorization");
            if (token != null) {
                Map<String, Object> tokenInfo = JWTUtils.getTokenInfo(token);
                String idValue = (String) tokenInfo.get("userId");
                if (idValue != null) {
                    return Long.parseLong(idValue);
                }
            }
        }
        throw new TopicException(ResultCodeEnum.LOGIN_ERROR);
    }

    /**
     * 获取当前登录用户名
     * @return
     */
    public static String getCurrentName() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String token = attributes.getRequest().getHeader("Authorization");
            if (token != null) {
                Map<String, Object> tokenInfo = JWTUtils.getTokenInfo(token);
                String username = (String) tokenInfo.get("username");
                if (username != null) {
                    return username;
                }
            }
        }
        throw new TopicException(ResultCodeEnum.LOGIN_ERROR);
    }

    /**
     * 获取当前登录用户角色
     */
    public static String getCurrentRole() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String token = attributes.getRequest().getHeader("Authorization");
            if (token != null) {
                Map<String, Object> tokenInfo = JWTUtils.getTokenInfo(token);
                String role = (String) tokenInfo.get("role");
                if (role != null) {
                    return role;
                }
            }
        }
        throw new TopicException(ResultCodeEnum.LOGIN_ERROR);
    }

    /**
     * 获取当前token
     */
    public static String getToken() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            return attributes.getRequest().getHeader("Authorization");
        } else {
            return null;
        }
    }
}