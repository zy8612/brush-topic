package com.ey.model.entity.topic;

import com.ey.model.entity.BaseEntity;
import lombok.Data;

@Data
public class TopicLabel  extends BaseEntity {
    private String labelName;
    private Integer status;
    private String createBy;
    private Long useCount;
    private String failMsg;
}
