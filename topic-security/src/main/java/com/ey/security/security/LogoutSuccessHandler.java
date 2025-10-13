package com.ey.security.security;

import com.ey.common.enums.ResultCodeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义登出成功处理器，用于处理用户登出成功的情况
 * 该类实现了 {@link ServerLogoutSuccessHandler} 接口，用于在Spring Security中处理用户登出成功后的操作。
 * 当用户成功登出时，将调用此处理器。
 * 它会清除用户的JWT令牌，并返回一个JSON格式的成功响应。
 */
@Component
public class LogoutSuccessHandler implements ServerLogoutSuccessHandler {

    /**
     * 处理用户登出成功的情况
     * 当用户成功登出时，将调用此方法。
     * 该方法会清除用户的JWT令牌，并返回一个JSON格式的成功响应。
     * @param webFilterExchange 当前的 {@link WebFilterExchange} 对象
     * @param authentication    当前认证的 {@link Authentication} 对象
     * @return {@link Mono<Void>} 表示处理完成
     */
    @SneakyThrows
    @Override
    public Mono<Void> onLogoutSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        //获取响应对象
        ServerHttpResponse response = webFilterExchange.getExchange().getResponse();
        //设置响应头
        HttpHeaders httpHeaders = response.getHeaders();
        httpHeaders.add("Content-Type", "application/json");
        httpHeaders.add("Cache-Control", "no-cache, no-store, must-revalidate");

        //设置响应体
        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("code", String.valueOf(ResultCodeEnum.LOGOUT_SUCCESS));
        responseBody.put("message", ResultCodeEnum.LOGOUT_SUCCESS.getMessage());

        // 使用ObjectMapper将响应体转换为JSON字节数组
        ObjectMapper objectMapper = new ObjectMapper();
        DataBuffer bodyDataBuffer = response.bufferFactory().wrap(objectMapper.writeValueAsBytes(responseBody));
        // 将JSON字节数组写入响应体
        return response.writeWith(Mono.just(bodyDataBuffer));
    }
}
