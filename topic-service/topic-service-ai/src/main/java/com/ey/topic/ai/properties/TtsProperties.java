package com.ey.topic.ai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Description: 语音合成配置
 * Author: Hao
 * Date: 2025/4/22 21:26
 */
@ConfigurationProperties(prefix = "tts")
@Configuration
@Data
public class TtsProperties {
    private String apiKey;
    private String model;
    private String voice;
}
