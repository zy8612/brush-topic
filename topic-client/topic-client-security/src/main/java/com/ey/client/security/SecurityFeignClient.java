package com.ey.client.security;

import com.ey.common.interceptor.TokenInterceptor;
import com.ey.model.dto.system.SysUserDto;
import com.ey.model.dto.system.SysUserListDto;
import com.ey.model.entity.system.SysUser;
import com.ey.model.excel.sytem.SysUserExcel;
import com.ey.model.excel.sytem.SysUserExcelExport;
import com.ey.model.vo.topic.TopicDataVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Description: 授权认证服务客户端 已被【系统服务】调用
 */
@FeignClient(name = "topic-security", configuration = TokenInterceptor.class)
public interface SecurityFeignClient {

    /**
     * 获取用户列表
     *
     * @param sysUserListDto
     * @return
     */
    //@SpringQueryMap 将一个Java对象的属性自动映射为HTTP请求的查询参数
    @GetMapping("/security/user/list")
    Map<String, Object> userList(@SpringQueryMap SysUserListDto sysUserListDto);

    /**
     * 新增用户
     *
     * @param sysUserDto
     */
    @PostMapping("/security/user/add")
    void addUser(@RequestBody SysUserDto sysUserDto);

    /**
     * 批量删除用户
     *
     * @param ids
     */
    @DeleteMapping("/security/user/delete/{ids}")
    void deleteUsers(@PathVariable String[] ids);

    /**
     * 修改用户
     *
     * @param sysUserDto
     */
    @PutMapping("security/user/update")
    void updateUser(@RequestBody SysUserDto sysUserDto);

    /**
     * 判断该权限下是否存在用户
     *
     * @param roleId
     * @return
     */
    @GetMapping("/security/user/getByRoleId/{roleId}")
    Boolean getByRoleId(@PathVariable Long roleId);

    @GetMapping("/security/user/getByUserId/{userId}")
    SysUser getByUserId(@PathVariable Long userId);

    /**
     * 获取excelVo数据
     *
     * @param sysUserListDto
     * @param ids
     * @return
     */
    @GetMapping("/security/user/export/{ids}")
    List<SysUserExcelExport> getExcelVo(SysUserListDto sysUserListDto, @PathVariable Long[] ids);

    /**
     * 将excel数据插入到数据库
     *
     * @param excelVoList
     * @param updateSupport
     */
    @PostMapping("/security/user/import")
    String importExcel(@RequestBody List<SysUserExcel> excelVoList, @RequestParam("updateSupport") Boolean updateSupport);

    @GetMapping("/security/user/count")
    Long countTotalUser();

    @GetMapping("/security/user/count/{date}")
    Long countUser(@PathVariable String date);

    @GetMapping("/security/user/countUserDay7")
    List<TopicDataVo> countUserDay7();

    @GetMapping("/security/user/getAllUser")
    public List<SysUser> getAllUser();
}
