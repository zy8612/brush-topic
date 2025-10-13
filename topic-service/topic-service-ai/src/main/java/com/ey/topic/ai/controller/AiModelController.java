package com.ey.topic.ai.controller;

import com.ey.common.result.Result;
import com.ey.model.dto.ai.AiHistoryDto;
import com.ey.model.dto.ai.ChatDto;
import com.ey.model.dto.ai.TtsDto;
import com.ey.model.vo.ai.AiHistoryContent;
import com.ey.model.vo.ai.AiHistoryListVo;
import com.ey.model.vo.topic.TopicDataVo;
import com.ey.topic.ai.service.AiModelService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Description: Ai模型控制器
 */
@RestController
@RequestMapping("/ai/model")
@AllArgsConstructor
public class AiModelController {

    private final AiModelService aiModelService;

    /**
     * 流式对话
     */
    @PostMapping("/chat")
    public Flux<String> chat(@RequestBody ChatDto chatDto) {
        return aiModelService.chat(chatDto);
    }

    /**
     * 语音合成
     */
    @PostMapping("tts")
    public ResponseEntity<byte[]> tts(@RequestBody TtsDto ttsDto) {
        return aiModelService.tts(ttsDto);
    }


    /**
     * 获取历史记录
     */
    @GetMapping("/history")
    public Result<List<AiHistoryListVo>> getHistory(AiHistoryDto aiHistoryDto) {
        List<AiHistoryListVo> historyListVoList = aiModelService.getHistory(aiHistoryDto);
        return Result.success(historyListVoList);
    }

    /**
     * 根据记录id获取当前对话的历史记录
     */
    @GetMapping("/history/{id}")
    public Result<List<AiHistoryContent>> getHistoryById(@PathVariable Long id) {
        List<AiHistoryContent> aiHistoryContentList = aiModelService.getHistoryById(id);
        return Result.success(aiHistoryContentList);
    }

    /**
     * 根据记录id删除对话记录
     */
    @DeleteMapping("/history/{id}")
    public Result<String> deleteHistory(@PathVariable Long id) {
        aiModelService.deleteHistory(id);
        return Result.success();
    }

    /**
     * 根据记录id重命名标题
     */
    @PutMapping("/history")
    public Result<String> updateHistory(@RequestBody AiHistoryDto aiHistoryDto) {
        aiModelService.updateHistoryById(aiHistoryDto);
        return Result.success();
    }

    /**
     * 根据日期查询ai使用总数
     */
    @GetMapping("/count/{date}")
    public Long countDate(@PathVariable String date) {
        return aiModelService.count(date);
    }

    /**
     * 查询ai使用总数
     *
     * @return
     */
    @GetMapping("/count")
    public Long count() {
        return aiModelService.count(null);
    }

    /**
     * 查询近7日ai使用次数
     *
     * @return
     */
    @GetMapping("/countAiDay7")
    List<TopicDataVo> countAiDay7() {
        return aiModelService.countAiDay7();
    }

    /**
     * 根据用户id查询ai使用总数
     *
     * @param currentId
     * @return
     */
    @GetMapping("/countAi/{currentId}")
    Long countAi(@PathVariable Long currentId) {
        return aiModelService.countAi(currentId);
    }
}
