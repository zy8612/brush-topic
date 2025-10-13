package com.ey.security;

import com.ey.common.config.FeignConfig;
import com.ey.common.config.MyMetaObjectHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Description: 认证服务
 */
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {
        "com.ey.common.handler",  // 添加这一行，扫描通用模块的异常处理器
})
@EnableFeignClients(basePackages = {"com.ey.client.system"})
@Import({FeignConfig.class, MyMetaObjectHandler.class})
public class TopicSecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(TopicSecurityApplication.class, args);
        System.out.println("  _   _             \n" +
                " | | | | __ _  ___  \n" +
                " | |_| |/ _` |/ _ \\ \n" +
                " |  _  | (_| | (_) |\n" +
                " |_| |_|\\__,_|\\___/ \n" +
                " >>> 认证服务启动成功 <<<\n");
    }
}
