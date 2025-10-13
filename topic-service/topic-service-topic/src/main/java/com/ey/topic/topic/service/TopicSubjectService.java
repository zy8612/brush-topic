package com.ey.topic.topic.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ey.model.dto.topic.TopicSubjectDto;
import com.ey.model.dto.topic.TopicSubjectListDto;
import com.ey.model.entity.topic.TopicSubject;
import com.ey.model.excel.topic.TopicSubjectExcel;
import com.ey.model.excel.topic.TopicSubjectExcelExport;
import com.ey.model.vo.system.TopicSubjectWebVo;
import com.ey.model.vo.topic.TopicSubjectDetailAndTopicVo;
import com.ey.model.vo.topic.TopicSubjectVo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TopicSubjectService extends IService<TopicSubject> {
    List<TopicSubjectVo> getAllSubject();

    Map<String, Object> subjectList(TopicSubjectListDto topicSubjectListDto);

    void addSubject(TopicSubjectDto topicCategoryDto);

    void deleteSubject(Long[] ids);

    void updateSubject(TopicSubjectDto topicSubjectDto);

    List<TopicSubjectExcelExport> getExcelVo(TopicSubjectListDto topicSubjectListDto, Long[] ids);

    String importExcel(List<TopicSubjectExcel> excelVoList, Boolean updateSupport);

    void auditSubject(TopicSubject topicSubject);

    Set<Long> getTopicIdsBySubjectIds(List<Long> subjectIds);

    TopicSubjectDetailAndTopicVo subjectDetail(Long id);

    List<TopicSubjectWebVo> subject(Long categoryId);
}
