package com.ey.common.utils;

import com.alibaba.fastjson2.JSON;
import com.ey.common.result.Result;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

/**
 * Description: 客户端工具类
 */
public class ServletUtils {

    /**
     * 设置webflux模型响应
     * @param response ServerHttpResponse
     * @param status   http状态码
     * @param code     响应状态码
     * @param value    响应内容
     * @return Mono<Void>
     */
    public static Mono<Void> webFluxResponseWriter(ServerHttpResponse response, HttpStatus status, Object value, int code) {
        return webFluxResponseWriter(response, MediaType.APPLICATION_JSON_VALUE, status, value, code);
    }
    /**
     * 设置webflux模型响应
     * @param response    ServerHttpResponse
     * @param contentType content-type
     * @param status      http状态码
     * @param code        响应状态码
     * @param value       响应内容
     * @return Mono<Void>
     */
    // 基础方法，其他重载方法最终调用此方法
    public static Mono<Void> webFluxResponseWriter(
            ServerHttpResponse response,
            String contentType,
            HttpStatus status,
            Object value,
            int code) {
        // 设置HTTP状态码
        response.setStatusCode(status);
        // 设置响应头
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, contentType);

        // 封装业务数据到Result对象
        Result<?> result = Result.fail(value.toString(), code);
        // 将Result对象序列化为JSON字节数组
        DataBuffer dataBuffer = response.bufferFactory().wrap(
                JSON.toJSONString(result).getBytes()
        );

        // 返回响应流
        return response.writeWith(Mono.just(dataBuffer));
    }

    /**
     * 设置webflux模型响应
     * @param response ServerHttpResponse
     * @param value    响应内容
     * @return Mono<Void>
     */
    public static Mono<Void> webFluxResponseWriter(ServerHttpResponse response, Object value) {
        return webFluxResponseWriter(response,
                HttpStatus.OK,
                value,
                Result.fail().getCode());
    }

    /**
     * 设置webflux模型响应
     * @param response ServerHttpResponse
     * @param code     响应状态码
     * @param value    响应内容
     * @return Mono<Void>
     */
    public static Mono<Void> webFluxResponseWriter(ServerHttpResponse response, Object value, int code) {
        return webFluxResponseWriter(response, HttpStatus.OK, value, code);
    }
}

