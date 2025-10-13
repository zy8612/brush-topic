package com.ey.security.config;

import com.ey.security.filter.CookieToHeadersFilter;
import com.ey.security.security.*;
import com.ey.security.service.SecurityUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DelegatingReactiveAuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.LinkedList;

@Configuration
@Slf4j
@EnableWebFluxSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfiguration {

    private final PasswordEncoderConfig passwordEncoderConfig;
    private final SecurityUserDetailsService securityUserDetailsService;
    private final SecurityRepository securityRepository;
    private final CookieToHeadersFilter cookieToHeadersFilter;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final AccessDeniedHandler accessDeniedHandler;
    private final LogoutHandler logoutHandler;
    private final LogoutSuccessHandler logoutSuccessHandler;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .securityContextRepository(securityRepository)
                .authenticationManager(reactiveAuthenticationManager())
                .addFilterBefore(cookieToHeadersFilter, SecurityWebFiltersOrder.HTTP_HEADERS_WRITER)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/security/user/login").permitAll()  // 登录接口不需要权限
                        .pathMatchers("/system/captcha").permitAll()       // 验证码接口不需要权限
                        .pathMatchers("/security/user/**").permitAll()     // 用户相关的远程接口不要权限 用角色权限一样的
                        .pathMatchers("/ai/model/**").permitAll()          // 流式接口不需要权限
                        .anyExchange().authenticated()                       // 其他所有接口都需要认证
                )

                .exceptionHandling(exceptionHandlingSpec -> exceptionHandlingSpec
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                .logout(logoutSpec -> logoutSpec
                        .logoutUrl("/security/user/logout")
                        .logoutHandler(logoutHandler)
                        .logoutSuccessHandler(logoutSuccessHandler)
                );
        return http.build();
    }

    @Bean
    public ReactiveAuthenticationManager reactiveAuthenticationManager() {
        LinkedList<ReactiveAuthenticationManager> managers = new LinkedList<>();
        //认证管理器调用 SecurityUserDetailsService 加载用户信息
        UserDetailsRepositoryReactiveAuthenticationManager userDetailsManager =
                new UserDetailsRepositoryReactiveAuthenticationManager(securityUserDetailsService);
        //BCrypt密码编码器验证密码
        userDetailsManager.setPasswordEncoder(passwordEncoderConfig.passwordEncoder());
        managers.add(userDetailsManager);

        // 添加一个处理已认证用户的管理器
        managers.add(authentication -> {
            if (authentication.isAuthenticated()) {
                //用户已认证，直接放行
                return Mono.just(authentication);
            }
            return Mono.empty();
        });

        return new DelegatingReactiveAuthenticationManager(managers);
    }

}
