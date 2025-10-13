package com.ey.security.controller;

import cn.hutool.core.util.StrUtil;
import com.ey.common.constant.ExceptionConstant;
import com.ey.common.constant.RedisConstant;
import com.ey.common.result.Result;
import com.ey.model.dto.system.SysUserDto;
import com.ey.model.dto.system.SysUserListDto;
import com.ey.model.entity.system.SysUser;
import com.ey.model.excel.sytem.SysUserExcel;
import com.ey.model.excel.sytem.SysUserExcelExport;
import com.ey.model.vo.system.UserInfoVo;
import com.ey.model.vo.topic.TopicDataVo;
import com.ey.security.dto.*;
import com.ey.security.security.AuthenticationSuccessHandler;
import com.ey.security.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.handler.DefaultWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/security/user")
@RequiredArgsConstructor
public class SecurityController {

    private final SysUserService sysUserService;
    private final ReactiveAuthenticationManager authenticationManager;
    private final AuthenticationSuccessHandler authenticationSuccessHandler;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 登录接口
     *
     * @param exchange        Spring WebFlux核心上下文对象
     *                        作用：封装HTTP请求-响应的完整上下文信息
     * @param loginRequestDto 用户输入的登录数据
     * @return
     */
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> login(ServerWebExchange exchange, @Validated @RequestBody LoginRequestDto loginRequestDto) {
        //从缓存中获取验证码
        String verificationCode = stringRedisTemplate.opsForValue().get(RedisConstant.VERIFICATION_CODE);
        //校验验证码
        if (StrUtil.isBlank(verificationCode) || !StrUtil.equals(verificationCode, loginRequestDto.getCode())) {
            log.info("验证码错误");
            return Mono.error(new RuntimeException(ExceptionConstant.CODE_ERROR));
        }

        exchange.getAttributes().put("rememberMe", loginRequestDto.getRemember());

        //委托认证管理器链执行认证
        return authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                loginRequestDto.getUsername(),
                                loginRequestDto.getPassword()
                        )
                )
                .flatMap(authentication -> {
                    // 登录成功后删除Redis中的验证码
                    stringRedisTemplate.delete(RedisConstant.VERIFICATION_CODE);

                    //登录请求已通过前期过滤器链（认证、授权过滤器），构建一个不包含任何额外过滤器的"纯净"执行环境
                    List<WebFilter> webFilters = new ArrayList<>();
                    DefaultWebFilterChain webFilterChain = new DefaultWebFilterChain(
                            exchange1 -> Mono.empty(),
                            webFilters);

                    WebFilterExchange webFilterExchange = new WebFilterExchange(exchange, webFilterChain);
                    return authenticationSuccessHandler.onAuthenticationSuccess(webFilterExchange, authentication);
                })
                .onErrorResume(e -> {
                    log.error("认证失败", e);
                    return Mono.error(e);
                });
    }

    /**
     * 根据token获取用户信息
     *
     * @param token
     * @return
     */
    @GetMapping("/userInfo")
    public Result<UserInfoVo> getUserInfo(String token) {
        UserInfoVo userInfoVo = sysUserService.getUserInfo(token);
        return Result.success(userInfoVo);
    }

    @GetMapping("/getByUserId/{userId}")
    public SysUser getByUserId(@PathVariable Long userId) {
        return sysUserService.getById(userId);
    }

    /**
     * 查询用户列表
     *
     * @param sysUserListDto
     * @return
     */
    @GetMapping("/list")
    public Map<String, Object> userList(SysUserListDto sysUserListDto) {
        return sysUserService.userList(sysUserListDto);
    }

    /**
     * 新增用户
     */
    @PostMapping("/add")
    public void addUser(@RequestBody SysUserDto sysUserDto) {
        sysUserService.addUser(sysUserDto);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/delete/{ids}")
    public void deleteUsers(@PathVariable String[] ids) {
        sysUserService.deleteUsers(ids);
    }

    /**
     * 修改用户
     */
    @PutMapping("/update")
    public void updateUser(@RequestBody SysUserDto sysUserDto) {
        sysUserService.updateUser(sysUserDto);
    }

    /**
     * 判断当前权限下是否存在用户
     */
    @GetMapping("/getByRoleId/{roleId}")
    public Boolean getByRoleId(@PathVariable Long roleId) {
        return sysUserService.getByRoleId(roleId);
    }

    /**
     * 获取excelVo数据
     */
    @RequestMapping("/export/{ids}")
    public List<SysUserExcelExport> getExcelVo(SysUserListDto sysUserListDto, @PathVariable Long[] ids) {
        return sysUserService.getExcelVo(sysUserListDto, ids);
    }

    /**
     * 导入excel数据
     */
    @PostMapping("import")
    public String importExcel(@RequestBody List<SysUserExcel> excelVoList, @RequestParam("updateSupport") Boolean updateSupport) {
        return sysUserService.importExcel(excelVoList, updateSupport);
    }

    /**
     * h5个人中心获取用户信息
     */
    @GetMapping("/info/{id}")
    public SysUser getUserInfo(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        // 隐藏密码防止泄露
        user.setPassword(null);
        return user;
    }

    /**
     * 个人中心获取用户信息
     */
    @GetMapping("/detailInfo/{id}")
    public Result<SysUser> getUserDetailInfo(@PathVariable Long id) {
        SysUser user = sysUserService.getById(id);
        // 隐藏密码防止泄露
        user.setPassword(null);
        return Result.success(user);
    }

    @GetMapping("/getAllUser")
    public List<SysUser> getAllUser() {
        return sysUserService.list();
    }

    /**
     * 保存头像
     */
    @PutMapping("/avatar")
    public Result<String> saveAvatar(@RequestBody UserDto userDto) {
        sysUserService.saveAvatar(userDto);
        return Result.success();
    }

    /**
     * 修改用户昵称和邮箱
     */
    @PutMapping("/updateNicknameAndEmail")
    public Result<String> updateNicknameAndEmail(@RequestBody UserDto userDto) {
        sysUserService.updateNicknameAndEmail(userDto);
        return Result.success();
    }

    /**
     * 发送验证码
     */
    @GetMapping("/sendEmail")
    public Result<String> sendEmail(String email) {
        // 发送qq邮箱验证码
        sysUserService.sendVerificationEmail(email);
        return Result.success();
    }

    /**
     * 修改用户密码
     */
    @PutMapping("/updatePassword")
    public Result<String> updatePassword(@RequestBody UserDto userDto) {
        // 修改密码
        sysUserService.updatePassword(userDto);
        return Result.success();
    }

    /**
     * 查询总数
     */
    @GetMapping("/count")
    public Long countTotalUser() {
        return sysUserService.count();
    }

    /**
     * 查询用户数量
     */
    @GetMapping("/count/{date}")
    public Long countUser(@PathVariable String date) {
        return sysUserService.countUser(date);
    }

    @GetMapping("/countUserDay7")
    public List<TopicDataVo> countUserDay7() {
        return sysUserService.countUserDay7();
    }

    /**
     * h5端登录
     */
    @PostMapping("loginType")
    public Mono<Result<Map<String, Object>>> loginType(@RequestBody @Validated LoginTypeDto loginTypeDto) {
        return sysUserService.loginType(loginTypeDto)
                .map(Result::success)
                .defaultIfEmpty(Result.fail("登录失败"));
    }

    /**
     * h5端忘记密码
     */
    @PutMapping("/resetPassword")
    public Result<String> resetPassword(@RequestBody @Validated ResetPasswordDto resetPasswordDto) {
        sysUserService.resetPassword(resetPasswordDto);
        return Result.success();
    }

    /**
     * 注册账户
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody @Validated RegisterDto registerDto) {
        sysUserService.register(registerDto);
        return Result.success();
    }

}
