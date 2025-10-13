package com.ey.model.entity.topic;

import com.ey.model.entity.BaseEntity;
import lombok.Data;

@Data
public class Topic extends BaseEntity {
    private String topicName;
    private String answer;
    private String aiAnswer;
    private Integer sorted;
    private Integer isEveryday;
    private Integer isMember;
    private Long viewCount;
    private Integer status;
    private String createBy;
    private String failMsg;
}
