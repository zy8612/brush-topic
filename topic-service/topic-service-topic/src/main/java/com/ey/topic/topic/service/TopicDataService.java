package com.ey.topic.topic.service;

import com.ey.model.vo.ai.AiTrendVo;
import com.ey.model.vo.system.SysUserTrentVo;
import com.ey.model.vo.topic.*;

import java.util.List;
import java.util.Map;

public interface TopicDataService {
    Map<String, Object> webHomeCount();

    Map<String, Object> adminHomeCount();

    List<TopicCategoryDataVo> adminHomeCategory();

    TopicTrendVo topicTrend();

    SysUserTrentVo userTrend();

    Map<String, Object> userHomeCount();

    List<TopicCategoryUserDataVo> userHomeCategory();

    List<TopicDataVo> userTopicDateCount(String date);

    AiTrendVo aiTrend();

    List<TopicUserRankVo> rank(Integer type);

    TopicUserRankVo userRank(Integer type);

    List<TopicTodayVo> topicTodayVo();

    void flushTopic(Long id);
}
