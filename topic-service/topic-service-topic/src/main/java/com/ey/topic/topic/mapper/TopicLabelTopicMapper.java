package com.ey.topic.topic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ey.model.entity.topic.TopicLabelTopic;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TopicLabelTopicMapper extends BaseMapper<TopicLabelTopic> {
    void insertBatch(List<TopicLabelTopic> topicLabelTopics);
}
