package com.ey.web.gateway.properties;

import cn.hutool.core.util.StrUtil;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Description: 读取配置白名单
 */
@Configuration
@ConfigurationProperties(prefix = "security.ignore")
public class IgnoreWhiteProperties {
    /**
     * 放行白名单配置，网关不校验此处的白名单
     */
    private List<String> whites = new ArrayList<>();

    public List<String> getWhites() {
        return whites;
    }

    public void setWhites(List<String> whites) {
        this.whites = whites;
    }

    private static final AntPathMatcher matcher = new AntPathMatcher();

    /**
     * 查找是否是白名单的路径
     * @param url  请求路径
     * @return 是否匹配
     */
    public boolean isWhites(String url) {
        if (StrUtil.isBlank(url)) {
            return false;
        }
        // 使用成员变量whites而不是静态方法参数
        for (String pattern : this.whites) {
            if (isMatch(pattern, url)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断url是否与规则配置:
     * ? 表示单个字符;
     * * 表示一层路径内的任意字符串，不可跨层级;
     * ** 表示任意层路径;
     * @param pattern 匹配规则
     * @param url     需要匹配的url
     * @return 是否匹配
     */
    public static boolean isMatch(String pattern, String url) {
        // 优化：使用静态的AntPathMatcher实例而不是每次创建新实例
        return matcher.match(pattern, url);
    }
}
