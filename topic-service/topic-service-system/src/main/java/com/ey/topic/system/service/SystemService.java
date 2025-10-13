package com.ey.topic.system.service;

import com.ey.model.entity.system.WebConfig;
import jakarta.servlet.http.HttpServletResponse;

public interface SystemService {
    void getCode(HttpServletResponse response);

    WebConfig getConfig(Integer status);
}
