package com.ey.model.vo.topic;

import com.ey.model.entity.BaseEntity;
import lombok.Data;

import java.util.List;

@Data
public class TopicVo extends BaseEntity {

    private String topicName;
    private String answer;
    private String aiAnswer;
    private String createBy;
    private Integer sorted;
    private Integer isEveryday;
    private Integer status;
    private Integer isMember;
    private Long viewCount;
    // 专题
    private String subject;
    // 标签
    private List<String> labels;
    private String failMsg;
}
