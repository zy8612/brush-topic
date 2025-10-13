package com.ey.model.entity.topic;

import com.baomidou.mybatisplus.annotation.TableField;
import com.ey.model.entity.BaseEntity;
import lombok.Data;

@Data
public class TopicSubject extends BaseEntity {
    private String subjectName;
    private String subjectDesc;
    private String imageUrl;
    private Long topicCount;
    private String createBy;
    private Long viewCount;
    private Integer status;
    @TableField(exist = false)
    private String categoryName;
    private String failMsg;
}
