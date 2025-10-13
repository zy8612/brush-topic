package com.ey.topic.ai.service;

import com.ey.model.dto.ai.AiHistoryDto;
import com.ey.model.dto.ai.ChatDto;
import com.ey.model.dto.ai.TtsDto;
import com.ey.model.dto.audit.TopicAudit;
import com.ey.model.dto.audit.TopicAuditCategory;
import com.ey.model.dto.audit.TopicAuditLabel;
import com.ey.model.dto.audit.TopicAuditSubject;
import com.ey.model.vo.ai.AiHistoryContent;
import com.ey.model.vo.ai.AiHistoryListVo;
import com.ey.model.vo.topic.TopicDataVo;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;

import java.util.List;

public interface AiModelService {
    Flux<String> chat(ChatDto chatDto);

    List<AiHistoryListVo> getHistory(AiHistoryDto aiHistoryDto);

    List<AiHistoryContent> getHistoryById(Long id);

    void deleteHistory(Long id);

    void updateHistoryById(AiHistoryDto aiHistoryDto);

    ResponseEntity<byte[]> tts(TtsDto ttsDto);

    void auditCategory(TopicAuditCategory topicAuditCategory);
    void auditSubject(TopicAuditSubject topicAuditSubject);
    void auditTopic(TopicAudit topicAudit);
    void auditLabel(TopicAuditLabel topicAuditLabel);

    void recordAuditLog(String content, String account, Long userId);

    Long countAi(Long currentId);

    Long count(String date);

    List<TopicDataVo> countAiDay7();

    void generateAnswer(TopicAudit topicAudit);
}
