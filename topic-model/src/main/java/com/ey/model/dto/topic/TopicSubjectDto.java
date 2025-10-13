package com.ey.model.dto.topic;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TopicSubjectDto {
    @NotBlank(message = "题目专题名称不能为空")
    private String subjectName;
    private Long id;
    @NotBlank(message = "题目专题图片不能为空")
    private String imageUrl;
    @NotBlank(message = "题目专题描述不能为空")
    private String subjectDesc;
    @NotBlank(message = "题目专题分类不能为空")
    private String categoryName;
}
