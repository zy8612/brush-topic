package com.ey.topic.ai.service.strategy.chat;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ey.client.topic.TopicFeignClient;
import com.ey.common.enums.RoleEnum;
import com.ey.common.security.utils.SecurityUtils;
import com.ey.model.dto.ai.ChatDto;
import com.ey.model.entity.ai.AiHistory;
import com.ey.model.entity.topic.Topic;
import com.ey.model.vo.topic.TopicSubjectVo;
import com.ey.topic.ai.constant.PromptConstant;
import com.ey.topic.ai.constant.ResultConstant;
import com.ey.topic.ai.enums.AiStatusEnums;
import com.ey.topic.ai.mapper.AiHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class SystemModelStrategy implements ChatStrategy {

    private final AiHistoryMapper aiHistoryMapper;
    private final TopicFeignClient topicFeignClient;
    private final ChatClient chatClient;

    @Override
    public Flux<String> handleChat(ChatDto chatDto) {
        // 获取当前用户名和id
        String currentName = SecurityUtils.getCurrentName();
        Long currentId = SecurityUtils.getCurrentId();
        String currentRole = SecurityUtils.getCurrentRole();
        // 提示词
        String prompt = null;

        // 获取上一次对话
        AiHistory aiHistory = AiChatUtils.getCurrentHistory(aiHistoryMapper, chatDto);

        if (ObjectUtil.isNull(aiHistory)) {
            // 1..用户第一次对话
            return sendRandomTopicToUser(currentRole, currentName, chatDto);
        } else {
            // 2.用户不是第一次对话
            /**
             * 有2种可能
             * 1.用户重新输入专题名称
             * 2.用户输入答案
             */
            // 获取上一次对话的状态
            Integer status = aiHistory.getStatus();
            // 上一个对话为发送题目，那用户输入的是面试题答案
            if (status.equals(AiStatusEnums.SEND_TOPIC.getCode())) {
                prompt = "你提出面试题：" + aiHistory.getContent()
                        + "用户回答：" + chatDto.getPrompt() + "  " + PromptConstant.EVALUATE
                        + "结尾最后一定要一定要返回下面这句话\n" +
                        " > 请输入'**继续**'或者输入新的**题目类型**'";
                // 用户输入答案后将状态改为评估答案
                return saveEvaluateAnswer(prompt, aiHistory, AiStatusEnums.EVALUATE_ANSWER.getCode(),
                        chatDto, currentName, currentId);
            }
            // 上一条记录是评估答案说明ai已经评估完了用户就得输入继续或者新专题
            if (AiStatusEnums.EVALUATE_ANSWER.getCode().equals(status)) {
                // 用户输入继续还是新专题
                if ("继续".equals(chatDto.getPrompt())) {
                    AiHistory lastTopic = AiChatUtils.getLastSendTopic(aiHistoryMapper, chatDto);
                    if (lastTopic != null) {
                        chatDto.setPrompt(lastTopic.getTitle());
                    }
                }
                // 再次处理专题就改为发送面试题
                return sendRandomTopicToUser(currentRole, currentName, chatDto);
            }
        }
        return Flux.just(ResultConstant.SYSTEM_ERROR);
    }

    /**
     * 根据专题名称和ID获取一道随机题目，并返回给用户
     */
    private Flux<String> sendRandomTopicToUser(String currentRole, String currentName, ChatDto chatDto) {
        // 根据用户输入的专题名称获取对应的专题id
        Long subjectId = getSubjectId(currentRole, currentName, chatDto);
        if (subjectId == null) {
            // false表示用户输入的专题不存在系统中和会员自定义中
            if (currentRole.equals(RoleEnum.MEMBER.getRoleKey())) {
                // 是会员
                return Flux.just(ResultConstant.PLEASE_INPUT_TOPIC_SUBJECT_OR_CUSTOM_TOPIC_SUBJECT);
            } else {
                return Flux.just(ResultConstant.PLEASE_INPUT_TOPIC_SUBJECT);
            }
        }
        // 查询该专题下的所有题目并随机返回一道题目
        Topic randomTopic = getSubjectTopicList(subjectId);
        if (randomTopic == null) {
            return Flux.just(ResultConstant.SYSTEM_IS_COMPLETING_TOPIC);
        }
        // 构造提示语
        String prompt = "### 【" + chatDto.getPrompt() + "】专题 💡\n\n" +
                "## 面试题目：\n" +
                "**" + randomTopic.getTopicName() + "**\n\n" +
                "> " + AiChatUtils.getRandomEncouragement();
        // 保存当前会话记录
        saveSendTopicHistory(chatDto, prompt, currentName, SecurityUtils.getCurrentId(), null);
        return Flux.just(prompt);
    }

    /**
     * 保存评估回答的记录
     */
    private Flux<String> saveEvaluateAnswer(String prompt, AiHistory aiHistory, Integer status, ChatDto chatDto, String currentName, Long currentId) {
        // 拼接信息
        StringBuffer fullReply = new StringBuffer();
        // 获取对回答的评价
        Flux<String> content = chatClient.prompt()
                .user(prompt)
                .stream()
                .content();
        Flux<String> stringFlux = content.flatMap(response -> {
            fullReply.append(response);
            return Flux.just(response);
        }).doOnComplete(() -> {
            log.info("执行完成保存历史记录");
            AiHistory history = new AiHistory();
            // 如果是ai模式需要保存原始题目
            if (StrUtil.isNotBlank(aiHistory.getOriginalTitle())) {
                history.setOriginalTitle(aiHistory.getOriginalTitle());
            }
            history.setChatId(chatDto.getChatId());
            history.setAccount(currentName);
            history.setUserId(currentId);
            history.setContent(fullReply.toString());
            history.setTitle(chatDto.getPrompt());
            history.setStatus(status);
            history.setMode(chatDto.getModel());
            history.setParent(0);
            aiHistoryMapper.insert(history);
        });
        return stringFlux;
    }

    /**
     * 查询用户输入专题是否存在
     */
    private Long getSubjectId(String currentRole, String currentName, ChatDto chatDto) {
        // 获取专题列表
        List<TopicSubjectVo> subjectVoList = topicFeignClient.getSubject(currentRole, currentName);
        if (CollectionUtil.isNotEmpty(subjectVoList)) {
            // 找出用户输入的专题
            List<TopicSubjectVo> list = subjectVoList.stream().filter(subjectVo ->
                            subjectVo.getSubjectName().equals(chatDto.getPrompt()))
                    .toList();
            if (CollectionUtils.isEmpty(list)) {
                // 系统中不存在用户输入的专题
                return null;
            } else {
                return list.get(0).getId();
            }
        } else {
            return null;
        }
    }

    /**
     * 查询专题下所有的题目并随机返回一道题目
     */
    private Topic getSubjectTopicList(Long subjectId) {
        List<Topic> topicList = topicFeignClient.getSubjectTopicList(subjectId);
        if (CollectionUtil.isEmpty(topicList)) {
            return null;
        }
        // 随机抽取题目
        int randomIndex = (int) (Math.random() * topicList.size());
        Topic selectedTopic = topicList.get(randomIndex);

        log.info("随机抽取到题目：{}", selectedTopic.getTopicName());
        return selectedTopic;
    }

    /**
     * 保存提供面试题的记录
     */
    private void saveSendTopicHistory(ChatDto chatDto, String prompt, String currentName, Long currentId, String originalTitle) {
        // 封装记录
        AiHistory aiHistory = new AiHistory();
        // 查询是是否存在父级id
        LambdaQueryWrapper<AiHistory> aiHistoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
        aiHistoryLambdaQueryWrapper.eq(AiHistory::getChatId, chatDto.getChatId());
        aiHistoryLambdaQueryWrapper.eq(AiHistory::getParent, 1);
        aiHistoryLambdaQueryWrapper.orderByDesc(AiHistory::getCreateTime);
        AiHistory parent = aiHistoryMapper.selectOne(aiHistoryLambdaQueryWrapper);
        // 不存在父级id
        if (ObjectUtil.isEmpty(parent) && chatDto.getMemoryId() == 1) {
            // 设置当前为父层级
            aiHistory.setParent(1);
        } else {
            aiHistory.setParent(0);
        }
        aiHistory.setChatId(chatDto.getChatId());
        aiHistory.setAccount(currentName);
        aiHistory.setUserId(currentId);
        aiHistory.setContent(prompt);
        aiHistory.setTitle(chatDto.getPrompt());
        aiHistory.setStatus(AiStatusEnums.SEND_TOPIC.getCode());
        aiHistory.setMode(chatDto.getModel());
        aiHistory.setOriginalTitle(originalTitle);
        aiHistoryMapper.insert(aiHistory);
    }
}
