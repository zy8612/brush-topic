package com.ey.topic.topic;

import com.ey.common.config.MyMetaObjectHandler;
import com.ey.service.utils.config.MybatisPlusConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;
import org.springframework.context.annotation.Import;

/**
 * Description: 题目管理服务
 */
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScans({
        @ComponentScan("com.ey.common.security")
        , @ComponentScan("com.ey.common.handler"),
})
@Import({MyMetaObjectHandler.class, MybatisPlusConfig.class})  // 直接导入配置类
@ComponentScan({"com.ey.service.utils", "com.ey.common.interceptor"})
@EnableFeignClients(basePackages = {"com.ey.client.security","com.ey.client.ai"})
public class TopicTopicApplication {
    public static void main(String[] args) {
        SpringApplication.run(TopicTopicApplication.class, args);
        System.out.println("  _   _             \n" +
                " | | | | __ _  ___  \n" +
                " | |_| |/ _` |/ _ \\ \n" +
                " |  _  | (_| | (_) |\n" +
                " |_| |_|\\__,_|\\___/ \n" +
                " >>> 题目管理服务启动成功 <<<\n");
    }
}
