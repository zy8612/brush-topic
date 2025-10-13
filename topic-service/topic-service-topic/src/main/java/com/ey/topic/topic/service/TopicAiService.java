package com.ey.topic.topic.service;

import com.ey.model.entity.topic.Topic;
import com.ey.model.vo.topic.TopicSubjectVo;

import java.util.List;

public interface TopicAiService {
    List<Topic> getSubjectIdByTopicList(Long subjectId);

    List<TopicSubjectVo> getSubject(String role, String createBy);
}
