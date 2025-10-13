package com.ey.security.security;

import com.ey.common.constant.RedisConstant;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.utils.JWTUtils;
import com.ey.security.constant.JwtConstant;
import com.ey.security.properties.AuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.WebFilterChainServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 自定义认证成功处理器，用于处理用户认证成功的情况
 * 该类继承自 {@link WebFilterChainServerAuthenticationSuccessHandler}，用于在Spring Security中处理用户认证成功后的操作。
 * 当用户成功登录时，将调用此处理器。
 * 它会生成JWT令牌，并将其存储在Redis中，同时将令牌作为cookie返回给客户端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationSuccessHandler extends WebFilterChainServerAuthenticationSuccessHandler {

    private final StringRedisTemplate stringRedisTemplate;
    private final AuthProperties authProperties;

    /**
     * 处理用户认证成功的情况
     * 当用户成功登录时，将调用此方法。
     * 该方法会生成JWT令牌，并将其存储在Redis中，同时将令牌作为cookie返回给客户端。
     *
     * @param webFilterExchange 当前的 {@link WebFilterExchange} 对象
     * @param authentication    当前认证的 {@link Authentication} 对象
     * @return {@link Mono <Void>} 表示处理完成
     */
    @SneakyThrows
    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        // 获取当前的ServerWebExchange对象
        ServerWebExchange exchange = webFilterExchange.getExchange();
        // 获取响应对象
        ServerHttpResponse response = exchange.getResponse();
        // 从请求头获取 remember，并正确处理类型转换
        Object remember_obj = exchange.getAttribute("rememberMe");
        boolean remember_me = remember_obj != null &&
                //是Boolean类型，直接使用，非Boolean类型，使用Boolean.parseBoolean()解析字符串为boolean
                (remember_obj instanceof Boolean ? (Boolean) remember_obj : Boolean.parseBoolean(remember_obj.toString()));

        // 设置响应头
        HttpHeaders httpHeaders = response.getHeaders();
        httpHeaders.add("Content-Type", "application/json; charset=UTF-8");
        httpHeaders.add("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");

        // 设置响应体
        HashMap<String, String> responseBody = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        List<? extends GrantedAuthority> list = authentication.getAuthorities().stream().toList();
        try {
            SecurityUserDetails userDetails = (SecurityUserDetails) authentication.getPrincipal();
            //jwt的payload
            Map<String, String> payload = new HashMap<>();
            payload.put("username", authentication.getName());
            payload.put("userId", String.valueOf(userDetails.getUserId()));
            //添加权限
            payload.put("role", list.get(0).getAuthority());

            String token;
            log.info(authentication.toString());

            // 根据Remember-me字段生成不同的JWT令牌
            if(!remember_me) {
                // 如果没有Remember-me，生成一个有效期为24小时的令牌
                token = JWTUtils.creatToken(payload, JwtConstant.EXPIRE_TIME * authProperties.getTimeout());
                // maxAge默认-1，浏览器关闭cookie失效
                response.addCookie(ResponseCookie.from("token", token).path("/").build());
                stringRedisTemplate.opsForValue().set(RedisConstant.USER_LOGIN + authentication.getName(),
                        token, authProperties.getTimeout(), TimeUnit.DAYS);
            } else {
                // 如果有Remember-me，生成一个有效期为180天的令牌
                token = JWTUtils.creatToken(payload, JwtConstant.EXPIRE_TIME * authProperties.getRememberMe());
                // 设置cookie的maxAge为180天
                response.addCookie(ResponseCookie.from("token", token).maxAge(Duration.ofDays(authProperties.getRememberMe())).path("/").build());
                // 保存180天
                stringRedisTemplate.opsForValue().set(RedisConstant.USER_LOGIN + authentication.getName(),
                        token, authProperties.getRememberMe(), TimeUnit.DAYS);
            }
            //添加响应体
            responseBody.put("code", String.valueOf(ResultCodeEnum.SUCCESS.getCode()));
            responseBody.put("msg", ResultCodeEnum.SUCCESS.getMessage());
            responseBody.put("token", token);
        } catch (Exception e) {
            e.printStackTrace();
            //设置失败响应体
            responseBody.put("code", String.valueOf(ResultCodeEnum.FAIL));
            responseBody.put("msg", ResultCodeEnum.FAIL.getMessage());
        }

        // 将响应体转换为JSON字节数组
        DataBuffer bodyDataBuffer = response.bufferFactory().wrap(objectMapper.writeValueAsBytes(responseBody));
        // 将JSON字节数组写入响应体
        return response.writeWith(Mono.just(bodyDataBuffer));
    }
}
