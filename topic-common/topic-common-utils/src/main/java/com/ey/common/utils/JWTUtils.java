package com.ey.common.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 */
public class JWTUtils {

    private static final String SECRET_KEY = "350419ey";

    //生成jwt
    public static String creatToken(Map<String, String> payload, int expireTime) {
        JWTCreator.Builder builder = JWT.create();
        Calendar instance = Calendar.getInstance();// 获取日历对象
        if (expireTime <= 0)
            instance.add(Calendar.SECOND, 3600);// 默认一小时
        else
            instance.add(Calendar.SECOND, expireTime);
        // 将数据添加进jwt
        payload.forEach(builder::withClaim);
        return builder.withExpiresAt(instance.getTime())
                .sign(Algorithm.HMAC256(SECRET_KEY));
    }

    //解析jwt
    public static Map<String, Object> getTokenInfo(String token) {
        try {
            DecodedJWT verify = JWT.require(Algorithm.HMAC256(SECRET_KEY)).build().verify(token);
            Map<String, Claim> claims = verify.getClaims();
            SimpleDateFormat dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String expired = dateTime.format(verify.getExpiresAt());
            Map<String, Object> map = new HashMap<>();
            claims.forEach((key, value) -> map.put(key, value.asString()));
            map.put("exp", expired);
            return map;
        } catch (Exception e) {
            throw new RuntimeException("无效的Token，请重新登录");
        }
    }

    //校验jwt
    public static boolean verifyToken(String token) {
        try {
            JWT.require(Algorithm.HMAC256(SECRET_KEY)).build().verify(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

