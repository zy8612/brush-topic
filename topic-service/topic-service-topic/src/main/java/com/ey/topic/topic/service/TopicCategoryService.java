package com.ey.topic.topic.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ey.model.dto.topic.TopicCategoryDto;
import com.ey.model.dto.topic.TopicCategoryListDto;
import com.ey.model.entity.topic.TopicCategory;
import com.ey.model.excel.topic.TopicCategoryExcel;
import com.ey.model.excel.topic.TopicCategoryExcelExport;
import com.ey.model.vo.topic.TopicCategoryVo;

import java.util.List;
import java.util.Map;

public interface TopicCategoryService extends IService<TopicCategory> {

    Map<String, Object> categoryList(TopicCategoryListDto topicCategoryDto);

    void addCategory(TopicCategoryDto topicCategoryDto);

    void deleteCategory(Long[] ids);

    void updateCategory(TopicCategoryDto topicCategoryDto);

    List<TopicCategoryExcelExport> getExcelVo(TopicCategoryListDto topicCategoryListDto, Long[] ids);

    String importExcel(List<TopicCategoryExcel> excelVoList, Boolean updateSupport);

    List<TopicCategoryVo> category(Boolean isCustomQuestion);

    void auditCategory(TopicCategory topicCategory);
}
