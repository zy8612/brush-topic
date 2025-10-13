package com.ey.model.dto.topic;

import lombok.Data;

@Data
public class TopicRecordCountDto {
    private Long subjectId;
    private Long topicId;
    private String nickname;
    private String avatar;
}
