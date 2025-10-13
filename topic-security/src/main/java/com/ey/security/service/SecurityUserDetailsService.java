package com.ey.security.service;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ey.client.system.SystemFeignClient;
import com.ey.common.constant.ExceptionConstant;
import com.ey.model.entity.system.SysUser;
import com.ey.model.entity.system.SysUserRole;
import com.ey.security.mapper.SysUserRoleMapper;
import com.ey.security.security.SecurityUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collection;


/**
 * 只有用户登录请求才会调用此方法
 * 自定义用户详细信息服务，用于从数据库中加载用户信息和权限
 * 该类实现了 {@link ReactiveUserDetailsService} 接口，用于在Spring Security中加载用户详细信息。
 * 它通过调用 {@link SysUserService} 从数据库中获取用户信息，并根据用户的角色设置相应的权限。
 */
@RequiredArgsConstructor
@Slf4j
@Service
public class SecurityUserDetailsService implements ReactiveUserDetailsService {

    private final SysUserService sysUserService;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SystemFeignClient systemFeignClient;

    /**
     * 要验证权限都会经过这个方法
     * 根据用户名加载用户详细信息
     * 该方法根据提供的用户名从数据库中加载用户信息，并设置相应的角色权限。
     * 如果用户不存在，则抛出 {@link UsernameNotFoundException} 异常。
     *
     * @param account 用户名
     * @return 包含用户详细信息的 {@link Mono<UserDetails>}
     * @throws UsernameNotFoundException 如果用户不存在
     */
    @Override
    public Mono<UserDetails> findByUsername(String account) {
        log.info(account);
        // 调用数据库根据用户名获取用户
        SysUser sysUser = sysUserService.findByUserName(account);

        //用户不存在
        if (ObjectUtil.isEmpty(sysUser)) {
            //创建一个失败的Mono，表示操作因异常而终止
            return Mono.error(new UsernameNotFoundException(ExceptionConstant.USER_NOT_EXIST));
        }
        //用户被禁用
        if (sysUser.getStatus() == 1) {
            return Mono.error(new UsernameNotFoundException(ExceptionConstant.USER_BEEN_DISABLED));
        }
        log.info(sysUser.toString());
        return Mono.fromCallable(() -> {
                    // 根据用户id获取用户角色信息
                    LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
                    queryWrapper.eq(SysUserRole::getUserId, sysUser.getId());
                    return sysUserRoleMapper.selectOne(queryWrapper);
                })
                //将任务调度到有界弹性线程池执行（避免阻塞Reactor主线程）
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(userRole -> {
                    //如果用户没有角色
                    if (ObjectUtil.isEmpty(userRole)) {
                        return Mono.error(new UsernameNotFoundException(ExceptionConstant.USER_LACK_ROLE));
                    }
                    return Mono.fromCallable(() -> systemFeignClient.getSysRoleById(userRole.getRoleId()))
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .flatMap(sysRole -> {
                    //如果有权限id，根据权限id查询不到对应权限
                    if (ObjectUtil.isEmpty(sysRole)) {
                        return Mono.error(new UsernameNotFoundException(ExceptionConstant.ROLE_NOT_EXIST));
                    }

                    Collection<GrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority(sysRole.getRoleKey()));

                    return Mono.just(new SecurityUserDetails(
                            sysUser.getAccount(),
                            //Spring Security 会自动识别 {bcrypt} 前缀并使用 BCryptPasswordEncoder
                            "{bcrypt}" + sysUser.getPassword(),
                            authorities,
                            sysUser.getId()
                    ));
                });
    }
}
