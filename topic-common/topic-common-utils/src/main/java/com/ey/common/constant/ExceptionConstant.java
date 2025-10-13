package com.ey.common.constant;

//Description: 用户异常常量
public class ExceptionConstant {
    public static final String USER_NOT_EXIST = "用户不存在";
    public static final String USER_BEEN_DISABLED = "用户被禁用";
    public static final String USER_LACK_ROLE = "用户无角色";
    public static final String ROLE_NOT_EXIST = "角色不存在";
    public static final String USER_NOT_ALLOW = "普通用户不能访问";
    public static final String CODE_ERROR = "验证码错误";
    // 认证失败的状态码
    public static final Integer AUTH_ERROR_CODE = 400;
}
