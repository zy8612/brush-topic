package com.ey.topic.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ey.model.entity.ai.AiRecord;
import com.ey.model.vo.topic.TopicDataVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiRecordMapper extends BaseMapper<AiRecord> {

    @Select("SELECT sum(count) FROM topic_record WHERE user_id = #{currentId}")
    Long countAi(Long currentId);

    Long countAiFrequency(String date);

    List<TopicDataVo> countAiDay7(String startDate, String endDate);
}
