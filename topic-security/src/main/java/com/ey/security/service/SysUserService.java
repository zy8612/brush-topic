package com.ey.security.service;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ey.client.system.SystemFeignClient;
import com.ey.common.constant.RedisConstant;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.exception.TopicException;
import com.ey.common.interceptor.TokenInterceptor;
import com.ey.common.utils.JWTUtils;
import com.ey.model.dto.system.SysUserDto;
import com.ey.model.dto.system.SysUserListDto;
import com.ey.model.entity.system.SysRole;
import com.ey.model.entity.system.SysUser;
import com.ey.model.entity.system.SysUserRole;
import com.ey.model.excel.sytem.SysUserExcel;
import com.ey.model.excel.sytem.SysUserExcelExport;
import com.ey.model.vo.system.SysMenuVo;
import com.ey.model.vo.system.SysUserListVo;
import com.ey.model.vo.system.UserInfoVo;
import com.ey.model.vo.topic.TopicDataVo;
import com.ey.security.constant.EmailConstant;
import com.ey.security.constant.JwtConstant;
import com.ey.security.dto.LoginTypeDto;
import com.ey.security.dto.RegisterDto;
import com.ey.security.dto.ResetPasswordDto;
import com.ey.security.dto.UserDto;
import com.ey.security.mapper.SysUserMapper;
import com.ey.security.mapper.SysUserRoleMapper;
import com.ey.security.properties.AuthProperties;
import com.ey.security.utils.DateUtils;
import com.ey.security.utils.EmailSendUtils;
import com.ey.security.utils.PasswordUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;


