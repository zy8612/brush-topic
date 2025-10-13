package com.ey.web.gateway.filter;

import com.ey.common.constant.RedisConstant;
import com.ey.common.utils.JWTUtils;
import com.ey.web.gateway.properties.IgnoreWhiteProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 网关全局过滤器
 */
@Component
@Slf4j
public class AuthorizeFilter implements Ordered, GlobalFilter {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IgnoreWhiteProperties ignoreWhiteProperties;

    /**
     * 网关登录校验，校验成功把用户信息存入请求，请求转发到微服务时携带用户信息
     *
     * @param exchange 请求访问
     * @param chain    处理请求
     * @return
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1.获取request和response对象
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        String url = request.getURI().getPath();
        // 跳过不需要验证的路径
        if (ignoreWhiteProperties.isWhites(url)) {
            return chain.filter(exchange);
        }

        // 3.获取token
        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // 4.判断token是否存在
        if (com.alibaba.nacos.common.utils.StringUtils.isBlank(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            //无需再处理该响应
            return response.setComplete();
        }

        try {
            // 5.解析JWT获取用户信息
            Map<String, Object> userInfo = JWTUtils.getTokenInfo(token);
            String username = (String) userInfo.get("username");

            // 从Redis验证token
            String cachedToken = stringRedisTemplate.opsForValue()
                    .get(RedisConstant.USER_LOGIN + username);
            if (!token.equals(cachedToken)) {
                log.warn("Token验证失败: {}", username);
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return response.setComplete();
            }

            // 将用户信息传递给下游服务方便获取
            ServerHttpRequest newRequest = request.mutate()
                    .header("userId", String.valueOf(userInfo.get("userId")))
                    .header("username", username)
                    .header("role", String.valueOf(userInfo.get("role")))
                    .build();
            //放行
            return chain.filter(exchange.mutate().request(newRequest).build());

        } catch (Exception e) {
            log.error("Token解析失败: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }
    }

    /**
     * 优先级设置  值越小  优先级越高
     */
    @Override
    public int getOrder() {
        return -1;
    }
}
