package com.ey.model.dto.topic;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class TopicDto implements Serializable {
    private Long id;
    @NotBlank(message = "题目名称不能为空")
    private String topicName;
    @NotBlank(message = "题目答案不能为空")
    private String answer;
    private Integer sorted;
    private Integer isEveryday;
    private Integer isMember;
    @NotBlank(message = "题目关联专题不能为空")
    private Long subjectId;
    @NotBlank(message = "题目关联标签不能为空")
    private List<Long> labelIds;
}
