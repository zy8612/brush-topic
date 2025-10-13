package com.ey.client.system;

import com.ey.common.interceptor.TokenInterceptor;
import com.ey.common.result.Result;
import com.ey.model.dto.system.SysNoticeDto;
import com.ey.model.entity.system.SysRole;
import com.ey.model.vo.system.SysMenuVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * Description: 系统远程服务客户端 已被【认证服务】调用
 */
@FeignClient(name = "service-system", configuration = TokenInterceptor.class)
public interface SystemFeignClient {

    /**
     * 根据id查询权限信息
     * @param id
     */
    @GetMapping("/system/role/{id}")
    SysRole getSysRoleById(@PathVariable("id") Long id);

    /**
     * 根据角色权限key查询用户角色信息
     */
    @GetMapping("/system/role/key/{role}")
    SysRole getByRoleKey(@PathVariable String role);

    /**
     * 根据角色id查询菜单信息
     * @param roleId
     * @return
     */
    @GetMapping("/system/menu/userMenu/{roleId}")
    List<SysMenuVo> userMenu(@PathVariable Long roleId);

    @PostMapping("/system/notice/record")
    Result<String> recordNotice(@RequestBody SysNoticeDto sysNoticeDto);

}
