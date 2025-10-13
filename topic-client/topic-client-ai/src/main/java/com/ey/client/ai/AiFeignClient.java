package com.ey.client.ai;

import com.ey.common.interceptor.TokenInterceptor;
import com.ey.model.vo.topic.TopicDataVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "service-ai", configuration = TokenInterceptor.class)
public interface AiFeignClient {

    /**
     * 根据日期查询ai使用总数
     */
    @GetMapping("/ai/model/count/{date}")
    Long countDate(@PathVariable String date);

    /**
     * 查询ai使用总数
     *
     * @return
     */
    @GetMapping("/ai/model/count")
    Long count();


    @GetMapping("/ai/model/countAiDay7")
    List<TopicDataVo> countAiDay7();

    @GetMapping("/ai/model/countAi/{currentId}")
    Long countAi(@PathVariable Long currentId);

}
