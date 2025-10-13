package com.ey.topic.topic.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ey.common.enums.RoleEnum;
import com.ey.model.entity.topic.Topic;
import com.ey.model.entity.topic.TopicSubject;
import com.ey.model.entity.topic.TopicSubjectTopic;
import com.ey.model.vo.topic.TopicSubjectVo;
import com.ey.service.utils.enums.StatusEnums;
import com.ey.topic.topic.mapper.TopicSubjectTopicMapper;
import com.ey.topic.topic.service.TopicAiService;
import com.ey.topic.topic.service.TopicService;
import com.ey.topic.topic.service.TopicSubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TopicAiServiceImpl implements TopicAiService {

    private final TopicService topicService;
    private final TopicSubjectTopicMapper topicSubjectTopicMapper;
    private final TopicSubjectService topicSubjectService;

    /**
     * 根据专题id查询该专题下所有的题目
     */
    @Override
    public List<Topic> getSubjectIdByTopicList(Long subjectId) {
        LambdaQueryWrapper<TopicSubjectTopic> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TopicSubjectTopic::getSubjectId, subjectId);
        List<TopicSubjectTopic> topicSubjectTopics = topicSubjectTopicMapper.selectList(queryWrapper);
        if (CollectionUtil.isEmpty(topicSubjectTopics)) {
            return null;
        }
        List<Long> topicIds = topicSubjectTopics.stream().map(TopicSubjectTopic::getTopicId).toList();
        return topicService.listByIds(topicIds);
    }

    /**
     * 查询全部专题或者会员专题
     */
    @Override
    public List<TopicSubjectVo> getSubject(String role, String createBy) {
        // 全部数据
        List<TopicSubject> topicSubjectList = null;
        // 会员数据
        List<TopicSubject> topicSubjects = null;
        // 查询公共题库
        LambdaQueryWrapper<TopicSubject> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TopicSubject::getCreateBy, RoleEnum.ADMIN.getRoleKey());
        queryWrapper.eq(TopicSubject::getStatus, StatusEnums.NORMAL.getCode());
        topicSubjectList = topicSubjectService.list(queryWrapper);
        // 如果是会员，查询自建专题
        if (role.equals(RoleEnum.MEMBER.getRoleKey())) {
            LambdaQueryWrapper<TopicSubject> memberQueryWrapper = new LambdaQueryWrapper<>();
            memberQueryWrapper.eq(TopicSubject::getCreateBy, createBy);
            memberQueryWrapper.eq(TopicSubject::getStatus, StatusEnums.NORMAL.getCode());
            topicSubjects = topicSubjectService.list(memberQueryWrapper);
        }
        // 如果会员自建专题不为空
        if (CollectionUtil.isNotEmpty(topicSubjects)) {
            // 添加进全部数据
            topicSubjectList.addAll(0, topicSubjects);
        }
        return topicSubjectList.stream().map(topicSubject -> {
            TopicSubjectVo topicSubjectVo = new TopicSubjectVo();
            BeanUtils.copyProperties(topicSubject, topicSubjectVo);
            return topicSubjectVo;
        }).toList();
    }
}
