package com.ey.topic.order;

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
 * Description: 系统管理服务
 */
@EnableDiscoveryClient
@SpringBootApplication
@ComponentScans({
        @ComponentScan("com.ey.common.security")
        , @ComponentScan("com.ey.common.handler")
        , @ComponentScan("com.ey.service.utils")
})
@Import({MyMetaObjectHandler.class, MybatisPlusConfig.class})  // 直接导入配置类
@EnableFeignClients(basePackages = {"com.ey.client.system", "com.ey.client.security","com.ey.service.utils", "com.ey.common.interceptor"})
public class TopicOrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(TopicOrderApplication.class, args);
        System.out.println("  _   _             \n" +
                " | | | | __ _  ___  \n" +
                " | |_| |/ _` |/ _ \\ \n" +
                " |  _  | (_| | (_) |\n" +
                " |_| |_|\\__,_|\\___/ \n" +
                " >>> 订单服务启动成功 <<<\n");
    }
}
