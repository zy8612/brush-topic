package com.ey.model.dto.audit;

import lombok.Data;

import java.io.Serializable;

@Data
public class TopicAuditLabel implements Serializable {
    private String labelName;
    private Long id;
    private String account;
    private Long userId;
}
