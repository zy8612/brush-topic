package com.ey.topic.topic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ey.model.entity.topic.TopicDailyStaging;
import org.apache.ibatis.annotations.Mapper;

/**
 * Description: 用户每日刷题暂存表
 */
@Mapper
public interface TopicDailyStagingMapper extends BaseMapper<TopicDailyStaging> {
}
