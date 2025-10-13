package com.ey.topic.topic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ey.model.entity.topic.TopicRecord;
import com.ey.model.vo.topic.TopicDataVo;
import com.ey.model.vo.topic.TopicUserRankVo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

@Mapper
public interface TopicRecordMapper extends BaseMapper<TopicRecord> {

    // 根据日期查询刷题次数总和
    Long countTopicFrequency(String date);

    List<TopicDataVo> countTopicDay15();

    List<TopicDataVo> countUserDay15();

    // 查询用户总刷题次数
    Long countTopicUserRecord(Long currentId);

    Long getRank(Long userId);

    Long getDateRank(Long currentId, String date);

    // 统计用户最长连续刷题数量
    Long selectMaximumCount(Long currentId);

    // 查询用户最近刷题多少天
    Long selectRecentConsecutiveCount(Long currentId);

    // 查询用户已经刷过的题目数量
    @Select("SELECT COUNT(DISTINCT topic_id) FROM topic_record WHERE user_id = #{userId}")
    Long userTopicRecordCount(Long currentId);

    List<Long> getTopicRecordIds(List<Long> subjectIds, Long userId);

    List<TopicDataVo> userTopicDateCount(String startDate, String endDate, Long userId);

    List<TopicUserRankVo> getCountRank(String date);

    TopicUserRankVo getUserTodayRank(String date, Long userId);

    TopicUserRankVo getUserTotalRank(Long userId);

    @Select("SELECT DISTINCT topic_id from topic_record where user_id = 16")
    Set<Long> selectByUserId();

    @Delete("delete from topic_record where topic_id = #{id}")
    void deleteByTopicId(Long id);

    Long selectMaxSubject(Long id);
}
