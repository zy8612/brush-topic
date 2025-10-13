package com.ey.topic.topic.controller;

import com.ey.model.entity.topic.Topic;
import com.ey.model.vo.topic.TopicSubjectVo;
import com.ey.topic.topic.service.TopicAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Description: ai查询题库
 */
@RestController
@RequestMapping("/topic/ai")
@RequiredArgsConstructor
public class TopicAiController {
    private final TopicAiService topicAiService;

    /**
     * 根据专题id查询该专题下所有的题目
     */
    @GetMapping("/topicList/{subjectId}")
    List<Topic> getSubjectTopicList(@PathVariable Long subjectId) {
        return topicAiService.getSubjectIdByTopicList(subjectId);
    }

    /**
     * 查询全部专题或者会员专题
     */
    @GetMapping("/subject/{role}/{createBy}")
    public List<TopicSubjectVo> aiSubject(@PathVariable String role, @PathVariable String createBy) {
        return topicAiService.getSubject(role, createBy);
    }
}
