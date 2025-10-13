package com.ey.topic.topic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ey.model.entity.topic.TopicDailyBrush;
import org.apache.ibatis.annotations.Mapper;

/**
 * Description: 每日刷题用户已刷的题目记录
 */
@Mapper
public interface TopicDailyBrushMapper extends BaseMapper<TopicDailyBrush> {
}