/**
 * Description: 系统用户接口层
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserService extends ServiceImpl<SysUserMapper, SysUser> {

    private final SysUserRoleMapper sysUserRoleMapper;
    private final SystemFeignClient systemFeignClient;
    private final SysUserMapper sysUserMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final AuthProperties ignoreWhiteProperties;
    private final EmailSendUtils emailSendUtils;

    /**
     * 根据账号查询用户信息
     *
     * @param account
     * @return
     */
    public SysUser findByUserName(String account) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getAccount, account);
        return getOne(queryWrapper);
    }

    /**
     * 解析token并获取用户信息
     *
     * @param token
     * @return
     */
    public UserInfoVo getUserInfo(String token) {
        if (StrUtil.isBlank(token)) {
            throw new TopicException(ResultCodeEnum.LOGIN_ERROR);
        }
        // 解析token
        Map<String, Object> tokenInfo = JWTUtils.getTokenInfo(token);
        // 校验
        if (CollectionUtil.isEmpty(tokenInfo)) {
            throw new TopicException(ResultCodeEnum.LOGIN_ERROR);
        }
        // 获取唯一用户名
        String username = tokenInfo.get("username").toString();
        // 校验
        if (StrUtil.isBlank(username)) {
            throw new TopicException(ResultCodeEnum.LOGIN_ERROR);
        }
        // 根据用户名获取用户信息
        SysUser sysUser = findByUserName(username);
        if (ObjectUtil.isEmpty(sysUser)) {
            throw new TopicException(ResultCodeEnum.ACCOUNT_ERROR);
        }
        // 封装返回信息
        UserInfoVo userInfoVo = new UserInfoVo();
        userInfoVo.setAccount(sysUser.getAccount());
        userInfoVo.setAvatar(sysUser.getAvatar());
        userInfoVo.setNickname(sysUser.getNickname());
        userInfoVo.setId(sysUser.getId());
        // 根据用户id查询用户与角色的关系表
        SysUserRole sysUserRole = sysUserRoleMapper.selectOne(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, sysUser.getId())
        );
        // 校验
        if (sysUserRole == null) {
            throw new TopicException(ResultCodeEnum.ACCOUNT_ERROR);
        }
        // 根据角色id查询角色相关信息
        SysRole sysRole = systemFeignClient.getSysRoleById(sysUserRole.getRoleId());
        // 校验
        if (sysRole == null) {
            throw new TopicException(ResultCodeEnum.ACCOUNT_ERROR);
        }
        // 设置用户权限
        userInfoVo.setIdentity(sysRole.getIdentify());
        // 设置token到ThreadLocal
        TokenInterceptor.setToken(token);
        // 调用系统管理服务 查询用户菜单权限
        List<SysMenuVo> sysMenus = systemFeignClient.userMenu(sysRole.getId());
        // 校验
        if (CollectionUtils.isEmpty(sysMenus)) {
            throw new TopicException(ResultCodeEnum.NO_MENU_FAIL);
        }
        userInfoVo.setMenuList(sysMenus);
        return userInfoVo;
    }

    /**
     * 获取用户列表
     *
     * @param sysUserListDto
     * @return
     */
    public Map<String, Object> userList(SysUserListDto sysUserListDto) {
        if (sysUserListDto.getPageNum() != null) {
            // 将前端传入的分页参数转换为后端数据库查询所需的偏移量
            sysUserListDto.setPageNum((sysUserListDto.getPageNum() - 1) * sysUserListDto.getPageSize());
        }
        List<SysUserListVo> list = sysUserMapper.selectUserList(sysUserListDto);
        //获取总记录数
        long total = sysUserMapper.countUserList(sysUserListDto);
        log.info("查询结果：{}", list);
        return Map.of(
                "total", total,
                "rows", list
        );
    }

    /**
     * 新增用户
     *
     * @param sysUserDto
     */
    @Transactional
    public void addUser(SysUserDto sysUserDto) {
        // 判断账户是否为空
        if (StrUtil.isBlank(sysUserDto.getAccount())) {
            throw new TopicException(ResultCodeEnum.PARAM_ACCOUNT_ERROR);
        }
        // 校验角色
        if (StrUtil.isBlank(sysUserDto.getRoleName())) {
            throw new TopicException(ResultCodeEnum.PARAM_ROLE_ERROR);
        }
        // 密码不能为空
        if (StrUtil.isBlank(sysUserDto.getPassword())) {
            throw new TopicException(ResultCodeEnum.PARAM_PASSWORD_ERROR);
        }
        // 如果用户存在
        if (ObjectUtil.isNotEmpty(findByUserName(sysUserDto.getAccount()))) {
            throw new TopicException(ResultCodeEnum.USER_ACCOUNT_EXIST);
        }
        SysUser sysUser = new SysUser();
        BeanUtils.copyProperties(sysUserDto, sysUser);
        // 加密密码
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        sysUser.setPassword(passwordEncoder.encode(sysUserDto.getPassword()));
        // 新增用户
        sysUserMapper.insert(sysUser);
        // 添加用户权限关系表
        SysUserRole sysUserRole = new SysUserRole();
        sysUserRole.setUserId(sysUser.getId());
        sysUserRole.setRoleId(sysUserDto.getRoleId());
        sysUserRoleMapper.insert(sysUserRole);
    }

    /**
     * 删除用户/批量删除用户
     *
     * @param ids
     */
    @Transactional
    public void deleteUsers(String[] ids) {
        // 删除用户
        sysUserMapper.deleteBatchIds(Arrays.asList(ids));
        // 删除权限关联表
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(SysUserRole::getUserId, Arrays.asList(ids));
        sysUserRoleMapper.delete(queryWrapper);
    }

    /**
     * 修改用户
     *
     * @param sysUserDto
     */
    @Transactional
    public void updateUser(SysUserDto sysUserDto) {
        // 校验账号和权限
        if (StrUtil.isBlank(sysUserDto.getAccount())) {
            throw new TopicException(ResultCodeEnum.PARAM_ACCOUNT_ERROR);
        }
        if (StrUtil.isBlank(sysUserDto.getRoleName())) {
            throw new TopicException(ResultCodeEnum.PARAM_ROLE_ERROR);
        }
        // 查询用户名是否存在
        SysUser user = findByUserName(sysUserDto.getAccount());
        if (ObjectUtil.isNotEmpty(user)) {
            // 是否是自身，不是则不能修改
            if (!user.getId().equals(sysUserDto.getId())) {
                throw new TopicException(ResultCodeEnum.USER_ACCOUNT_EXIST);
            }
        }
        // 修改用户
        SysUser sysUser = new SysUser();
        BeanUtils.copyProperties(sysUserDto, sysUser);
        sysUserMapper.updateById(sysUser);
        // 修改权限表信息
        SysUserRole sysUserRole = new SysUserRole();
        sysUserRole.setUserId(sysUser.getId());
        sysUserRole.setRoleId(sysUserDto.getRoleId());
        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUserRole::getUserId, sysUser.getId());
        sysUserRoleMapper.update(sysUserRole, queryWrapper);
    }

    /**
     * 判断该权限下是否存在用户
     *
     * @param roleId
     * @return
     */
    public Boolean getByRoleId(Long roleId) {
        return sysUserRoleMapper.selectExist(roleId);
    }

    /**
     * 获取excelVo数据
     *
     * @param sysUserListDto
     * @param ids
     * @return
     */
    public List<SysUserExcelExport> getExcelVo(SysUserListDto sysUserListDto, Long[] ids) {
        if (ids != null && ids.length > 0 && ids[0] != 0) {
            // 查询选择导出的数据
            List<SysUserListVo> exportUsers = sysUserMapper.selectExportUsers(Arrays.asList(ids));
            if (CollectionUtil.isNotEmpty(exportUsers)) {
                return exportUsers.stream()
                        .map(exportUser -> {
                            SysUserExcelExport sysUserExcelExport = new SysUserExcelExport();
                            BeanUtils.copyProperties(exportUser, sysUserExcelExport);
                            return sysUserExcelExport;
                        }).toList();
            }
        }
        // 如果没有选择
        List<SysUserListVo> exportUsers = sysUserMapper.selectUserList(sysUserListDto);
        if (CollectionUtil.isNotEmpty(exportUsers)) {
            return exportUsers.stream()
                    .map(exportUser -> {
                        SysUserExcelExport sysUserExcelExport = new SysUserExcelExport();
                        BeanUtils.copyProperties(exportUser, sysUserExcelExport);
                        return sysUserExcelExport;
                    }).toList();
        }
        return null;
    }

    /**
     * 导入excel数据
     *
     * @param excelVoList
     * @param updateSupport
     * @return
     */
    @Transactional
    public String importExcel(List<SysUserExcel> excelVoList, Boolean updateSupport) {
        // 校验数据
        if (ObjectUtil.isNull(excelVoList)) {
            throw new TopicException(ResultCodeEnum.IMPORT_EXCEL_ERROR);
        }
        // 导入成功数据量
        int successNum = 0;
        // 导入失败数据量
        int failureNum = 0;
        // 拼接成功消息
        StringBuilder successMsg = new StringBuilder();
        // 拼接错误消息
        StringBuilder failureMsg = new StringBuilder();

        for (SysUserExcel sysUserExcel : excelVoList) {
            try {
                // 根据用户名查询用户
                SysUser sysUser = findByUserName(sysUserExcel.getAccount());
                // 如果用户不存在，插入数据
                if (ObjectUtil.isNull(sysUser)) {
                    SysRole sysRole = sysUserMapper.getByRoleName(sysUserExcel.getRoleName());
                    if (ObjectUtil.isNull(sysRole)) {
                        // 角色不存在
                        failureNum++;
                        failureMsg.append("<br/>").append(failureNum).append("-用户：").append(sysUserExcel.getAccount()).append("-角色不存在");
                    } else {
                        // 存在则添加
                        SysUser user = new SysUser();
                        // 加密密码
                        sysUserExcel.setPassword(PasswordUtils.encodePassword(sysUserExcel.getPassword()));
                        BeanUtils.copyProperties(sysUserExcel, user);
                        sysUserMapper.insert(user);
                        // 插入角色到权限关系表中
                        SysUserRole sysUserRole = new SysUserRole();
                        sysUserRole.setUserId(user.getId());
                        sysUserRole.setRoleId(sysRole.getId());
                        sysUserRoleMapper.insert(sysUserRole);
                        successNum++;
                        successMsg.append("<br/>").append(successNum).append("-用户：").append(sysUserExcel.getAccount()).append("-导入成功");
                    }
                } else if (updateSupport) {
                    // 如果存在，且用户点击了更新已存在的数据
                    sysUserExcel.setPassword(PasswordUtils.encodePassword(sysUserExcel.getPassword()));
                    SysRole sysRole = sysUserMapper.getByRoleName(sysUserExcel.getRoleName());
                    if (ObjectUtil.isNull(sysRole)) {
                        // 角色不存在
                        failureNum++;
                        failureMsg.append("<br/>").append(failureNum).append("-用户：").append(sysUserExcel.getAccount()).append("-角色不存在");
                    } else {
                        // 修改
                        BeanUtils.copyProperties(sysUserExcel, sysUser);
                        sysUserMapper.updateById(sysUser);
                        SysUserRole sysUserRole = new SysUserRole();
                        LambdaQueryWrapper<SysUserRole> queryWrapper = new LambdaQueryWrapper<>();
                        queryWrapper.eq(SysUserRole::getUserId, sysUser.getId());
                        sysUserRole.setRoleId(sysRole.getId());
                        sysUserRoleMapper.update(sysUserRole, queryWrapper);
                        successNum++;
                        successMsg.append("<br/>").append(successNum).append("-用户：").append(sysUserExcel.getAccount()).append("-更新成功");
                    }
                } else {
                    // 用户已存在但不更新
                    failureNum++;
                    failureMsg.append("<br/>").append(failureNum).append("-用户：").append(sysUser.getAccount()).append("-已存在");
                }
            } catch (Exception e) {
                failureNum++;
                String msg = "<br/>" + failureNum + "-用户： " + sysUserExcel.getAccount() + " 导入失败：";
                failureMsg.append(msg).append(e.getMessage());
                log.error(msg, e);
            }
        }
        if (failureNum > 0) {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
            throw new TopicException(failureMsg.toString());
        } else {
            successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
        }
        return successMsg.toString();
    }

    /**
     * 保存头像
     *
     * @param userDto
     */
    public void saveAvatar(UserDto userDto) {
        if (userDto.getId() == null) {
            throw new TopicException(ResultCodeEnum.USER_NOT_EXIST);
        }
        SysUser sysUser = sysUserMapper.selectById(userDto.getId());
        // 如果用户不存在
        if (ObjectUtil.isNull(sysUser)) {
            throw new TopicException(ResultCodeEnum.USER_NOT_EXIST);
        }
        sysUser.setAvatar(userDto.getAvatar());
        sysUserMapper.updateById(sysUser);
    }

    /**
     * 修改用户昵称和邮箱
     *
     * @param userDto
     */
    public void updateNicknameAndEmail(UserDto userDto) {
        // 校验
        if (userDto.getId() == null) {
            throw new TopicException(ResultCodeEnum.USER_NOT_EXIST);
        }
        SysUser sysUserDb = sysUserMapper.selectById(userDto.getId());
        if (ObjectUtil.isNull(sysUserDb)) {
            throw new TopicException(ResultCodeEnum.USER_NOT_EXIST);
        }
        if (!userDto.getPassword().equals(sysUserDb.getPassword())) {
            if (!PasswordUtils.matches(userDto.getPassword(), sysUserDb.getPassword())) {
                throw new TopicException(ResultCodeEnum.USER_PASSWORD_ERROR);
            }
        }
        // 查询用户昵称是否存在了
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<SysUser>();
        queryWrapper.eq(SysUser::getNickname, userDto.getNickname());
        if (sysUserMapper.exists(queryWrapper)) {
            throw new TopicException(ResultCodeEnum.USER_NICKNAME_EXIST);
        }
        // 根据邮箱查询用户是否存在
        if (StrUtil.isNotEmpty(userDto.getEmail())) {
            LambdaQueryWrapper<SysUser> emailEq = new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, userDto.getEmail());
            if (sysUserMapper.exists(emailEq)) {
                throw new TopicException(ResultCodeEnum.USER_EMAIL_EXIST);
            }
            // 校验验证码
            String verificationCode = stringRedisTemplate.opsForValue().get(EmailConstant.EMAIL_CODE.getValue() + userDto.getEmail());
            if (StrUtil.isBlank(verificationCode)) {
                throw new TopicException(ResultCodeEnum.USER_EMAIL_CODE_ERROR);
            }
            if (!userDto.getCode().equals(verificationCode)) {
                throw new TopicException(ResultCodeEnum.USER_EMAIL_CODE_INPUT_ERROR);
            }
            sysUserDb.setEmail(userDto.getEmail());
        }
        sysUserDb.setNickname(userDto.getNickname());
        try {
            sysUserMapper.updateById(sysUserDb);
            stringRedisTemplate.delete(EmailConstant.EMAIL_CODE.getValue() + userDto.getEmail());
        } catch (Exception e) {
            throw new TopicException(ResultCodeEnum.USER_NICKNAME_EXIST);
        }
    }

    /**
     * 发送qq邮箱验证码
     */
    public void sendVerificationEmail(String email) {
        // 校验参数
        if (StrUtil.isEmpty(email)) {
            throw new TopicException(ResultCodeEnum.USER_EMAIL_NOT_EXIST);
        }
        // 先查询是否发送过验证码
        if (stringRedisTemplate.hasKey(EmailConstant.EMAIL_CODE.getValue() + ":" + email)) {
            throw new TopicException(ResultCodeEnum.USER_EMAIL_CODE_EXIST);
        }
        // 发送验证码
        try {
            emailSendUtils.sendVerificationEmail(email);
        } catch (Exception e) {
            throw new TopicException(ResultCodeEnum.USER_EMAIL_SEND_ERROR);
        }
    }

    public void updatePassword(UserDto userDto) {
        // 校验
        if (userDto.getId() == null) {
            throw new TopicException(ResultCodeEnum.USER_NOT_EXIST);
        }
        SysUser sysUserDb = sysUserMapper.selectById(userDto.getId());
        if (ObjectUtil.isNull(sysUserDb)) {
            throw new TopicException(ResultCodeEnum.USER_NOT_EXIST);
        }
        // 判断当前密码
        if (!PasswordUtils.matches(userDto.getPassword(), sysUserDb.getPassword())) {
            throw new TopicException(ResultCodeEnum.USER_PASSWORD_ERROR);
        }
        // 判断两次密码是否正确
        if (!userDto.getNewPassword().equals(userDto.getConfirmPassword())) {
            throw new TopicException(ResultCodeEnum.USER_PASSWORD_ERROR);
        }
        // 开始修改
        sysUserDb.setPassword(PasswordUtils.encodePassword(userDto.getNewPassword()));
        sysUserMapper.updateById(sysUserDb);
    }

    /**
     * 查询用户数量
     *
     * @param date
     * @return
     */
    public Long countUser(String date) {
        LocalDateTime start = DateUtils.parseStartOfDay(date);
        LocalDateTime end = DateUtils.parseEndOfDay(date);
        LambdaQueryWrapper<SysUser> userQueryWrapper = new LambdaQueryWrapper<>();
        userQueryWrapper.between(SysUser::getCreateTime, start, end);
        userQueryWrapper.eq(SysUser::getStatus, 0);
        return sysUserMapper.selectCount(userQueryWrapper);
    }

    /**
     * 获取7天内的新增用户数据
     *
     * @return
     */
    public List<TopicDataVo> countUserDay7() {
        return sysUserMapper.countUserDay7();
    }

    /**
     * h5端账户登录和邮箱登录
     *
     * @param loginTypeDto
     * @return
     */
    public Mono<Map<String, Object>> loginType(LoginTypeDto loginTypeDto) {
        Integer loginType = loginTypeDto.getLoginType();
        String account = loginTypeDto.getAccount();
        if (account == null) {
            throw new TopicException(ResultCodeEnum.FAIL);
        }
        String email = loginTypeDto.getEmail();
        if (email == null) {
            throw new TopicException(ResultCodeEnum.FAIL);
        }
        return switch (loginType) {
            case 0 -> accountLogin(loginTypeDto);
            case 1 -> emailLogin(loginTypeDto);
            default -> throw new TopicException(ResultCodeEnum.FAIL);
        };
    }
    // 邮箱登录
    private Mono<Map<String, Object>> emailLogin(LoginTypeDto loginTypeDto) {
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysUser::getEmail, loginTypeDto.getEmail());
        SysUser sysUser = sysUserMapper.selectOne(queryWrapper);
        if (ObjectUtil.isNull(sysUser)) {
            throw new TopicException(ResultCodeEnum.USER_NOT_EXIST);
        }
        if (!PasswordUtils.matches(loginTypeDto.getPassword(), sysUser.getPassword())) {
            throw new TopicException(ResultCodeEnum.USER_PASSWORD_ERROR);
        }
        if (sysUser.getStatus() == 1) {
            throw new TopicException(ResultCodeEnum.USER_ACCOUNT_STOP);
        }
        return getRoleIdentify(sysUser)
                .map(sysRole -> {
                    String token = createToken(sysUser, sysRole);
                    TokenInterceptor.setToken(token);
                    return Map.of(
                            "token", token,
                            "userInfo", JSON.toJSONString(sysUser),
                            "role", sysRole.getIdentify()
                    );
                });
    }
    // 账号登录
    private Mono<Map<String, Object>> accountLogin(LoginTypeDto loginTypeDto) {
        // 根据账户查询
        SysUser sysUser = findByUserName(loginTypeDto.getAccount());
        if (sysUser == null) {
            throw new TopicException(ResultCodeEnum.USER_NOT_EXIST);
        }
        // 密码校验
        if (!PasswordUtils.matches(loginTypeDto.getPassword(), sysUser.getPassword())) {
            throw new TopicException(ResultCodeEnum.USER_PASSWORD_ERROR);
        }
        // 校验用户状态是否被停用了
        if (sysUser.getStatus() == 1) {
            throw new TopicException(ResultCodeEnum.USER_ACCOUNT_STOP);
        }
        return getRoleIdentify(sysUser)
                .map(sysRole -> {
                    String token = createToken(sysUser, sysRole);
                    TokenInterceptor.setToken(token);
                    return Map.of(
                            "token", token,
                            "userInfo", JSON.toJSONString(sysUser),
                            "role", sysRole.getIdentify()
                    );
                });
    }

    /**
     * 查询用户角色标识
     *
     * @param sysUser
     * @return
     */
    private Mono<SysRole> getRoleIdentify(SysUser sysUser) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<SysUserRole> sysUserRoleLambdaQueryWrapper = new LambdaQueryWrapper<>();
                    sysUserRoleLambdaQueryWrapper.eq(SysUserRole::getUserId, sysUser.getId());
                    return sysUserRoleMapper.selectOne(sysUserRoleLambdaQueryWrapper);
                })
                .publishOn(Schedulers.boundedElastic()) // 在弹性线程池中执行
                .filter(Objects::nonNull)
                .switchIfEmpty(Mono.error(new TopicException(ResultCodeEnum.USER_NOT_EXIST)))
                .flatMap(sysUserRole ->
                        Mono.fromCallable(() -> systemFeignClient.getSysRoleById(sysUserRole.getRoleId()))
                                .publishOn(Schedulers.boundedElastic())
                                .filter(Objects::nonNull)
                                .switchIfEmpty(Mono.error(new TopicException(ResultCodeEnum.USER_NOT_EXIST)))
                );
    }

    // 生成token
    private String createToken(SysUser sysUser, SysRole sysRole) {
        Map<String, String> payload = new HashMap<>();
        payload.put("username", sysUser.getAccount());
        payload.put("userId", String.valueOf(sysUser.getId()));
        payload.put("role", sysRole.getRoleKey());
        String token = JWTUtils.creatToken(payload, JwtConstant.EXPIRE_TIME * ignoreWhiteProperties.getH5Timeout());
        // 存入redis中
        stringRedisTemplate.opsForValue().set(RedisConstant.USER_LOGIN + sysUser.getAccount(), token, ignoreWhiteProperties.getH5Timeout(), TimeUnit.DAYS);
        return token;
    }

    /**
     * 重置密码
     * @param resetPasswordDto
     */
    public void resetPassword(ResetPasswordDto resetPasswordDto) {
        // 根据邮箱查询
        LambdaQueryWrapper<SysUser> sysUserLambdaQueryWrapper = new LambdaQueryWrapper<>();
        sysUserLambdaQueryWrapper.eq(SysUser::getEmail, resetPasswordDto.getEmail());
        SysUser sysUser = sysUserMapper.selectOne(sysUserLambdaQueryWrapper);
        if (sysUser == null) {
            throw new TopicException(ResultCodeEnum.USER_NOT_EXIST);
        }
        // 用户存在校验验证码
        String emailCode = stringRedisTemplate.opsForValue().get(EmailConstant.EMAIL_CODE.getValue() + resetPasswordDto.getEmail());
        if (emailCode == null) {
            throw new TopicException(ResultCodeEnum.USER_EMAIL_CODE_ERROR);
        }
        // 开始比较
        if (!emailCode.equals(resetPasswordDto.getCode())) {
            throw new TopicException(ResultCodeEnum.USER_EMAIL_CODE_INPUT_ERROR);
        }
        // 判断两次密码是否一致
        if (!resetPasswordDto.getNewPassword().equals(resetPasswordDto.getPassword())) {
            throw new TopicException(ResultCodeEnum.USER_PASSWORD_ERROR);
        }
        sysUser.setPassword(PasswordUtils.encodePassword(resetPasswordDto.getNewPassword()));
        sysUserMapper.updateById(sysUser);
    }

    /**
     * 注册用户
     * @param registerDto
     */
    public void register(RegisterDto registerDto) {
        String email = registerDto.getEmail();
        // 根据邮箱查询是否存在
        LambdaQueryWrapper<SysUser> sysUserLambdaQueryWrapper = new LambdaQueryWrapper<>();
        sysUserLambdaQueryWrapper.eq(SysUser::getEmail, email);
        if (sysUserMapper.exists(sysUserLambdaQueryWrapper)) {
            throw new TopicException(ResultCodeEnum.USER_EMAIL_EXIST);
        }
        String account = registerDto.getAccount();
        // 根据账户查询是否已存在
        LambdaQueryWrapper<SysUser> userLambdaQueryWrapper = new LambdaQueryWrapper<>();
        userLambdaQueryWrapper.eq(SysUser::getAccount, account);
        if (sysUserMapper.exists(userLambdaQueryWrapper)) {
            throw new TopicException(ResultCodeEnum.USER_ACCOUNT_EXIST);
        }
        // 判断昵称是否存在
        if (!StrUtil.isEmpty(registerDto.getNickname())) {
            // 存在那根据昵称查询
            LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SysUser::getNickname, registerDto.getNickname());
            if (sysUserMapper.exists(queryWrapper)) {
                throw new TopicException(ResultCodeEnum.USER_NICKNAME_EXIST);
            }
        }
        // 校验验证码
        String emailCode = stringRedisTemplate.opsForValue().get(EmailConstant.EMAIL_CODE.getValue() + ":" + email);
        if (emailCode == null) {
            throw new TopicException(ResultCodeEnum.USER_EMAIL_CODE_ERROR);
        }
        if (!emailCode.equals(registerDto.getCode())) {
            throw new TopicException(ResultCodeEnum.USER_EMAIL_CODE_INPUT_ERROR);
        }
        // 加密密码
        String password = PasswordUtils.encodePassword(registerDto.getPassword());
        // 开始注册
        SysUser sysUserDb = new SysUser();
        sysUserDb.setAccount(account);
        sysUserDb.setEmail(email);
        sysUserDb.setPassword(password);
        sysUserDb.setNickname(registerDto.getNickname());
        sysUserMapper.insert(sysUserDb);

        // 插入到用户角色关系表中默认是用户
        SysUserRole sysUserRole = new SysUserRole();
        sysUserRole.setUserId(sysUserDb.getId());
        sysUserRole.setRoleId(2L);
        sysUserRoleMapper.insert(sysUserRole);
    }
}
