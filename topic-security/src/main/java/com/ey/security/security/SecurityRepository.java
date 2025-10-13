package com.ey.security.security;

import cn.hutool.core.util.StrUtil;
import com.ey.common.utils.JWTUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 用户登录后会调用此方法获取权限
 * 自定义安全上下文存储库，用于从请求中加载安全上下文
 * 该类实现了 {@link ServerSecurityContextRepository} 接口，用于在Spring Security中处理安全上下文的加载和保存。
 * 它从请求头中提取JWT令牌，并验证令牌的有效性，然后根据令牌中的信息创建安全上下文。
 * 安全上下文的存储：
 * 将认证成功后的 SecurityContext 对象存储到特定的存储介质中（如 Session、WebSession 或其他自定义存储）。
 * 安全上下文的检索：
 * 在请求处理过程中，从存储介质中获取 SecurityContext 对象，以便后续的授权检查和用户身份验证。
 */
@Slf4j
@Component
public class SecurityRepository implements ServerSecurityContextRepository {
    @Override
    public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
        return Mono.empty();
    }

    /**
     * 所以请求都会调用这个方法
     * 从请求中加载安全上下文
     * 该方法从请求头中提取JWT令牌，并验证令牌的有效性。
     * 如果令牌有效，则根据令牌中的信息创建安全上下文并返回。
     * 如果令牌无效或不存在，则返回一个空的Mono。
     * @param exchange 当前的 {@link ServerWebExchange} 对象
     * @return 包含安全上下文的 {@link Mono<SecurityContext>}
     */
    @Override
    public Mono<SecurityContext> load(ServerWebExchange exchange) {
        String token = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        log.info("获取到的令牌: {}", token);
        // 校验令牌是否合法
        if(StrUtil.isBlank(token) || !JWTUtils.verifyToken(token)) {
            token = null;
        }
        if(StrUtil.isNotBlank(token)) {
            Map<String, Object> tokenInfo = JWTUtils.getTokenInfo(token);
            String username = (String) tokenInfo.get("userName");
            String role = (String) tokenInfo.get("role");

            Collection<GrantedAuthority> authorities = new ArrayList<>();
            // 添加所有需要的权限
            authorities.add(new SimpleGrantedAuthority(role));
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    username,null, authorities);
            SecurityContext context = SecurityContextHolder.getContext();
            context.setAuthentication(authentication);
            log.info("创建的SecurityContext: {}", context);

            return Mono.just(context);
        }
        return Mono.empty();
    }
}
