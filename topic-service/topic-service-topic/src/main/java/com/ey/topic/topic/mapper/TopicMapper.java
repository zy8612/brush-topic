package com.ey.topic.topic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ey.model.entity.topic.Topic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

@Mapper
public interface TopicMapper extends BaseMapper<Topic> {
    @Select("SELECT id from topic")
    Set<Long> get();
}
