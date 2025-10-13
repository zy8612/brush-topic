package com.ey.topic.topic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ey.model.entity.topic.TopicLabel;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TopicLabelMapper extends BaseMapper<TopicLabel> {

    List<String> getLabelNamesByIds(List<Long> labelIds);
}
