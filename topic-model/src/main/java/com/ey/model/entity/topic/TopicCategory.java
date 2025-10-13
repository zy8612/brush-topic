package com.ey.model.entity.topic;


import com.ey.model.entity.BaseEntity;
import lombok.Data;

@Data
public class TopicCategory extends BaseEntity {
    private String categoryName;
    private Integer status;
    private String createBy;
    private String failMsg;
    private Long subjectCount;
}
