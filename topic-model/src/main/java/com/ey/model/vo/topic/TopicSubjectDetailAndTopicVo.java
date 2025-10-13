package com.ey.model.vo.topic;

import lombok.Data;

import java.util.List;

@Data
public class TopicSubjectDetailAndTopicVo {
    private String imageUrl;
    private String subjectName;
    private String subjectDesc;
    private Long topicCount;
    private Long viewCount;
    List<TopicNameVo> topicNameVos;

}
