package com.ey.topic.system.controller;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ey.client.security.SecurityFeignClient;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.exception.TopicException;
import com.ey.common.result.Result;
import com.ey.model.dto.system.SysUserDto;
import com.ey.model.dto.system.SysUserListDto;
import com.ey.model.entity.system.SysRole;
import com.ey.model.excel.sytem.SysUserExcel;
import com.ey.model.excel.sytem.SysUserExcelExport;
import com.ey.model.vo.system.SysRoleVo;
import com.ey.service.utils.helper.MinioHelper;
import com.ey.service.utils.utils.ExcelUtil;
import com.ey.common.enums.RoleEnum;
import com.ey.topic.system.service.SysRoleService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Description: 用户控制器
 */
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SecurityFeignClient securityFeignClient;
    private final SysRoleService sysRoleService;
    private final MinioHelper minioHelper;

    /**
     * 获取用户列表
     *
     * @param sysUserListDto
     * @return
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('admin')")
    public Result<Map<String, Object>> userList(SysUserListDto sysUserListDto) {
        Map<String, Object> map = securityFeignClient.userList(sysUserListDto);
        return Result.success(map);
    }

    /**
     * 查询角色列表
     *
     * @return
     */
    @GetMapping("/roleList")
    @PreAuthorize("hasAuthority('admin')")
    public Result<List<SysRoleVo>> roleList() {
        List<SysRole> roles = sysRoleService.list();
        List<SysRoleVo> roleVoList = roles.stream().map(role -> {
            SysRoleVo sysRoleVo = new SysRoleVo();
            sysRoleVo.setRoleName(role.getName());
            return sysRoleVo;
        }).toList();
        return Result.success(roleVoList);
    }

    /**
     * 文件上传
     *
     * @param file
     * @return
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("avatar") MultipartFile file) {
        System.out.println(file.getOriginalFilename());
        // 上传文件
        String url = minioHelper.uploadFile(file, "avatar");
        return Result.success(url);
    }

    /**
     * 新增用户
     */
    @PostMapping("/add")
    @PreAuthorize("hasAuthority('admin')")
    public Result<String> addUser(@RequestBody SysUserDto sysUserDto) {
        Boolean result = prepareSysUserDto(sysUserDto);
        if (!result) {
            return Result.fail(ResultCodeEnum.ROLE_NO_EXIST);
        }
        try {
            securityFeignClient.addUser(sysUserDto);
        } catch (Exception e) {
            return Result.fail(ResultCodeEnum.ADD_USER_ERROR);
        }
        return Result.success();
    }

    /**
     * 添加用户时准备数据校验角色
     *
     * @param sysUserDto
     * @return
     */
    private Boolean prepareSysUserDto(SysUserDto sysUserDto) {
        // 查询角色是否存在
        LambdaQueryWrapper<SysRole> sysRoleLambdaQueryWrapper = new LambdaQueryWrapper<>();
        sysRoleLambdaQueryWrapper.eq(SysRole::getName, sysUserDto.getRoleName());
        SysRole sysRole = sysRoleService.getOne(sysRoleLambdaQueryWrapper);
        if (ObjectUtil.isEmpty(sysRole)) {
            return false;
        }
        // 如果是会员添加会员时间
        if (Objects.equals(sysRole.getIdentify(), RoleEnum.MEMBER.getIdentify())) {
            // 添加当前时间格式化
            LocalDateTime now = LocalDateTime.now();
            sysUserDto.setMemberTime(now);  // 直接设置 LocalDate
        }
        // 存在赋值
        sysUserDto.setRoleId(sysRole.getId());
        return true;
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/delete/{ids}")
    @PreAuthorize("hasAuthority('admin')")
    public Result<String> deleteUsers(@PathVariable String[] ids) {
        securityFeignClient.deleteUsers(ids);
        return Result.success();
    }

    /**
     * 修改用户
     */
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('admin')")
    public Result<String> updateUser(@RequestBody SysUserDto sysUserDto) {
        Boolean result = prepareSysUserDto(sysUserDto);
        if (!result) {
            return Result.fail(ResultCodeEnum.ROLE_NO_EXIST);
        }
        securityFeignClient.updateUser(sysUserDto);
        return Result.success();
    }

    /**
     * 导出excel
     */
    @GetMapping("/export/{ids}")
    public void exportExcel(HttpServletResponse response, SysUserListDto sysUserListDto, @PathVariable Long[] ids) {
        List<SysUserExcelExport> sysUserExcelExports = securityFeignClient.getExcelVo(sysUserListDto, ids);
        if (CollectionUtils.isEmpty(sysUserExcelExports)) {
            throw new TopicException(ResultCodeEnum.EXPORT_ERROR);
        }
        // 导出
        try {
            ExcelUtil.download(response, sysUserExcelExports, SysUserExcelExport.class);
        } catch (IOException e) {
            throw new TopicException(ResultCodeEnum.EXPORT_ERROR);
        }
    }

    /**
     * 导入excel
     */
    @PostMapping("/import")
    public Result<String> importExcel(@RequestParam("file") MultipartFile multipartFile, @RequestParam("updateSupport") Boolean updateSupport) {
        try {
            List<SysUserExcel> excelVoList = EasyExcel.read(multipartFile.getInputStream())
                    // 映射数据
                    .head(SysUserExcel.class)
                    // 读取工作表
                    .sheet()
                    // 读取并同步返回数据
                    .doReadSync();
            // 封装数据插入到数据库中
            String s = securityFeignClient.importExcel(excelVoList, updateSupport);
            return Result.success(s);
        } catch (Exception e) {
            // 打印异常消息
            System.out.println("Exception message: " + e.getMessage());
            // 正则表达式匹配 message 字段
            String regex = "\"message\":\"(.*?)\"";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(e.getMessage());
            System.out.println(matcher);
            if (matcher.find()) {
                return Result.fail(matcher.group(1), ResultCodeEnum.IMPORT_ERROR);
            } else {
                // 如果匹配不到，直接抛出原始异常消息
                return Result.fail(e.getMessage(), ResultCodeEnum.IMPORT_ERROR);
            }
        }
    }

    /**
     * 下载excel模板
     */
    @GetMapping("/template")
    public void getExcelTemplate(HttpServletResponse response) {
        // 存储模板数据
        List<SysUserExcel> excelVoList = new ArrayList<>();
        // 组成模板数据
        SysUserExcel excelVo = new SysUserExcel();
        // 存放
        excelVoList.add(excelVo);
        try {
            // 导出
            ExcelUtil.download(response, excelVoList, SysUserExcel.class);
        } catch (IOException e) {
            throw new TopicException(ResultCodeEnum.DOWNLOAD_ERROR);
        }
    }
}
