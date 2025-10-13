package com.ey.topic.topic.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ey.model.dto.topic.TopicDto;
import com.ey.model.dto.topic.TopicListDto;
import com.ey.model.dto.topic.TopicRecordCountDto;
import com.ey.model.entity.topic.Topic;
import com.ey.model.excel.topic.TopicExcel;
import com.ey.model.excel.topic.TopicExcelExport;
import com.ey.model.excel.topic.TopicMemberExcel;
import com.ey.model.vo.topic.TopicAnswerVo;
import com.ey.model.vo.topic.TopicCollectionVo;
import com.ey.model.vo.topic.TopicDetailVo;

import java.util.List;
import java.util.Map;

public interface TopicService extends IService<Topic> {

    Map<String, Object> topicList(TopicListDto topicListDto);

    void addTopic(TopicDto topicDto);

    void deleteTopic(Long[] ids);

    List<TopicExcelExport> getExcelVo(TopicListDto topicListDto, Long[] ids);

    String memberImport(List<TopicMemberExcel> excelVoList, Boolean updateSupport);

    String adminImport(List<TopicExcel> excelVoList, Boolean updateSupport);

    TopicDetailVo detail(Long id);

    void setCount(TopicRecordCountDto topicRecordCountDto);

    TopicAnswerVo getAnswer(Long id);

    void collection(Long id);

    List<TopicCollectionVo> collectionList();

    void updateTopic(TopicDto topicDto);

    void generateAnswer(Long id);

    void updateAiAnswer(Topic topic);
}
