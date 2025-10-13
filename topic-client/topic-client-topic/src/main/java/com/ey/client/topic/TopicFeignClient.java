package com.ey.client.topic;

import com.ey.common.interceptor.TokenInterceptor;
import com.ey.model.entity.topic.Topic;
import com.ey.model.entity.topic.TopicCategory;
import com.ey.model.entity.topic.TopicLabel;
import com.ey.model.entity.topic.TopicSubject;
import com.ey.model.vo.topic.TopicSubjectVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "service-topic", configuration = TokenInterceptor.class)
public interface TopicFeignClient {

    /**
     * 审核分类名称
     */
    @PutMapping("/topic/category/audit")
    void auditCategory(@RequestBody TopicCategory topicCategory);

    /**
     * 审核专题
     */
    @PutMapping("/topic/subject/audit")
    void auditSubject(@RequestBody TopicSubject topicSubject);

    /**
     * 审核标签名称
     */
    @PutMapping("/topic/label/audit")
    void auditLabel(@RequestBody TopicLabel topicLabel);

    /**
     * 审核题目
     */
    @PutMapping("/topic/topic/audit")
    void auditTopic(@RequestBody Topic topic);

    /**
     * 修改答案
     */
    @PutMapping("/topic/topic/answer")
    void updateAiAnswer(@RequestBody Topic topic);


    /**
     * ai查询全部专题或者会员专题
     */
    @GetMapping("/topic/ai/subject/{role}/{createBy}")
    List<TopicSubjectVo> getSubject(@PathVariable String role, @PathVariable String createBy);

    /**
     * ai查询专题下的所有题目
     */
    @GetMapping("/topic/ai/topicList/{subjectId}")
    List<Topic> getSubjectTopicList(@PathVariable Long subjectId);
}
