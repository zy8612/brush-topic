package com.ey.topic.topic.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ey.model.dto.topic.TopicLabelDto;
import com.ey.model.dto.topic.TopicLabelListDto;
import com.ey.model.entity.topic.TopicLabel;
import com.ey.model.excel.topic.TopicLabelExcel;
import com.ey.model.excel.topic.TopicLabelExcelExport;
import com.ey.model.vo.topic.TopicLabelVo;

import java.util.List;
import java.util.Map;

public interface TopicLabelService extends IService<TopicLabel> {
    List<TopicLabelVo> getAllLabel();

    Map<String, Object> labelList(TopicLabelListDto topicLabelListDto);

    void addLabel(TopicLabelDto topicLabelDto);

    void deleteLabel(Long[] ids);

    void updateLabel(TopicLabelDto topicLabelDto);

    List<TopicLabelExcelExport> getExcelVo(TopicLabelListDto topicLabelListDto, Long[] ids);

    String importExcel(List<TopicLabelExcel> excelVoList, Boolean updateSupport);

    List<String> getLabelNamesByIds(List<Long> labelIds);
}
