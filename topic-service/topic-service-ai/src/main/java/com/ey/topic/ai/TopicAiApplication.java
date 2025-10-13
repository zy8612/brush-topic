package com.ey.topic.ai;

import com.ey.common.config.MyMetaObjectHandler;
import com.ey.service.utils.config.MybatisPlusConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Description:
 * Author: Hao
 * Date: 2025/4/18 22:21
 */
@EnableDiscoveryClient
@SpringBootApplication
@ComponentScans({
        @ComponentScan("com.ey.common.security")
        , @ComponentScan("com.ey.common.handler"),
})
@Import({MyMetaObjectHandler.class, MybatisPlusConfig.class})  // 直接导入配置类
@ComponentScan({"com.ey.common.interceptor"})
@EnableFeignClients(basePackages = {"com.ey.client.system", "com.ey.client.topic"})
@EnableAsync
public class TopicAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TopicAiApplication.class, args);
        System.out.println("  _   _             \n" +
                " | | | | __ _  ___  \n" +
                " | |_| |/ _` |/ _ \\ \n" +
                " |  _  | (_| | (_) |\n" +
                " |_| |_|\\__,_|\\___/ \n" +
                " >>> AI服务启动成功 <<<\n");
    }
}
