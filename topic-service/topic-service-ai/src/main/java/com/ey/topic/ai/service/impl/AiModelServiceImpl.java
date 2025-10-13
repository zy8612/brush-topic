package com.ey.topic.ai.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.util.DateUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ey.client.system.SystemFeignClient;
import com.ey.client.topic.TopicFeignClient;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.enums.RoleEnum;
import com.ey.common.exception.TopicException;
import com.ey.common.security.utils.SecurityUtils;
import com.ey.model.dto.ai.AiHistoryDto;
import com.ey.model.dto.ai.ChatDto;
import com.ey.model.dto.ai.TtsDto;
import com.ey.model.dto.audit.TopicAudit;
import com.ey.model.dto.audit.TopicAuditCategory;
import com.ey.model.dto.audit.TopicAuditLabel;
import com.ey.model.dto.audit.TopicAuditSubject;
import com.ey.model.entity.ai.AiHistory;
import com.ey.model.entity.ai.AiLog;
import com.ey.model.entity.ai.AiRecord;
import com.ey.model.entity.ai.AiUser;
import com.ey.model.entity.system.SysRole;
import com.ey.model.entity.topic.Topic;
import com.ey.model.entity.topic.TopicCategory;
import com.ey.model.entity.topic.TopicLabel;
import com.ey.model.entity.topic.TopicSubject;
import com.ey.model.vo.ai.AiHistoryContent;
import com.ey.model.vo.ai.AiHistoryListVo;
import com.ey.model.vo.ai.AiHistoryVo;
import com.ey.model.vo.topic.TopicDataVo;
import com.ey.model.vo.topic.TopicSubjectVo;
import com.ey.service.utils.enums.StatusEnums;
import com.ey.topic.ai.constant.PromptConstant;
import com.ey.topic.ai.constant.ResultConstant;
import com.ey.topic.ai.enums.AiStatusEnums;
import com.ey.topic.ai.mapper.AiAuditLogMapper;
import com.ey.topic.ai.mapper.AiHistoryMapper;
import com.ey.topic.ai.mapper.AiRecordMapper;
import com.ey.topic.ai.properties.TtsProperties;
import com.ey.topic.ai.service.AiModelService;
import com.ey.topic.ai.service.AiUserManageService;
import com.ey.topic.ai.service.strategy.chat.ChatStrategy;
import com.ey.topic.ai.service.strategy.chat.ChatStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements AiModelService {

    private final ChatClient chatClient;
    private final AiRecordMapper aiRecordMapper;
    private final AiUserManageService aiUserManageService;
    private final SystemFeignClient systemFeignClient;
    private final AiHistoryMapper aiHistoryMapper;
    private final TopicFeignClient topicFeignClient;
    private final TtsProperties ttsProperties;
    private final AiAuditLogMapper aiAuditLogMapper;
    // 注入策略工厂
    private final ChatStrategyFactory chatStrategyFactory;

    /**
     * 随机鼓励语
     */
    private static final String[] ENCOURAGEMENTS = {
            "💪 加油！你能行的！",
            "✨ 你可以的，相信自己！",
            "🔥 别放弃，再想想看～",
            "🌟 你已经很棒了，继续加油！",
            "🧠 慢慢来，答案就在前方～",
            "🚀 再试一次，你离成功不远了！",
            "💡 这道题对你来说不是问题！",
            "🎯 坚持到底就是胜利！",
            "🌈 每一次尝试都让你更接近成功！",
            "🌻 你的努力正在开花结果！",
            "⚡ 让智慧之光指引你前进！",
            "🦸 你就是自己的超级英雄！",
            "🌊 像海浪一样永不言弃！",
            "🎯 专注目标，你一定能做到！",
            "🚴 保持平衡，稳步前进！",
            "🧩 每个难题都是成长的拼图！",
            "🏆 冠军的潜力就在你心中！",
            "🌠 梦想就在不远处等着你！",
            "🦉 智慧正在你的脑中闪耀！",
            "⏳ 时间会证明你的坚持！"
    };

    /**
     * 获取随机鼓励语
     *
     * @return
     */
    private static String getRandomEncouragement() {
        int index = (int) (Math.random() * ENCOURAGEMENTS.length);
        return ENCOURAGEMENTS[index];
    }

    /**
     * 使用api发起对话
     */
    @Override
    public Flux<String> chat(ChatDto chatDto) {
        // 1.记录ai使用记录
        recordAi(chatDto.getNickname());
        // 2.记录用户使用记录
        recordAiUser();
        // 对话内容超过上限
        if (chatDto.getMemoryId() >= 21) {
            return Flux.just("对话内容到达上限，请新建对话");
        }
        //策略工厂模式
        ChatStrategy strategy = chatStrategyFactory.getStrategy(chatDto.getModel());
        return strategy.handleChat(chatDto);
        /* 3.校验模式
        if (chatDto.getModel().equals(AiConstant.SYSTEM_MODEL)) {
            // 系统模式
            return systemModel(chatDto);
        } else if (chatDto.getModel().equals(AiConstant.AI_MODEL)) {
            //  AI模式
            return aiModel(chatDto);
        }
        //  混合模式
        return mixModel(chatDto);*/
    }

    @Override
    public ResponseEntity<byte[]> tts(TtsDto text) {
        recordAiUser();
        SpeechSynthesisParam param =
                SpeechSynthesisParam.builder()
                        .apiKey(ttsProperties.getApiKey())
                        .model(ttsProperties.getModel())
                        .voice(ttsProperties.getVoice())
                        .build();
        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, null);
        ByteBuffer audio = synthesizer.call(text.getText()); // 用前端传入的text
        byte[] audioBytes = audio.array();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=output.mp3");
        return ResponseEntity
                .ok()
                .headers(headers)
                .body(audioBytes);
    }

    //          ======== AI 系统模式 ========
    private Flux<String> systemModel(ChatDto chatDto) {
        // 获取当前用户名和id
        String currentName = SecurityUtils.getCurrentName();
        Long currentId = SecurityUtils.getCurrentId();
        String currentRole = SecurityUtils.getCurrentRole();
        // 提示词
        String prompt = null;

        // 获取上一次对话
        AiHistory aiHistory = getCurrentHistory(currentName, currentId, chatDto);

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
                    // 查询前1条发出的面试题
                    List<AiHistory> aiHistoryList = aiHistoryMapper.selectList(new QueryWrapper<AiHistory>()
                            .eq("user_id", currentId)
                            .eq("status", AiStatusEnums.SEND_TOPIC.getCode())
                            .eq("chat_id", chatDto.getChatId())
                            .orderByDesc("create_time")
                            .last("limit 1"));
                    log.info("aiHistoryList: {}", aiHistoryList);
                    // 将专题名称添加到prompt中
                    chatDto.setPrompt(aiHistoryList.get(0).getTitle());
                    // 继续将状态改为发送面试题并发送一道题目
                    return sendRandomTopicToUser(currentRole, currentName, chatDto);
                } else {
                    // 再次处理专题就改为发送面试题
                    return sendRandomTopicToUser(currentRole, currentName, chatDto);
                }
            }
        }
        return Flux.just(ResultConstant.SYSTEM_ERROR);
    }

    //          ======== AI 模型模式 ========
    private Flux<String> aiModel(ChatDto chatDto) {
        /**
         * 模型模式根据用户输入的题目类型进行发送面试题
         */
        // 获取当前用户Id
        Long currentId = SecurityUtils.getCurrentId();
        // 当前账户
        String currentName = SecurityUtils.getCurrentName();

        // 提示词
        String prompt = null;
        // 查询当前对话记录
        AiHistory aiHistory = getCurrentHistory(currentName, currentId, chatDto);
        // 处理对话逻辑
        if (aiHistory == null) {
            // 1.用户第一次对话需要题目类型的校验
            return verifyPrompt(chatDto, true, currentName, currentId);
        } else {
            // 2.获取上一条记录的状态
            Integer status = aiHistory.getStatus();

            // 上一条记录是ai提出问题
            if (AiStatusEnums.SEND_TOPIC.getCode().equals(status)) {
                // 用户就得输入答案
                prompt = "你提出面试题：" + aiHistory.getContent()
                        + "用户回答：" + chatDto.getPrompt() + "  " + PromptConstant.EVALUATE
                        + "结尾最后一定要一定要返回下面这句话\n" +
                        " > 请输入'**继续**'或者输入新的**题目类型**'";
                // 用户输入答案后将状态改为评估答案
                return saveEvaluateAnswer(prompt, aiHistory, AiStatusEnums.EVALUATE_ANSWER.getCode(), chatDto, currentName, currentId);
            }
            // 上一条记录是评估答案说明ai已经评估完了用户就得输入继续或者新专题
            if (AiStatusEnums.EVALUATE_ANSWER.getCode().equals(status)) {
                // 用户输入继续还是新专题
                if ("继续".equals(chatDto.getPrompt())) {
                    // 查询前1条发出的面试题
                    List<AiHistory> aiHistoryList = aiHistoryMapper.selectList(new QueryWrapper<AiHistory>()
                            .eq("user_id", currentId)
                            .eq("status", AiStatusEnums.SEND_TOPIC.getCode())
                            .eq("chat_id", chatDto.getChatId())
                            .orderByDesc("create_time")
                            .last("limit 1"));
                    log.info("aiHistoryList: {}", aiHistoryList);
                    // 将题目类型添加到prompt中
                    chatDto.setPrompt(aiHistoryList.get(0).getTitle());
                    // 继续将状态改为发送面试题并发送一道题目
                    return verifyPrompt(chatDto, false, currentName, currentId);
                } else {
                    // 再次处理就改为发送面试题
                    return verifyPrompt(chatDto, false, currentName, currentId);
                }
            }
        }
        return null;
    }

    //          ======== AI 混合模式 ========
    private Flux<String> mixModel(ChatDto chatDto) {
        /**
         * 混合模式用户输入题目类型从ai库中或者系统库中抽取
         */
        // 获取当前用户Id
        Long currentId = SecurityUtils.getCurrentId();
        // 当前账户
        String currentName = SecurityUtils.getCurrentName();
        // 当前权限
        String currentRole = SecurityUtils.getCurrentRole();
        // 查询当前对话
        AiHistory aiHistory = getCurrentHistory(currentName, currentId, chatDto);
        // 提示词
        String prompt = null;
        // 处理对话逻辑
        if (aiHistory == null) {
            // 1.用户第一次对话
            // 校验用户输入的题目专题是否在系统库中
            Long subjectId = getSubjectId(currentRole, currentName, chatDto);
            // 用户输入的题目专题不存在，校验用户输入的题目专题是否合法
            if (subjectId == null) {
                log.info("Hao-发ai题目");
                return verifyPrompt(chatDto, true, currentName, currentId);
            }
            log.info("Hao-发系统题目");
            // 存在发系统题目给用户
            return sendRandomTopicToUser(currentRole, currentName, chatDto);
        } else {
            // 2.说明ai已经给用户返回题目，校验用户输入的答案是否正确
            // 获取上一条记录的状态
            Integer status = aiHistory.getStatus();
            // 上一条记录是ai提出问题
            if (AiStatusEnums.SEND_TOPIC.getCode().equals(status)) {
                // 用户就得输入答案
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
                    // 查询前1条发出的面试题
                    List<AiHistory> aiHistoryList = aiHistoryMapper.selectList(new QueryWrapper<AiHistory>()
                            .eq("user_id", currentId)
                            .eq("status", AiStatusEnums.SEND_TOPIC.getCode())
                            .eq("chat_id", chatDto.getChatId())
                            .orderByDesc("create_time")
                            .last("limit 1"));
                    log.info("aiHistoryList: {}", aiHistoryList);
                    // 将题目类型添加到prompt中
                    chatDto.setPrompt(aiHistoryList.get(0).getTitle());
                }
                // 校验用户输入的题目专题是否在系统库中
                Long subjectId = getSubjectId(currentRole, currentName, chatDto);
                // 不在校验用户输入的题目专题是否合法
                if (subjectId != null) {
                    log.info("Hao-发系统题目");
                    // 存在发系统题目给用户
                    return sendRandomTopicToUser(currentRole, currentName, chatDto);
                }
                log.info("Hao-发ai题目");
                // 不存在
                // 继续将状态改为发送面试题并发送一道题目
                return verifyPrompt(chatDto, false, currentName, currentId);
            }
        }
        return null;
    }

    /**
     * 记录ai使用记录
     *
     * @param nickname
     */
    private void recordAi(String nickname) {
        // 如果没有昵称用账户名代替
        if (StrUtil.isEmpty(nickname)) {
            nickname = SecurityUtils.getCurrentName();
        }
        // 获取当前日期
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        LambdaQueryWrapper<AiRecord> aiRecordLambdaQueryWrapper = new LambdaQueryWrapper<>();
        // 查询当天用户是否调用过ai
        aiRecordLambdaQueryWrapper.eq(AiRecord::getAiTime, date);
        aiRecordLambdaQueryWrapper.eq(AiRecord::getUserId, SecurityUtils.getCurrentId());
        AiRecord aiRecord = aiRecordMapper.selectOne(aiRecordLambdaQueryWrapper);
        // 如果当天第一次调用则新增记录
        if (ObjectUtil.isEmpty(aiRecord)) {
            aiRecord = new AiRecord();
            aiRecord.setUserId(SecurityUtils.getCurrentId());
            aiRecord.setNickname(nickname);
            aiRecord.setAiTime(new Date());
            aiRecord.setCount(1L);
            aiRecordMapper.insert(aiRecord);
        } else {
            // 当天不是第一次调用，更新调用次数
            aiRecord.setCount(aiRecord.getCount() + 1);
            aiRecordMapper.updateById(aiRecord);
        }
    }

    /**
     * 记录用户使用记录
     */
    private void recordAiUser() {
        // 获取当前用户信息
        String username = SecurityUtils.getCurrentName();
        String role = SecurityUtils.getCurrentRole();
        Long userId = SecurityUtils.getCurrentId();
        // 查询ai用户表是否存在
        LambdaQueryWrapper<AiUser> aiUserLambdaQueryWrapper = new LambdaQueryWrapper<>();
        aiUserLambdaQueryWrapper.eq(AiUser::getUserId, userId);
        aiUserLambdaQueryWrapper.eq(AiUser::getAccount, username);
        AiUser aiUser = aiUserManageService.getOne(aiUserLambdaQueryWrapper);
        // 不存在则添加
        if (ObjectUtil.isEmpty(aiUser)) {
            aiUser = new AiUser();
            aiUser.setUserId(userId);
            aiUser.setAccount(username);
            aiUser.setAiCount(1L);
            // 管理员和用户都是默认100次，会员是1000次
            if (role.equals(RoleEnum.MEMBER.getRoleKey())) {
                aiUser.setCount(1000L);
            }
            // 查询权限
            SysRole sysRole = systemFeignClient.getByRoleKey(role);
            if (ObjectUtil.isEmpty(sysRole)) {
                throw new TopicException(ResultCodeEnum.ROLE_NO_EXIST);
            }
            aiUser.setRoleName(sysRole.getName());
            aiUser.setRecentlyUsedTime(DateUtils.parseLocalDateTime(DateUtils.format(new Date())));
            aiUserManageService.save(aiUser);
        } else {
            // 如果被禁用
            if (aiUser.getStatus() == 1) {
                throw new TopicException(ResultCodeEnum.AI_ERROR);
            }
            // 普通用户100，会员1000，管理员不限次数
            if (!role.equals(RoleEnum.ADMIN.getRoleKey())) {
                // 不为空校验是否还有次数
                if (aiUser.getAiCount() >= aiUser.getCount()) {
                    throw new TopicException(ResultCodeEnum.AI_COUNT_ERROR);
                }
            }
            // 使用次数+1
            aiUser.setAiCount(aiUser.getAiCount() + 1);
            // 更新最近使用时间
            aiUser.setRecentlyUsedTime(DateUtils.parseLocalDateTime(DateUtils.format(new Date())));
            aiUserManageService.updateById(aiUser);
        }
    }

    /**
     * 获取上一次对话
     */
    private AiHistory getCurrentHistory(String currentName, Long currentId, ChatDto chatDto) {
        AiHistory aiHistory = null;
        Page<AiHistory> aiHistoryPage = new Page<>(1, 1);
        LambdaQueryWrapper<AiHistory> aiHistoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
        // 根据会话id，用户名，id查询对话是否记录
        aiHistoryLambdaQueryWrapper.eq(AiHistory::getChatId, chatDto.getChatId());
        aiHistoryLambdaQueryWrapper.eq(AiHistory::getUserId, currentId);
        aiHistoryLambdaQueryWrapper.eq(AiHistory::getAccount, currentName);
        // 查询最新的对话
        aiHistoryLambdaQueryWrapper.orderByDesc(AiHistory::getCreateTime);
        Page<AiHistory> aiHistoryPageDb = aiHistoryMapper.selectPage(aiHistoryPage, aiHistoryLambdaQueryWrapper);
        if (!aiHistoryPageDb.getRecords().isEmpty()) {
            aiHistory = aiHistoryPageDb.getRecords().get(0);
        }
        return aiHistory;
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
                "> " + getRandomEncouragement();
        // 保存当前会话记录
        saveSendTopicHistory(chatDto, prompt, currentName, SecurityUtils.getCurrentId(), null);
        return Flux.just(prompt);
    }

    /**
     * 校验用户输入的专题是否合法，并返回不重复的题目给用户
     */
    private Flux<String> verifyPrompt(ChatDto chatDto, boolean isFirst, String currentName, Long currentId) {
        String prompt = null;
        // 如果不是第一次需要查询发送过的题目，防止发送相同题目
        if (!isFirst) {
            // 获取之前发送过的题目
            LambdaQueryWrapper<AiHistory> aiHistoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
            aiHistoryLambdaQueryWrapper.eq(AiHistory::getUserId, SecurityUtils.getCurrentId());
            aiHistoryLambdaQueryWrapper.eq(AiHistory::getChatId, chatDto.getChatId());
            aiHistoryLambdaQueryWrapper.eq(AiHistory::getStatus, AiStatusEnums.SEND_TOPIC.getCode());
            List<AiHistory> histories = aiHistoryMapper.selectList(aiHistoryLambdaQueryWrapper);
            // 拼接已发送的题目
            String topics = histories.stream()
                    .map(AiHistory::getOriginalTitle) // 映射到originalTitle
                    .filter(title -> title != null && !title.trim().isEmpty()) // 过滤掉null和空字符串
                    .collect(Collectors.joining("\n")); // 使用换行符连接
            prompt = PromptConstant.CHECK_TOPIC_TYPE +
                    "当前对话记录已经出过的面试题\n【：" + topics + "】" +
                    "就不可以在出了\n用户输入的面试题类型：【" + chatDto.getPrompt() + "】";
        } else {
            // 第一次直接发送用户输入的类型
            prompt = PromptConstant.CHECK_TOPIC_TYPE + "\n用户输入的面试题类型：【" + chatDto.getPrompt() + "】";
        }
        // 发起对话
        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        log.info("verifyPrompt================>?: {}", content);
        // 没通过
        if (content != null && content.equalsIgnoreCase("false")) {
            // 返回一个提示信息给用户
            return Flux.just("❌请输入正确的题目类型\uD83D\uDE0A");
        }
        // 通过了返回题目
        // 构造提示语
        prompt = "### 【" + chatDto.getPrompt() + "】类型 💡\n\n" +
                "## 面试题目：\n" +
                "**" + content + "**\n\n" +
                "> " + getRandomEncouragement();

        // 保存
        saveSendTopicHistory(chatDto, prompt, currentName, currentId, content);
        // 返回
        return Flux.just(prompt);
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
     * 获取历史记录
     *
     * @param aiHistoryDto
     * @return
     */
    @Override
    public List<AiHistoryListVo> getHistory(AiHistoryDto aiHistoryDto) {
        Long currentId = SecurityUtils.getCurrentId();
        // 设置分页参数
        Page<AiHistory> aiHistoryPage = new Page<>(aiHistoryDto.getPageNum(), aiHistoryDto.getPageSize());
        // 设置分页条件
        LambdaQueryWrapper<AiHistory> aiHistoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
        aiHistoryLambdaQueryWrapper.eq(AiHistory::getUserId, currentId); // 设置用户id
        aiHistoryLambdaQueryWrapper.eq(AiHistory::getParent, 1); // 表示第一条数据
        aiHistoryLambdaQueryWrapper.orderByDesc(AiHistory::getCreateTime);
        // 是否根据title查询
        if (StrUtil.isNotBlank(aiHistoryDto.getTitle())) {
            aiHistoryLambdaQueryWrapper.like(AiHistory::getTitle, aiHistoryDto.getTitle());
        }
        // 查询数据
        Page<AiHistory> page = aiHistoryMapper.selectPage(aiHistoryPage, aiHistoryLambdaQueryWrapper);
        List<AiHistory> aiHistoryList = page.getRecords();
        // 返回数据
        List<AiHistoryListVo> aiHistoryListVoList = new ArrayList<>();
        // 获取日期
        List<String> dates = aiHistoryList.stream()
                .map(aiHistory -> DateUtil.format(aiHistory.getCreateTime(), "yyyy-MM-dd"))
                .distinct().toList();
        for (String date : dates) {
            AiHistoryListVo aiHistoryListVo = new AiHistoryListVo();
            aiHistoryListVo.setDate(date);
            List<AiHistoryVo> aiHistories = aiHistoryList.stream().filter(aiHistory ->
                            DateUtil.format(aiHistory.getCreateTime(), "yyyy-MM-dd").equals(date))
                    .map(aiHistory -> {
                        AiHistoryVo aiHistoryVo = new AiHistoryVo();
                        BeanUtils.copyProperties(aiHistory, aiHistoryVo);
                        return aiHistoryVo;
                    }).toList();
            aiHistoryListVo.setAiHistoryVos(aiHistories);
            aiHistoryListVoList.add(aiHistoryListVo);
        }
        return aiHistoryListVoList;
    }

    /**
     * 根据记录id获取到对话历史记录
     *
     * @param id 记录表的id
     * @return
     */
    @Override
    public List<AiHistoryContent> getHistoryById(Long id) {
        if (id == null) {
            throw new TopicException(ResultCodeEnum.AI_HISTORY_ERROR);
        }
        // 根据id查询会话id
        AiHistory aiHistory = aiHistoryMapper.selectById(id);
        String chatId = aiHistory.getChatId();
        // 查询这次会话的所用内容
        LambdaQueryWrapper<AiHistory> aiHistoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
        aiHistoryLambdaQueryWrapper.eq(AiHistory::getChatId, chatId);
        aiHistoryLambdaQueryWrapper.eq(AiHistory::getUserId, SecurityUtils.getCurrentId());
        // 父层级第一个
        aiHistoryLambdaQueryWrapper.orderByDesc(AiHistory::getParent);
        // 子层级根据创建时间升序，最新的记录在最底下
        aiHistoryLambdaQueryWrapper.orderByAsc(AiHistory::getCreateTime);
        List<AiHistory> histories = aiHistoryMapper.selectList(aiHistoryLambdaQueryWrapper);
        return histories.stream().map(history -> {
            AiHistoryContent aiHistoryContent = new AiHistoryContent();
            BeanUtils.copyProperties(history, aiHistoryContent);
            return aiHistoryContent;
        }).toList();
    }

    /**
     * 根据id删除记录
     *
     * @param id
     */
    @Override
    public void deleteHistory(Long id) {
        if (id == null) {
            throw new TopicException(ResultCodeEnum.AI_HISTORY_DELETE_ERROR);
        }
        // 查询是否存在
        AiHistory aiHistory = aiHistoryMapper.selectById(id);
        if (aiHistory == null) {
            throw new TopicException(ResultCodeEnum.AI_HISTORY_ERROR);
        }
        // 根据对话id删除所有的历史记录
        LambdaQueryWrapper<AiHistory> aiHistoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
        aiHistoryLambdaQueryWrapper.eq(AiHistory::getChatId, aiHistory.getChatId());
        // 物理删除
        aiHistoryMapper.delete(aiHistoryLambdaQueryWrapper);
    }

    /**
     * 修改父层级title
     *
     * @param aiHistoryDto
     */
    @Override
    public void updateHistoryById(AiHistoryDto aiHistoryDto) {
        if (aiHistoryDto == null) {
            throw new TopicException(ResultCodeEnum.AI_HISTORY_UPDATE_ERROR);
        }
        // 校验
        if (aiHistoryDto.getId() == null || aiHistoryDto.getTitle() == null) {
            throw new TopicException(ResultCodeEnum.AI_HISTORY_UPDATE_ERROR);
        }
        AiHistory aiHistory = aiHistoryMapper.selectById(aiHistoryDto.getId());
        if (aiHistory == null) {
            throw new TopicException(ResultCodeEnum.AI_HISTORY_UPDATE_ERROR);
        }
        // 开始修改
        aiHistory.setTitle(aiHistoryDto.getTitle());
        aiHistoryMapper.updateById(aiHistory);
    }

    /**
     * 审核分类名称是否合法
     *
     * @param topicAuditCategory
     */
    @Override
    public void auditCategory(TopicAuditCategory topicAuditCategory) {
        // 获取分类名称
        String categoryName = topicAuditCategory.getCategoryName();
        // 封装提示词
        String prompt = PromptConstant.AUDIT_CATEGORY + "\n" +
                "分类名称: 【" + categoryName + "】";
        // 调用ai审核
        String content = getAiContent(prompt, topicAuditCategory.getAccount(), topicAuditCategory.getUserId());
        // 解析结果
        log.info("AI返回结果: {}", content);
        TopicCategory topicCategory = new TopicCategory();
        String reason = null;
        try {
            // 转换结果
            JSONObject jsonObject = JSON.parseObject(content);
            boolean result = false;
            if (jsonObject != null) {
                result = jsonObject.getBooleanValue("result");
            }
            if (jsonObject != null) {
                reason = jsonObject.getString("reason");
            }
            topicCategory.setId(topicAuditCategory.getId());
            if (result) {
                log.info("审核通过: {}", reason);
                // 处理审核通过的逻辑
                topicCategory.setStatus(StatusEnums.NORMAL.getCode());
            } else {
                log.warn("审核未通过: {}", reason);
                // 处理审核未通过的逻辑
                topicCategory.setStatus(StatusEnums.AUDIT_FAIL.getCode());
                // 失败原因
                topicCategory.setFailMsg(reason);
            }
        } catch (Exception e) {
            log.error("解析AI返回结果失败: {}", content, e);
            // 处理解析失败的情况
            topicCategory.setStatus(StatusEnums.AUDIT_FAIL.getCode());
            topicCategory.setFailMsg("解析AI返回结果失败");
            reason = "解析AI返回结果失败";
        }
        // 修改状态
        topicFeignClient.auditCategory(topicCategory);
        // 记录日志
        recordAuditLog(reason, topicAuditCategory.getAccount(), topicAuditCategory.getUserId());
    }

    @Override
    public void auditSubject(TopicAuditSubject topicAuditSubject) {
        // 获取分类
        String categoryName = topicAuditSubject.getCategoryName();
        // 获取专题名称
        String subjectName = topicAuditSubject.getSubjectName();
        // 专题描述
        String subjectDesc = topicAuditSubject.getSubjectDesc();
        // 提示词
        String prompt = PromptConstant.AUDIT_SUBJECT + "\n" +
                "专题内容: 【" + subjectName + "】\n" +
                "专题描述: 【" + subjectDesc + "】\n" +
                "分类名称: 【" + categoryName + "】";
        // 调用ai审核
        String content  = getAiContent(prompt, topicAuditSubject.getAccount(), topicAuditSubject.getUserId());
        // 解析结果
        log.info("AI返回结果: {}", content);
        TopicSubject topicSubject = new TopicSubject();
        String reason = null;
        try {
            // 转换结果
            JSONObject jsonObject = JSON.parseObject(content);
            boolean result = false;
            if (jsonObject != null) {
                result = jsonObject.getBooleanValue("result");
            }
            if (jsonObject != null) {
                reason = jsonObject.getString("reason");
            }
            topicSubject.setId(topicAuditSubject.getId());
            if (result) {
                log.info("审核通过: {}", reason);
                // 处理审核通过的逻辑
                topicSubject.setStatus(StatusEnums.NORMAL.getCode());
            } else {
                log.warn("审核未通过: {}", reason);
                // 处理审核未通过的逻辑
                // 失败原因
                topicSubject.setFailMsg(reason);
                topicSubject.setStatus(StatusEnums.AUDIT_FAIL.getCode());
            }
        } catch (Exception e) {
            log.error("解析AI返回结果失败: {}", content, e);
            // 处理解析失败的情况
            topicSubject.setStatus(StatusEnums.AUDIT_FAIL.getCode());
            topicSubject.setFailMsg("解析AI返回结果失败");
            reason = "解析AI返回结果失败";
        }
        // 调用远程服务的接口实现状态修改
        topicFeignClient.auditSubject(topicSubject);
        // 记录日志
        recordAuditLog(reason, topicAuditSubject.getAccount(), topicAuditSubject.getUserId());
    }

    /**
     * 审核题目标签是否合法
     *
     * @param topicAuditLabel
     */
    public void auditLabel(TopicAuditLabel topicAuditLabel) {
        // 获取分类名称
        String labelName = topicAuditLabel.getLabelName();
        // 封装提示词
        String prompt = PromptConstant.AUDIT_LABEL + "\n" +
                "标签名称: 【" + labelName + "】";
        // 发送给ai
        String content = getAiContent(prompt, topicAuditLabel.getAccount(), topicAuditLabel.getUserId());
        // 解析结果
        log.info("AI返回结果: {}", content);
        TopicLabel topicLabel = new TopicLabel();
        String reason = null;
        try {
            // 转换结果
            JSONObject jsonObject = JSON.parseObject(content);
            boolean result = false;
            if (jsonObject != null) {
                result = jsonObject.getBooleanValue("result");
            }
            if (jsonObject != null) {
                reason = jsonObject.getString("reason");
            }
            topicLabel.setId(topicAuditLabel.getId());
            if (result) {
                log.info("审核通过: {}", reason);
                // 处理审核通过的逻辑
                topicLabel.setStatus(StatusEnums.NORMAL.getCode());
            } else {
                log.warn("审核未通过: {}", reason);
                // 处理审核未通过的逻辑
                // 失败原因
                topicLabel.setFailMsg(reason);
                topicLabel.setStatus(StatusEnums.AUDIT_FAIL.getCode());
            }
        } catch (Exception e) {
            log.error("解析AI返回结果失败: {}", content, e);
            // 处理解析失败的情况
            topicLabel.setStatus(StatusEnums.AUDIT_FAIL.getCode());
            topicLabel.setFailMsg("解析AI返回结果失败");
            reason = "解析AI返回结果失败";
        }
        // 调用远程服务的接口实现状态修改
        topicFeignClient.auditLabel(topicLabel);
        // 记录日志
        recordAuditLog(reason, topicAuditLabel.getAccount(), topicAuditLabel.getUserId());
    }

    /**
     * 审核题目并生成题目答案
     *
     * @param topicAudit
     */
    public void auditTopic(TopicAudit topicAudit) {
        // 获取题目标题
        String topicName = topicAudit.getTopicName();
        // 获取题目专题名称
        String subjectName = topicAudit.getTopicSubjectName();
        // 获取标题名称
        String labelName = topicAudit.getTopicLabelName();
        // 获取题目答案
        String answer = topicAudit.getAnswer();
        // 封装提示词
        String prompt = PromptConstant.AUDIT_TOPIC + "\n" +
                "面试题名称: 【" + topicName + "】\n" +
                "用户输入的面试题答案: 【" + answer + "】\n" +
                "关联标签可以有多个他们是通过':'分割的: 【" + labelName + "】\n" +
                "所属专题: 【" + subjectName + "】\n";
        // 发送给ai
        String content = getAiContent(prompt, topicAudit.getAccount(), topicAudit.getUserId());
        // 解析结果
        log.info("AI返回结果: {}", content);
        Topic topic = new Topic();
        String reason = null;
        try {
            // 转换结果
            JSONObject jsonObject = JSON.parseObject(content);
            boolean result = false;
            if (jsonObject != null) {
                result = jsonObject.getBooleanValue("result");
            }
            if (jsonObject != null) {
                reason = jsonObject.getString("reason");
            }
            topic.setId(topicAudit.getId());
            if (result) {
                log.info("审核通过: {}", reason);
                // 处理审核通过的逻辑
                topic.setStatus(StatusEnums.NORMAL.getCode());
            } else {
                log.warn("审核未通过: {}", reason);
                // 处理审核未通过的逻辑
                topic.setAiAnswer("");
                // 失败原因
                topic.setFailMsg(reason);
                topic.setStatus(StatusEnums.AUDIT_FAIL.getCode());
            }
        } catch (Exception e) {
            log.error("解析AI返回结果失败: {}", content, e);
            // 处理解析失败的情况
            topic.setStatus(StatusEnums.AUDIT_FAIL.getCode());
            topic.setFailMsg("解析AI返回结果失败");
            reason = "解析AI返回结果失败";
        }
        // 调用远程服务的接口实现状态修改
        topicFeignClient.auditTopic(topic);
        // 记录日志
        recordAuditLog(reason, topicAudit.getAccount(), topicAudit.getUserId());
    }

    /**
     * 调用ai生成结果
     * @param prompt
     * @param account
     * @param userId
     * @return
     */
    private String getAiContent(String prompt, String account, Long userId) {
        try {
            return chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            // 记录日志
            AiLog aiLog = new AiLog();
            aiLog.setAccount(account);
            aiLog.setContent("AI回复异常");
            aiLog.setUserId(userId);
            aiAuditLogMapper.insert(aiLog);
            throw new TopicException(ResultCodeEnum.FAIL);
        }
    }

    /**
     * 记录审核的日志
     */
    @Override
    public void recordAuditLog(String content, String account, Long userId) {
        AiLog aiLog = new AiLog();
        aiLog.setContent(content);
        aiLog.setAccount(account);
        aiLog.setUserId(userId);
        aiAuditLogMapper.insert(aiLog);
    }

    /**
     * 根据用户id查询ai使用总数
     *
     * @param currentId
     * @return
     */
    @Override
    public Long countAi(Long currentId) {
        Long count = aiRecordMapper.countAi(currentId);
        count = count == null ? 0L : count;
        return count;
    }

    @Override
    public Long count(String date) {
        Long count = aiRecordMapper.countAiFrequency(date);
        count = count == null ? 0L : count;
        return count;
    }

    @Override
    public List<TopicDataVo> countAiDay7() {
        // 1. 生成最近7天日期列表
        List<LocalDate> dateRange = Stream.iterate(LocalDate.now().minusDays(6),
                        d -> d.plusDays(1))
                .limit(7)
                .toList();

        // 2. 查询数据库获取有数据的日期
        List<TopicDataVo> dbResults = aiRecordMapper.countAiDay7(
                dateRange.get(0).toString(),
                dateRange.get(6).toString()
        );
        // 3. 创建日期->计数的映射
        Map<String, Integer> dateCountMap = dbResults.stream()
                .collect(Collectors.toMap(
                        TopicDataVo::getDate,
                        TopicDataVo::getCount
                ));
        // 4. 构建完整7天的结果列表
        return dateRange.stream()
                .map(date -> {
                    TopicDataVo vo = new TopicDataVo();
                    vo.setDate(date.toString());
                    vo.setCount(dateCountMap.getOrDefault(date.toString(), 0));
                    return vo;
                })
                .toList();
    }

    /**
     * 生成ai答案
     * @param topicAudit
     */
    @Override
    public void generateAnswer(TopicAudit topicAudit) {
        // 封装提示词
        String prompt = PromptConstant.GENERATE_ANSWER + "\n" +
                "面试题: 【" + topicAudit.getTopicName() + "】";
        // 发送给ai
        String aiContent = getAiContent(prompt, topicAudit.getAccount(), topicAudit.getUserId());
        Topic topic = new Topic();
        topic.setAiAnswer(aiContent);
        topic.setId(topicAudit.getId());
        // 调用远程服务的接口实现修改ai答案
        topicFeignClient.updateAiAnswer(topic);
        // 记录日志
        recordAuditLog("生成AI答案成功啦！", topicAudit.getAccount(), topicAudit.getUserId());
    }

}