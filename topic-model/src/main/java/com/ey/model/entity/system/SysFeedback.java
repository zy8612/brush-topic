package com.ey.model.entity.system;

import com.ey.model.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysFeedback extends BaseEntity {

    private String account;
    private Long userId;
    private String feedbackContent;
    private String replyContent;
    private Integer status;
    private String replyAccount;
    private Long replyId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime replyTime;
}
