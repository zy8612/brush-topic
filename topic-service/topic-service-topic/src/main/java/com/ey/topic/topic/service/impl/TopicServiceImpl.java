package com.ey.topic.topic.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ey.common.constant.RedisConstant;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.enums.RoleEnum;
import com.ey.common.exception.TopicException;
import com.ey.common.security.utils.SecurityUtils;
import com.ey.model.dto.audit.TopicAudit;
import com.ey.model.dto.topic.TopicDto;
import com.ey.model.dto.topic.TopicListDto;
import com.ey.model.dto.topic.TopicRecordCountDto;
import com.ey.model.entity.topic.*;
import com.ey.model.excel.topic.TopicExcel;
import com.ey.model.excel.topic.TopicExcelExport;
import com.ey.model.excel.topic.TopicMemberExcel;
import com.ey.model.vo.topic.TopicAnswerVo;
import com.ey.model.vo.topic.TopicCollectionVo;
import com.ey.model.vo.topic.TopicDetailVo;
import com.ey.model.vo.topic.TopicVo;
import com.ey.service.utils.constant.RabbitConstant;
import com.ey.service.utils.enums.StatusEnums;
import com.ey.service.utils.mq.RabbitService;
import com.ey.topic.topic.mapper.*;
import com.ey.topic.topic.service.TopicLabelService;
import com.ey.topic.topic.service.TopicService;
import com.ey.topic.topic.service.TopicSubjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicServiceImpl extends ServiceImpl<TopicMapper, Topic> implements TopicService {

    private final TopicMapper topicMapper;
    private final TopicSubjectTopicMapper topicSubjectTopicMapper;
    private final TopicLabelTopicMapper topicLabelTopicMapper;
    private final TopicSubjectService topicSubjectService;
    private final TopicLabelService topicLabelService;
    private final RabbitService rabbitService;
    private final TopicCollectionMapper topicCollectionMapper;
    private final TopicRecordMapper topicRecordMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 查询题目列表
     *
     * @param topicListDto
     * @return
     */
    @Override
    public Map<String, Object> topicList(TopicListDto topicListDto) {
        // 获取当前用户登录名称
        String username = SecurityUtils.getCurrentName();
        // 获取当前用户登录id
        Long currentId = SecurityUtils.getCurrentId();
        // 获取当前用户权限
        String role = SecurityUtils.getCurrentRole();
        log.info("当前用户登录名称和id：{},{}", username, currentId);
        // 设置分页条件
        LambdaQueryWrapper<Topic> queryWrapper = new LambdaQueryWrapper<>();
        // 判断是否为会员
        if (role.equals(RoleEnum.MEMBER.getRoleKey())) {
            // 根据当前登录用户查询
            queryWrapper.like(Topic::getCreateBy, username);
        } else {
            // 是管理员
            // 判断是否查询创建人
            if (!StrUtil.isEmpty(topicListDto.getCreateBy())) {
                queryWrapper.like(Topic::getCreateBy, topicListDto.getCreateBy());
            }
        }
        // 判断题目名称
        if (!StrUtil.isEmpty(topicListDto.getTopicName())) {
            queryWrapper.like(Topic::getTopicName, topicListDto.getTopicName());
        }
        // 判断创建时间
        if (!StrUtil.isEmpty(topicListDto.getBeginCreateTime()) && !StrUtil.isEmpty(topicListDto.getEndCreateTime())) {
            queryWrapper.between(Topic::getCreateTime, topicListDto.getBeginCreateTime(), topicListDto.getEndCreateTime());
        }
        queryWrapper.orderByDesc(Topic::getCreateTime);
        // 查询所有题目
        Page<Topic> page = new Page<>(topicListDto.getPageNum(), topicListDto.getPageSize());
        Page<Topic> topicPage = topicMapper.selectPage(page, queryWrapper);
        // 封装返回对象，给题目加标签和专题
        List<TopicVo> list = topicPage.getRecords().stream().map(topic -> {
            TopicVo topicVo = new TopicVo();
            BeanUtils.copyProperties(topic, topicVo);
            // 根据专题id查询专题
            LambdaQueryWrapper<TopicSubjectTopic> topicSubjectTopicLambdaQueryWrapper = new LambdaQueryWrapper<>();
            topicSubjectTopicLambdaQueryWrapper.eq(TopicSubjectTopic::getTopicId, topic.getId());
            // 一个题目只有一个专题
            TopicSubjectTopic topicSubjectTopic = topicSubjectTopicMapper.selectOne(topicSubjectTopicLambdaQueryWrapper);
            if (ObjectUtil.isNotNull(topicSubjectTopic)) {
                TopicSubject topicSubject = topicSubjectService.getById(topicSubjectTopic.getSubjectId());
                if (ObjectUtil.isNotNull(topicSubject)) {
                    topicVo.setSubject(topicSubject.getSubjectName());
                }
            }
            List<String> labelNameList = new ArrayList<>();
            // 封装标签
            LambdaQueryWrapper<TopicLabelTopic> topicLabelTopicLambdaQueryWrapper = new LambdaQueryWrapper<>();
            topicLabelTopicLambdaQueryWrapper.eq(TopicLabelTopic::getTopicId, topic.getId());
            // 一个题目可以有多个标签
            List<TopicLabelTopic> topicLabelTopics = topicLabelTopicMapper.selectList(topicLabelTopicLambdaQueryWrapper);
            if (CollectionUtils.isNotEmpty(topicLabelTopics)) {
                // 转为能批量查询的ids
                List<Long> labelIds = topicLabelTopics.stream()
                        .map(TopicLabelTopic::getLabelId)
                        .toList();
                // 根据标签的id批量查询
                LambdaQueryWrapper<TopicLabel> topicLabelLambdaQueryWrapper = new LambdaQueryWrapper<>();
                topicLabelLambdaQueryWrapper.in(TopicLabel::getId, labelIds);
                List<TopicLabel> labels = topicLabelService.list(topicLabelLambdaQueryWrapper);
                if (CollectionUtils.isNotEmpty(labels)) {
                    labels.forEach(label -> {
                        labelNameList.add(label.getLabelName());
                    });
                }
            }
            topicVo.setLabels(labelNameList);
            return topicVo;
        }).toList();
        return Map.of("total", page.getTotal(), "rows", list);
    }

    /**
     * 新增题目
     *
     * @param topicDto
     */
    @Transactional
    public void addTopic(TopicDto topicDto) {
        LambdaQueryWrapper<Topic> topicLambdaQueryWrapper = new LambdaQueryWrapper<>();
        topicLambdaQueryWrapper.eq(Topic::getTopicName, topicDto.getTopicName());
        // 查询
        boolean isExist = topicMapper.exists(topicLambdaQueryWrapper);
        if (isExist) {
            throw new TopicException(ResultCodeEnum.TOPIC_NAME_EXIST);
        }
        // 查询专题
        TopicSubject topicSubject = topicSubjectService.getById(topicDto.getSubjectId());
        // 判断
        if (ObjectUtil.isNull(topicSubject)) {
            throw new TopicException(ResultCodeEnum.SUBJECT_NOT_EXIST);
        }
        StringBuilder labelNames = new StringBuilder();
        // 查询标签
        List<TopicLabel> topicLabels = topicLabelService.listByIds(topicDto.getLabelIds());
        if (CollectionUtils.isEmpty(topicLabels)) {
            throw new TopicException(ResultCodeEnum.LABEL_NOT_EXIST);
        }
        List<Long> labelIds = topicLabels.stream().map(TopicLabel::getId).toList();
        for (TopicLabel topicLabel : topicLabels) {
            labelNames.append(topicLabel.getLabelName());
            // 拼接最后一个不要拼接
            if (topicLabels.size() != topicLabels.indexOf(topicLabel) + 1) {
                labelNames.append(":");
            }
        }
        log.info("标签名称：{}", labelNames);

        // 获取当前用户登录名称
        String username = SecurityUtils.getCurrentName();
        // 新增题目
        Topic topic = new Topic();
        BeanUtils.copyProperties(topicDto, topic);
        // 设置创建人
        topic.setCreateBy(username);
        // 每日题目只能有9题
        if (topic.getIsEveryday() == 1) {
            LambdaQueryWrapper<Topic> topicLambdaQueryWrapper1 = new LambdaQueryWrapper<>();
            topicLambdaQueryWrapper1.eq(Topic::getIsEveryday, 1);
            List<Topic> topics = topicMapper.selectList(topicLambdaQueryWrapper1);
            if (CollectionUtils.isNotEmpty(topics) && topics.size() >= 9) {
                throw new TopicException(ResultCodeEnum.TOPIC_EVERYDAY_ERROR);
            }
        }

        // 获取当前用户id
        Long currentId = SecurityUtils.getCurrentId();
        String currentRole = SecurityUtils.getCurrentRole();
        if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
            // 是开发者不需要审核
            topic.setStatus(StatusEnums.NORMAL.getCode());
            // 开始插入
            topicMapper.insert(topic);
        } else {
            topicMapper.insert(topic);
            // 不是开发者需要审核
            topic.setStatus(StatusEnums.AUDITING.getCode());
            // 异步发送信息给AI审核
            TopicAudit topicAudit = new TopicAudit();
            topicAudit.setId(topic.getId());
            topicAudit.setTopicName(topic.getTopicName());
            topicAudit.setAccount(username);
            topicAudit.setAnswer(topic.getAnswer());
            topicAudit.setUserId(currentId);
            topicAudit.setTopicSubjectName(topicSubject.getSubjectName());
            topicAudit.setTopicLabelName(labelNames.toString());
            String topicAuditJson = JSON.toJSONString(topicAudit);
            log.info("发送消息{}", topicAuditJson);
            rabbitService.sendMessage(RabbitConstant.TOPIC_AUDIT_EXCHANGE, RabbitConstant.TOPIC_AUDIT_ROUTING_KEY_NAME, topicAuditJson);
        }

        // 插入到专题题目关系表中
        TopicSubjectTopic topicSubjectTopic = new TopicSubjectTopic();
        topicSubjectTopic.setTopicId(topic.getId());
        topicSubjectTopic.setSubjectId(topicDto.getSubjectId());
        topicSubjectTopicMapper.insert(topicSubjectTopic);

        // 更新专题数量
        topicSubject.setTopicCount(topicSubject.getTopicCount() + 1);
        topicSubjectService.updateById(topicSubject);
        List<TopicLabelTopic> topicLabelTopics = new ArrayList<>();
        for (TopicLabel topicLabel : topicLabels) {
            // 插入到题目标签关系表中
            TopicLabelTopic topicLabelTopic = new TopicLabelTopic();
            topicLabelTopic.setTopicId(topic.getId());
            topicLabelTopic.setLabelId(topicLabel.getId());
            topicLabelTopics.add(topicLabelTopic);
            topicLabel.setUseCount(topicLabel.getUseCount() + 1);
        }
        topicLabelTopicMapper.insertBatch(topicLabelTopics);
        topicLabelService.updateBatchById(topicLabels);
    }

    /**
     * 删除题目
     *
     * @param ids
     */
    @Override
    @Transactional
    public void deleteTopic(Long[] ids) {
        if (ids == null || ids.length == 0) {
            throw new TopicException(ResultCodeEnum.TOPIC_DELETE_IS_NULL);
        }
        for (Long id : ids) {
            Topic topic = topicMapper.selectById(id);
            if (ObjectUtil.isNull(topic)) {
                throw new TopicException(ResultCodeEnum.TOPIC_DELETE_IS_NULL);
            }
            // 删除题目
            topicMapper.deleteById(id);
            // 删除题目标签，专题关系表的数据
            LambdaQueryWrapper<TopicSubjectTopic> topicSubjectTopicLambdaQueryWrapper = new LambdaQueryWrapper<>();
            topicSubjectTopicLambdaQueryWrapper.eq(TopicSubjectTopic::getTopicId, id);
            // 查询题目-专题关系表
            TopicSubjectTopic topicSubjectTopic = topicSubjectTopicMapper.selectOne(topicSubjectTopicLambdaQueryWrapper);
            if (ObjectUtil.isNotNull(topicSubjectTopic)) {
                // 删除数据
                TopicSubject topicSubject = topicSubjectService.getById(topicSubjectTopic.getSubjectId());
                if (ObjectUtil.isNotNull(topicSubject)) {
                    // 更新专题下题目总数
                    topicSubject.setTopicCount(topicSubject.getTopicCount() - 1);
                    topicSubjectService.updateById(topicSubject);
                }
                topicSubjectTopicMapper.deleteById(topicSubjectTopic.getId());
            }
            // 删除题目专题关系表
            topicSubjectTopicMapper.deleteById(topicSubjectTopic);

            // 查询题目标签关系表
            LambdaQueryWrapper<TopicLabelTopic> topicLabelTopicLambdaQueryWrapper = new LambdaQueryWrapper<>();
            topicLabelTopicLambdaQueryWrapper.eq(TopicLabelTopic::getTopicId, id);
            List<TopicLabelTopic> topicLabelTopics = topicLabelTopicMapper.selectList(topicLabelTopicLambdaQueryWrapper);
            if (!CollectionUtils.isEmpty(topicLabelTopics)) {
                List<Long> labelIds = topicLabelTopics.stream().map(TopicLabelTopic::getLabelId).toList();
                // 查询使用标签
                List<TopicLabel> topicLabels = topicLabelService.listByIds(labelIds);
                // 更新标签被使用次数
                topicLabels.forEach(topicLabel -> {
                    topicLabel.setUseCount(topicLabel.getUseCount() - 1);
                });
                topicLabelService.updateBatchById(topicLabels);
            }
            // 删除题目标签关系表
            topicLabelTopicMapper.delete(topicLabelTopicLambdaQueryWrapper);
        }
    }

    /**
     * 修改题目
     * @param topicDto
     */
    @Override
    public void updateTopic(TopicDto topicDto) {
        // 根据id查询
        Topic oldTopic = topicMapper.selectById(topicDto.getId());
        if (oldTopic == null) {
            throw new TopicException(ResultCodeEnum.TOPIC_UPDATE_IS_NULL);
        }
        // 查询专题
        TopicSubject topicSubject = topicSubjectService.getById(topicDto.getSubjectId());
        // 判断
        if (topicSubject == null) {
            throw new TopicException(ResultCodeEnum.SUBJECT_NOT_EXIST);
        }
        // 查询标签
        List<TopicLabel> topicLabels = topicLabelService.listByIds(topicDto.getLabelIds());
        if (CollectionUtils.isEmpty(topicLabels)) {
            throw new TopicException(ResultCodeEnum.LABEL_NOT_EXIST);
        }
        StringBuilder labelNames = new StringBuilder();
        for (TopicLabel topicLabel : topicLabels) {
            labelNames.append(topicLabel.getLabelName());
            // 拼接最后一个不要拼接
            if (topicLabels.size() != topicLabels.indexOf(topicLabel) + 1) {
                labelNames.append(":");
            }
        }
        // 修改题目
        Topic topic = new Topic();
        BeanUtils.copyProperties(topicDto, topic);
        // 获取当前用户登录名称
        String username = SecurityUtils.getCurrentName();
        // 每日题目只能有9题
        if (topic.getIsEveryday() == 1) {
            LambdaQueryWrapper<Topic> topicLambdaQueryWrapper1 = new LambdaQueryWrapper<>();
            topicLambdaQueryWrapper1.eq(Topic::getIsEveryday, 1);
            List<Topic> topics = topicMapper.selectList(topicLambdaQueryWrapper1);
            if (CollectionUtils.isNotEmpty(topics) && topics.size() >= 9) {
                throw new TopicException(ResultCodeEnum.TOPIC_EVERYDAY_ERROR);
            }
        }

        // 获取当前用户id
        Long currentId = SecurityUtils.getCurrentId();
        String role = SecurityUtils.getCurrentRole();
        if (role.equals(RoleEnum.ADMIN.getRoleKey())) {
            // 是开发者不需要审核
            topic.setStatus(StatusEnums.NORMAL.getCode());
        } else {
            // 不是开发者需要审核
            topic.setStatus(StatusEnums.AUDITING.getCode());
            // 异步发送信息给AI审核
            TopicAudit topicAudit = new TopicAudit();
            topicAudit.setId(topicDto.getId());
            topicAudit.setTopicName(topicDto.getTopicName());
            topicAudit.setAccount(username);
            topicAudit.setAnswer(topicDto.getAnswer());
            topicAudit.setUserId(currentId);
            topicAudit.setTopicSubjectName(topicSubject.getSubjectName());
            topicAudit.setTopicLabelName(labelNames.toString());
            String topicAuditJson = JSON.toJSONString(topicAudit);
            log.info("发送消息{}", topicAuditJson);
            rabbitService.sendMessage(RabbitConstant.TOPIC_AUDIT_EXCHANGE, RabbitConstant.TOPIC_AUDIT_ROUTING_KEY_NAME, topicAuditJson);
        }
        topic.setFailMsg("");
        // 开始更新
        topicMapper.updateById(topic);

        // 查询专题题目关系表
        LambdaQueryWrapper<TopicSubjectTopic> topicSubjectTopicLambdaQueryWrapper = new LambdaQueryWrapper<>();
        topicSubjectTopicLambdaQueryWrapper.eq(TopicSubjectTopic::getTopicId, oldTopic.getId());
        TopicSubjectTopic topicSubjectTopicDb = topicSubjectTopicMapper.selectOne(topicSubjectTopicLambdaQueryWrapper);
        if (topicSubjectTopicDb != null) {
            // 查询专题
            TopicSubject topicSubjectDb = topicSubjectService.getById(topicSubjectTopicDb.getSubjectId());
            if (topicSubjectDb != null) {
                // 更新专题数量
                topicSubjectDb.setTopicCount(topicSubjectDb.getTopicCount() - 1);
                topicSubjectService.updateById(topicSubjectDb);
            }
            // 删除
            topicSubjectTopicMapper.deleteById(topicSubjectTopicDb);
        }
        // 插入到专题题目关系表中
        TopicSubjectTopic topicSubjectTopic = new TopicSubjectTopic();
        topicSubjectTopic.setTopicId(topic.getId());
        topicSubjectTopic.setSubjectId(topicDto.getSubjectId());
        topicSubjectTopicMapper.insert(topicSubjectTopic);
        // 更新次数
        topicSubject.setTopicCount(topicSubject.getTopicCount() + 1);
        topicSubjectService.updateById(topicSubject);

        // 查询标签题目关系表
        LambdaQueryWrapper<TopicLabelTopic> topicLabelTopicLambdaQueryWrapper = new LambdaQueryWrapper<>();
        topicLabelTopicLambdaQueryWrapper.eq(TopicLabelTopic::getTopicId, oldTopic.getId());
        // 原本的标签
        List<TopicLabelTopic> topicLabelTopics = topicLabelTopicMapper.selectList(topicLabelTopicLambdaQueryWrapper);
        if (CollectionUtils.isNotEmpty(topicLabelTopics)) {
            List<Long> labelIds = topicLabelTopics.stream().map(TopicLabelTopic::getLabelId).toList();
            List<TopicLabel> oldLabels = topicLabelService.listByIds(labelIds);
            oldLabels.forEach(oldLabel -> {
                oldLabel.setUseCount(oldLabel.getUseCount() - 1);
            });
            topicLabelService.updateBatchById(oldLabels);
            topicLabelTopicMapper.delete(topicLabelTopicLambdaQueryWrapper);
        }

        // 插入到题目标签关系表中
        List<TopicLabelTopic> newLabels = new ArrayList<>();
        for (TopicLabel topicLabel : topicLabels) {
            // 插入到题目标签关系表中
            TopicLabelTopic topicLabelTopic = new TopicLabelTopic();
            topicLabelTopic.setTopicId(topic.getId());
            topicLabelTopic.setLabelId(topicLabel.getId());
            newLabels.add(topicLabelTopic);
            topicLabel.setUseCount(topicLabel.getUseCount() + 1);
        }
        topicLabelTopicMapper.insertBatch(newLabels);
        topicLabelService.updateBatchById(topicLabels);
    }

    /**
     * 生成ai答案
     * @param id
     */
    @Override
    public void generateAnswer(Long id) {
        // 查询一下这个题目存不存在
        Topic topicDb = topicMapper.selectById(id);
        if (topicDb == null) {
            throw new TopicException(ResultCodeEnum.TOPIC_GENERATE_ANSWER_ERROR);
        }
        // 发送消息给ai生成答案
        TopicAudit topicAudit = new TopicAudit();
        topicAudit.setTopicName(topicDb.getTopicName());
        topicAudit.setId(topicDb.getId());
        topicAudit.setUserId(SecurityUtils.getCurrentId());
        topicAudit.setAccount(SecurityUtils.getCurrentName());
        rabbitService.sendMessage(RabbitConstant.AI_ANSWER_EXCHANGE, RabbitConstant.AI_ANSWER_ROUTING_KEY_NAME, JSON.toJSONString(topicAudit));
    }

    /**
     * 保存ai答案
     * @param topic
     */
    @Override
    public void updateAiAnswer(Topic topic) {
        // 查询一下这个题目存不存在
        Topic topicDb = topicMapper.selectById(topic.getId());
        if (topicDb == null) {
            throw new TopicException(ResultCodeEnum.TOPIC_GENERATE_ANSWER_ERROR);
        }
        // 将答案封装
        topicDb.setAiAnswer(topic.getAiAnswer());
        topicMapper.updateById(topicDb);
    }

    /**
     * 根据题目id查询题目详情和标签
     *
     * @param id
     * @return
     */
    @Override
    public TopicDetailVo detail(Long id) {
        if (id == null) {
            return null;
        }
        Topic topic = topicMapper.selectById(id);
        if (topic == null) {
            return null;
        }
        // 查询题目对应标签信息
        LambdaQueryWrapper<TopicLabelTopic> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TopicLabelTopic::getTopicId, id);
        List<TopicLabelTopic> topicLabelTopics = topicLabelTopicMapper.selectList(queryWrapper);
        if (CollectionUtils.isEmpty(topicLabelTopics)) {
            return null;
        }
        // 根据标签id查询标签名称
        List<Long> labelIds = topicLabelTopics.stream().map(TopicLabelTopic::getLabelId).toList();
        List<String> labelNames = topicLabelService.getLabelNamesByIds(labelIds);
        TopicDetailVo topicDetailVo = new TopicDetailVo();
        BeanUtils.copyProperties(topic, topicDetailVo);
        topicDetailVo.setLabelNames(labelNames);
        // 查询这个题目id是否收藏
        boolean topicCollected = isTopicCollected(topic.getId());
        topicDetailVo.setIsCollected(topicCollected);
        return topicDetailVo;
    }

    // 查询是否收藏该题目
    private boolean isTopicCollected(Long topicId) {
        // 当前用户id
        Long userId = SecurityUtils.getCurrentId();
        // 查询redis
        String key = RedisConstant.USER_COLLECTIONS + userId;
        // 题目id
        String value = String.valueOf(topicId);
        // 查询用户收藏是否在缓存，如果存在则返回非 null
        Boolean hasKey = stringRedisTemplate.hasKey(key);
        if (hasKey) {
            // 再查询redis，如果redis没有就是没有
            Double scope = stringRedisTemplate.opsForZSet().score(key, value);
            return scope != null;
        } else {
            // 用户收藏缓存不存在，说明7天未登录，需要重建缓存
            LambdaQueryWrapper<TopicCollection> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(TopicCollection::getUserId, userId);
            List<TopicCollection> collections = topicCollectionMapper.selectList(queryWrapper);
            if (CollectionUtils.isNotEmpty(collections)) {
                // 数据库存在，缓存重建
                for (TopicCollection collection : collections) {
                    stringRedisTemplate.opsForZSet().add(key, String.valueOf(collection.getTopicId()), System.currentTimeMillis());
                }
                stringRedisTemplate.expire(RedisConstant.USER_COLLECTIONS + userId, RedisConstant.COLLECTIONS_EXPIRE_TIME, TimeUnit.DAYS);
            } else {
                // 数据库不存在，缓存空数据
                stringRedisTemplate.opsForZSet().add(key, "empty", 0);
                return false;
            }
        }
        return true;
    }

    /**
     * 计算用户刷题次数
     * @param topicRecordCountDto
     */
    @Override
    public void setCount(TopicRecordCountDto topicRecordCountDto) {
        // 校验参数
        if (topicRecordCountDto.getTopicId() == null || topicRecordCountDto.getSubjectId() == null) {
            return;
        }
        // 当前登录id
        Long userId = SecurityUtils.getCurrentId();
        // 获取用户身份
        String currentRole = SecurityUtils.getCurrentRole();
        String currentName;
        // 判断当前名称
        if (StrUtil.isEmpty(topicRecordCountDto.getNickname())) {
            // 获取当前登录名称
            currentName = SecurityUtils.getCurrentName();
        } else {
            currentName = topicRecordCountDto.getNickname();
        }
        // 当天日期
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        // 查询记录表
        LambdaQueryWrapper<TopicRecord> topicRecordLambdaQueryWrapper = new LambdaQueryWrapper<>();
        topicRecordLambdaQueryWrapper.eq(TopicRecord::getUserId, userId);
        topicRecordLambdaQueryWrapper.eq(TopicRecord::getTopicId, topicRecordCountDto.getTopicId());
        // 查询今日
        topicRecordLambdaQueryWrapper.eq(TopicRecord::getTopicTime, date);
        TopicRecord topicRecord = topicRecordMapper.selectOne(topicRecordLambdaQueryWrapper);
        if (topicRecord == null) {
            // 说明是第一次
            topicRecord = new TopicRecord();
            topicRecord.setTopicId(topicRecordCountDto.getTopicId());
            topicRecord.setSubjectId(topicRecordCountDto.getSubjectId());
            topicRecord.setCount(1L);
            topicRecord.setUserId(userId);
            topicRecord.setNickname(currentName);
            topicRecord.setTopicTime(new Date());
            topicRecordMapper.insert(topicRecord);
        } else {
            // 不是第一次刷这个题目
            topicRecord.setCount(topicRecord.getCount() + 1);
            topicRecord.setTopicTime(new Date());
            topicRecordMapper.updateById(topicRecord);
        }
        // 查询redis中是否有今日key
        Boolean hasKey = stringRedisTemplate.hasKey(RedisConstant.TOPIC_RANK_TODAY);
        if (hasKey) {
            // 存在今日，直接更新用户今日的做题总数
            stringRedisTemplate.opsForZSet().incrementScore(
                    RedisConstant.TOPIC_RANK_TODAY,
                    String.valueOf(userId),
                    1);
        } else {
            stringRedisTemplate.opsForZSet().add(RedisConstant.TOPIC_RANK_TODAY, String.valueOf(userId), 1);
        }
        // 查询redis中是否有改用户的总榜key
        Boolean aBoolean = stringRedisTemplate.hasKey(RedisConstant.TOPIC_RANK_TOTAL);
        if (aBoolean) {
            // 存在总榜，直接更新用户的做题总数
            stringRedisTemplate.opsForZSet().incrementScore(
                    RedisConstant.TOPIC_RANK_TOTAL,
                    String.valueOf(userId),
                    1);
        } else {
            // 不存在
            stringRedisTemplate.opsForZSet().add(RedisConstant.TOPIC_RANK_TOTAL, String.valueOf(userId), 1);
        }
        // 存储用户信息到Hash
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("nickname", currentName);
        userInfo.put("avatar", topicRecordCountDto.getAvatar());
        userInfo.put("role", currentRole);
        stringRedisTemplate.opsForHash().putAll(RedisConstant.USER_RANK + userId, userInfo);
    }

    /**
     * 获取答案
     * @param id
     * @return
     */
    @Override
    public TopicAnswerVo getAnswer(Long id) {
        if (id == null) {
            throw new TopicException(ResultCodeEnum.TOPIC_ANSWER_NOT_EXIST);
        }
        // 获取当前身份
        String role = SecurityUtils.getCurrentRole();
        Topic topic = topicMapper.selectById(id);
        if (topic == null) {
            throw new TopicException(ResultCodeEnum.TOPIC_ANSWER_NOT_EXIST);
        }
        if (topic.getIsMember() == 1) {
            // 题目是会员和管理员才能查看答案
            if (role.equals(RoleEnum.MEMBER.getRoleKey()) || role.equals(RoleEnum.ADMIN.getRoleKey())) {
                TopicAnswerVo topicAnswerVo = new TopicAnswerVo();
                topicAnswerVo.setAnswer(topic.getAnswer());
                topicAnswerVo.setAiAnswer(topic.getAiAnswer());
                return topicAnswerVo;
            } else {
                throw new TopicException(ResultCodeEnum.TOPIC_MEMBER_ERROR);
            }
        } else {
            TopicAnswerVo topicAnswerVo = new TopicAnswerVo();
            topicAnswerVo.setAnswer(topic.getAnswer());
            topicAnswerVo.setAiAnswer(topic.getAiAnswer());
            return topicAnswerVo;
        }
    }

    /**
     * 根据题目id收藏题目
     * @param id
     */
    @Override
    public void collection(Long id) {
        if (id == null) {
            throw new TopicException(ResultCodeEnum.TOPIC_COLLECTION_ERROR);
        }
        Long userId = SecurityUtils.getCurrentId(); // 获取当前用户ID

        // 查询是否已收藏
        LambdaQueryWrapper<TopicCollection> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TopicCollection::getTopicId, id)
                .eq(TopicCollection::getUserId, userId);
        boolean exist = topicCollectionMapper.exists(wrapper);
        if (exist) {
            // 已收藏，执行取消收藏
            int delete = topicCollectionMapper.delete(wrapper);
            if (delete <= 0) {
                throw new TopicException(ResultCodeEnum.TOPIC_COLLECTION_ERROR);
            }
            try {
                // 同步更新Redis缓存
                stringRedisTemplate.opsForZSet().remove(RedisConstant.USER_COLLECTIONS + userId, String.valueOf(id));
            } catch (Exception e) {
                throw new TopicException(ResultCodeEnum.TOPIC_COLLECTION_ERROR);
            }
        } else {
            // 未收藏，执行收藏
            TopicCollection newCollection = new TopicCollection();
            newCollection.setTopicId(id);
            newCollection.setUserId(userId);
            int insert = topicCollectionMapper.insert(newCollection);
            if (insert <= 0) {
                throw new TopicException(ResultCodeEnum.TOPIC_COLLECTION_ERROR);
            }
            try {
                // 同步更新Redis缓存
                stringRedisTemplate.opsForZSet().add(RedisConstant.USER_COLLECTIONS + userId, String.valueOf(id), System.currentTimeMillis());
            } catch (Exception e) {
                throw new TopicException(ResultCodeEnum.TOPIC_COLLECTION_ERROR);
            }
        }
    }

    /**
     * 查询收藏的题目
     * @return
     */
    @Override
    public List<TopicCollectionVo> collectionList() {
        // 获取当前用户id
        Long userId = SecurityUtils.getCurrentId();
        // 查询数据库
        LambdaQueryWrapper<TopicCollection> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TopicCollection::getUserId, userId);
        List<TopicCollection> topicCollections = topicCollectionMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(topicCollections)) {
            return null;
        }
        // 封装返回数据
        List<TopicCollectionVo> topicCollectionVoList = new ArrayList<>();
        // 遍历收藏表
        for (TopicCollection topicCollection : topicCollections) {
            TopicCollectionVo topicCollectionVo = new TopicCollectionVo();
            Topic topic = topicMapper.selectById(topicCollection.getTopicId());
            topicCollectionVo.setTopicName(topic.getTopicName());
            topicCollectionVo.setId(topic.getId());
            topicCollectionVo.setCollectionTime(topicCollection.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            // 查询专题
            LambdaQueryWrapper<TopicSubjectTopic> subjectQueryWrapper = new LambdaQueryWrapper<>();
            subjectQueryWrapper.eq(TopicSubjectTopic::getTopicId, topicCollection.getTopicId());
            TopicSubjectTopic topicSubjectTopic = topicSubjectTopicMapper.selectOne(subjectQueryWrapper);
            topicCollectionVo.setSubjectId(topicSubjectTopic.getSubjectId());
            // 查询标签
            LambdaQueryWrapper<TopicLabelTopic> labelQueryWrapper = new LambdaQueryWrapper<>();
            labelQueryWrapper.eq(TopicLabelTopic::getTopicId, topicCollection.getTopicId());
            List<TopicLabelTopic> topicLabelTopics = topicLabelTopicMapper.selectList(labelQueryWrapper);
            List<Long> labelIds = topicLabelTopics.stream().map(TopicLabelTopic::getLabelId).toList();
            List<String> labelNames = topicLabelService.getLabelNamesByIds(labelIds);
            topicCollectionVo.setLabelNames(labelNames);
            topicCollectionVoList.add(topicCollectionVo);
        }
        return topicCollectionVoList;
    }

    /**
     * 获取导出数据
     *
     * @param topicListDto
     * @param ids
     * @return
     */
    public List<TopicExcelExport> getExcelVo(TopicListDto topicListDto, Long[] ids) {
        // 是否有id
        if (ids != null && ids.length > 0 && ids[0] != 0) {
            // 根据id查询
            List<Topic> topics = topicMapper.selectBatchIds(Arrays.asList(ids));
            if (CollectionUtils.isEmpty(topics)) {
                throw new TopicException(ResultCodeEnum.EXPORT_ERROR);
            }
            return topics.stream().map(topic -> {
                TopicExcelExport topicExcelExport = new TopicExcelExport();
                BeanUtils.copyProperties(topic, topicExcelExport);
                // 状态特殊处理
                topicExcelExport.setStatus(StatusEnums.getMessageByCode(topic.getStatus()));
                // 处理专题
                // 查询专题题目关系表
                LambdaQueryWrapper<TopicSubjectTopic> topicSubjectTopicLambdaQueryWrapper = new LambdaQueryWrapper<>();
                topicSubjectTopicLambdaQueryWrapper.eq(TopicSubjectTopic::getTopicId, topic.getId());
                TopicSubjectTopic topicSubjectTopic = topicSubjectTopicMapper.selectOne(topicSubjectTopicLambdaQueryWrapper);
                if (topicSubjectTopic != null) {
                    // 查询专题
                    TopicSubject topicSubjectDb = topicSubjectService.getById(topicSubjectTopic.getSubjectId());
                    if (topicSubjectDb != null) {
                        topicExcelExport.setSubjectName(topicSubjectDb.getSubjectName());
                    }
                }
                // 处理标签
                // 查询标签题目关系表
                LambdaQueryWrapper<TopicLabelTopic> topicLabelTopicLambdaQueryWrapper = new LambdaQueryWrapper<>();
                topicLabelTopicLambdaQueryWrapper.eq(TopicLabelTopic::getTopicId, topic.getId());
                List<TopicLabelTopic> topicLabelTopics = topicLabelTopicMapper.selectList(topicLabelTopicLambdaQueryWrapper);
                StringBuilder labelNames = new StringBuilder();
                if (!CollectionUtils.isEmpty(topicLabelTopics)) {
                    for (TopicLabelTopic topicLabelTopic : topicLabelTopics) {
                        // 查询标签
                        TopicLabel topicLabelDb = topicLabelService.getById(topicLabelTopic.getLabelId());
                        if (topicLabelDb != null) {
                            labelNames.append(topicLabelDb.getLabelName());
                            // 拼接最后一个不要拼接
                            if (topicLabelTopics.size() != topicLabelTopics.indexOf(topicLabelTopic) + 1) {
                                labelNames.append(":");
                            }
                        }
                    }
                }
                topicExcelExport.setLabelName(labelNames.toString());
                return topicExcelExport;
            }).toList();
        } else {
            Map<String, Object> map = topicList(topicListDto);
            if (map.get("rows") == null) {
                throw new TopicException(ResultCodeEnum.EXPORT_ERROR);
            }
            List<TopicVo> topicVoList = (List<TopicVo>) map.get("rows");
            // 封装返回数据
            return topicVoList.stream().map(item -> {
                TopicExcelExport topicExcelExport = new TopicExcelExport();
                BeanUtils.copyProperties(item, topicExcelExport);
                String labelNames = String.join(":", item.getLabels());
                topicExcelExport.setLabelName(labelNames);
                topicExcelExport.setSubjectName(item.getSubject());
                topicExcelExport.setStatus(StatusEnums.getMessageByCode(item.getStatus()));
                return topicExcelExport;
            }).toList();
        }
    }

    /**
     * 会员导入
     *
     * @param excelVoList
     * @param updateSupport
     * @return
     */
    @Transactional
    public String memberImport(List<TopicMemberExcel> excelVoList, Boolean updateSupport) {
        // 获取当前用户登录名称
        String username = SecurityUtils.getCurrentName();
        // 校验
        if (CollectionUtils.isEmpty(excelVoList)) {
            throw new TopicException(ResultCodeEnum.IMPORT_ERROR);
        }
        // 计算成功的数量
        int successNum = 0;
        // 计算失败的数量
        int failureNum = 0;
        // 拼接成功消息
        StringBuilder successMsg = new StringBuilder();
        // 拼接错误消息
        StringBuilder failureMsg = new StringBuilder();
        Long currentId = SecurityUtils.getCurrentId();
        String currentRole = SecurityUtils.getCurrentRole();
        // 遍历
        for (TopicMemberExcel topicExcel : excelVoList) {
            try {
                // 查询这个题目是否存在
                LambdaQueryWrapper<Topic> topicLambdaQueryWrapper = new LambdaQueryWrapper<>();
                topicLambdaQueryWrapper.eq(Topic::getTopicName, topicExcel.getTopicName());
                Topic topic = topicMapper.selectOne(topicLambdaQueryWrapper);
                if (ObjectUtil.isEmpty(topic)) {
                    // 不存在插入
                    Topic topicDb = new Topic();
                    BeanUtils.copyProperties(topicExcel, topicDb);
                    topicDb.setCreateBy(username);
                    if (topicExcel.getSubjectName() == null) {
                        throw new TopicException(ResultCodeEnum.TOPIC_SUBJECT_IS_NULL);
                    }
                    if (topicExcel.getLabelName() == null) {
                        throw new TopicException(ResultCodeEnum.TOPIC_LABEL_IS_NULL);
                    }
                    // 查询专题
                    LambdaQueryWrapper<TopicSubject> topicSubjectLambdaQueryWrapper = new LambdaQueryWrapper<>();
                    topicSubjectLambdaQueryWrapper.eq(TopicSubject::getSubjectName, topicExcel.getSubjectName());
                    TopicSubject topicSubjectDb = topicSubjectService.getOne(topicSubjectLambdaQueryWrapper);
                    if (ObjectUtil.isNull(topicSubjectDb)) {
                        throw new TopicException(ResultCodeEnum.TOPIC_SUBJECT_IS_NULL);
                    }
                    // 判断是否是开发者
                    if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
                        // 是开发者不需要审核
                        topicDb.setStatus(StatusEnums.NORMAL.getCode());
                        topicMapper.insert(topicDb);
                    } else {
                        // 不是开发者进行审核
                        topicDb.setStatus(StatusEnums.AUDITING.getCode());
                        topicMapper.insert(topicDb);
                    }

                    topicSubjectDb.setTopicCount(topicSubjectDb.getTopicCount() + 1);
                    topicSubjectService.updateById(topicSubjectDb);
                    // 添加到题目关联专题表中
                    TopicSubjectTopic topicSubject = new TopicSubjectTopic();
                    topicSubject.setTopicId(topicDb.getId());
                    topicSubject.setSubjectId(topicSubjectDb.getId());
                    topicSubjectTopicMapper.insert(topicSubject);

                    // 将标签分割 标签1:标签2:标签3
                    String[] labelNames = topicExcel.getLabelName().split(":");
                    // 校验labelNames是否存在相同的标签
                    for (int i = 0; i < labelNames.length; i++) {
                        for (int j = i + 1; j < labelNames.length; j++) {
                            if (labelNames[i].equals(labelNames[j])) {
                                throw new TopicException(ResultCodeEnum.TOPIC_LABEL_IS_NULL);
                            }
                            if (StrUtil.isEmpty(labelNames[i])) {
                                throw new TopicException(ResultCodeEnum.TOPIC_LABEL_IS_NULL);
                            }
                        }
                    }
                    List<TopicLabel> labels = new ArrayList<>();
                    List<TopicLabelTopic> topicLabelTopics = new ArrayList<>();
                    for (String labelName : labelNames) {
                        // 根据标签名称查询标签
                        LambdaQueryWrapper<TopicLabel> topicLabelLambdaQueryWrapper = new LambdaQueryWrapper<>();
                        topicLabelLambdaQueryWrapper.eq(TopicLabel::getLabelName, labelName);
                        TopicLabel topicLabelDb = topicLabelService.getOne(topicLabelLambdaQueryWrapper);
                        if (topicLabelDb == null) {
                            throw new TopicException(ResultCodeEnum.TOPIC_LABEL_IS_NULL);
                        }
                        topicLabelDb.setUseCount(topicLabelDb.getUseCount() + 1);
                        labels.add(topicLabelDb);
                        // 添加到题目标签关系表中
                        TopicLabelTopic topicLabelTopic = new TopicLabelTopic();
                        topicLabelTopic.setTopicId(topicDb.getId());
                        topicLabelTopic.setLabelId(topicLabelDb.getId());
                        topicLabelTopics.add(topicLabelTopic);
                    }
                    // 有标签修改
                    topicLabelService.updateBatchById(labels);
                    // 修改关系表
                    topicLabelTopicMapper.insertBatch(topicLabelTopics);
                    // 判断是否是开发者
                    if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
                    } else {
                        // 异步发送消息给AI审核
                        TopicAudit topicAudit = new TopicAudit();
                        topicAudit.setTopicName(topicExcel.getTopicName());
                        topicAudit.setAnswer(topicExcel.getAnswer());
                        topicAudit.setAccount(username);
                        topicAudit.setUserId(currentId);
                        topicAudit.setTopicSubjectName(topicExcel.getSubjectName());
                        topicAudit.setTopicLabelName(topicExcel.getLabelName());
                        topicAudit.setId(topicDb.getId());
                        // 转换json
                        String topicAuditJson = JSON.toJSONString(topicAudit);
                        rabbitService.sendMessage(RabbitConstant.TOPIC_AUDIT_EXCHANGE, RabbitConstant.TOPIC_AUDIT_ROUTING_KEY_NAME, topicAuditJson);
                    }
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("-题目：").append(topicDb.getTopicName()).append("-导入成功");
                } else if (updateSupport) {
                    if (topicExcel.getSubjectName() == null) {
                        throw new TopicException(ResultCodeEnum.TOPIC_SUBJECT_IS_NULL);
                    }
                    if (topicExcel.getLabelName() == null) {
                        throw new TopicException(ResultCodeEnum.TOPIC_LABEL_IS_NULL);
                    }
                    // 将标签分割 标签1:标签2:标签3
                    String[] labelNames = topicExcel.getLabelName().split(":");
                    // 校验labelNames是否存在相同的标签
                    for (int i = 0; i < labelNames.length; i++) {
                        for (int j = i + 1; j < labelNames.length; j++) {
                            if (labelNames[i].equals(labelNames[j])) {
                                throw new TopicException(ResultCodeEnum.TOPIC_LABEL_IS_NULL);
                            }
                        }
                    }
                    // 查询专题题目关系表
                    LambdaQueryWrapper<TopicSubjectTopic> topicSubjectTopicLambdaQueryWrapper = new LambdaQueryWrapper<>();
                    topicSubjectTopicLambdaQueryWrapper.eq(TopicSubjectTopic::getTopicId, topic.getId());
                    TopicSubjectTopic topicSubjectTopic = topicSubjectTopicMapper.selectOne(topicSubjectTopicLambdaQueryWrapper);
                    if (topicSubjectTopic != null) {
                        // 查询专题
                        TopicSubject topicSubject = topicSubjectService.getById(topicSubjectTopic.getSubjectId());
                        if (topicSubject != null) {
                            // 判断数据库的专题和当前要修改的专题是否一致
                            if (!topicSubject.getSubjectName().equals(topicExcel.getSubjectName())) {
                                // 不一致更新当前专题被使用次数-1
                                topicSubject.setTopicCount(topicSubject.getTopicCount() - 1);
                                topicSubjectService.updateById(topicSubject);
                                // 然后查询当前要添加的专题
                                LambdaQueryWrapper<TopicSubject> topicSubjectLambdaQueryWrapper = new LambdaQueryWrapper<>();
                                topicSubjectLambdaQueryWrapper.eq(TopicSubject::getSubjectName, topicExcel.getSubjectName());
                                TopicSubject topicSubjectDb = topicSubjectService.getOne(topicSubjectLambdaQueryWrapper);
                                if (topicSubjectDb != null) {
                                    topicSubjectDb.setTopicCount(topicSubjectDb.getTopicCount() + 1);
                                    topicSubjectService.updateById(topicSubjectDb);
                                    // 添加到题目关联专题表中
                                    TopicSubjectTopic topicSubjectTopicDb = new TopicSubjectTopic();
                                    topicSubjectTopicDb.setSubjectId(topicSubjectDb.getId());
                                    topicSubjectTopicDb.setTopicId(topic.getId());
                                    topicSubjectTopicMapper.insert(topicSubjectTopicDb);
                                }
                                // 删除以前的题目专题关联关系
                                topicSubjectTopicMapper.deleteById(topicSubjectTopic);
                            }
                        }
                    }

                    // 查询标签题目关系表
                    LambdaQueryWrapper<TopicLabelTopic> topicLabelTopicLambdaQueryWrapper = new LambdaQueryWrapper<>();
                    topicLabelTopicLambdaQueryWrapper.eq(TopicLabelTopic::getTopicId, topic.getId());
                    List<TopicLabelTopic> topicLabelTopics = topicLabelTopicMapper.selectList(topicLabelTopicLambdaQueryWrapper);
                    // 校验一下
                    if (CollectionUtils.isNotEmpty(topicLabelTopics)) {
                        // 获取所有的标签id
                        List<Long> labelIds = topicLabelTopics.stream()
                                .map(TopicLabelTopic::getLabelId)
                                .toList();
                        // 查询标签
                        List<TopicLabel> topicLabels = topicLabelService.listByIds(labelIds);
                        // 更新所有标签次数-1
                        topicLabels.forEach(topicLabel -> {
                            topicLabel.setUseCount(topicLabel.getUseCount() - 1);
                        });
                        topicLabelService.updateBatchById(topicLabels);
                        // 先删除题目关系表
                        topicLabelTopicMapper.delete(topicLabelTopicLambdaQueryWrapper);

                        // 校验要修改的标签名称是否与以前的名称是否一样
                        List<String> labelsName = Arrays.asList(labelNames);
                        // 然后查询当前要添加的标签
                        LambdaQueryWrapper<TopicLabel> topicLabelLambdaQueryWrapper = new LambdaQueryWrapper<>();
                        topicLabelLambdaQueryWrapper.in(TopicLabel::getLabelName, labelsName);
                        List<TopicLabel> newLabels = topicLabelService.list(topicLabelLambdaQueryWrapper);
                        if (CollectionUtils.isNotEmpty(newLabels)) {
                            newLabels.forEach(topicLabel -> topicLabel.setUseCount(topicLabel.getUseCount() + 1));
                            for (TopicLabel topicLabel : newLabels) {
                                // 添加到题目关联标签表中
                                TopicLabelTopic topicLabelTopicDb = new TopicLabelTopic();
                                topicLabelTopicDb.setLabelId(topicLabel.getId());
                                topicLabelTopicDb.setTopicId(topic.getId());
                                topicLabelTopicMapper.insert(topicLabelTopicDb);
                            }
                        }
                    }


                    // 判断是否是开发者
                    if (currentId == 1L) {
                        // 是开发者不需要审核
                        topic.setStatus(StatusEnums.NORMAL.getCode());
                    } else {
                        // 不是开发者进行审核
                        topic.setStatus(StatusEnums.AUDITING.getCode());
                        // 异步发送消息给AI审核
                        TopicAudit topicAudit = new TopicAudit();
                        topicAudit.setTopicName(topicExcel.getTopicName());
                        topicAudit.setAnswer(topicExcel.getAnswer());
                        topicAudit.setAccount(username);
                        topicAudit.setUserId(currentId);
                        topicAudit.setTopicSubjectName(topicExcel.getSubjectName());
                        topicAudit.setTopicLabelName(topicExcel.getLabelName());
                        topicAudit.setId(topic.getId());
                        // 转换json
                        String topicAuditJson = JSON.toJSONString(topicAudit);
                        rabbitService.sendMessage(RabbitConstant.TOPIC_AUDIT_EXCHANGE, RabbitConstant.TOPIC_AUDIT_ROUTING_KEY_NAME, topicAuditJson);
                    }
                    topic.setFailMsg("");
                    // 更新
                    BeanUtils.copyProperties(topicExcel, topic);
                    topicMapper.updateById(topic);

                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("-题目：").append(topic.getTopicName()).append("-更新成功");


                } else {
                    // 已存在
                    failureNum++;
                    failureMsg.append("<br/>").append(failureNum).append("-题目：").append(topic.getTopicName()).append("-已存在");
                }
            } catch (Exception e) {
                failureNum++;
                String msg = "<br/>" + failureNum + "-题目： " + topicExcel.getTopicName() + " 导入失败：";
                failureMsg.append(msg).append(e.getMessage());
                log.error(msg, e);
            }
        }
        if (failureNum > 0) {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
            throw new TopicException(failureMsg.toString());
        } else {
            successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
        }
        return successMsg.toString();
    }

    /**
     * 管理员导入
     *
     * @param excelVoList
     * @param updateSupport
     * @return
     */
    @Transactional
    public String adminImport(List<TopicExcel> excelVoList, Boolean updateSupport) {
        // 获取当前用户登录名称
        String username = SecurityUtils.getCurrentName();
        // 校验
        if (CollectionUtils.isEmpty(excelVoList)) {
            throw new TopicException(ResultCodeEnum.IMPORT_ERROR);
        }
        // 计算成功的数量
        int successNum = 0;
        // 计算失败的数量
        int failureNum = 0;
        // 拼接成功消息
        StringBuilder successMsg = new StringBuilder();
        // 拼接错误消息
        StringBuilder failureMsg = new StringBuilder();
        Long currentId = SecurityUtils.getCurrentId();
        String currentRole = SecurityUtils.getCurrentRole();
        // 遍历
        for (TopicExcel topicExcel : excelVoList) {
            try {
                // 查询这个题目是否存在
                LambdaQueryWrapper<Topic> topicLambdaQueryWrapper = new LambdaQueryWrapper<>();
                topicLambdaQueryWrapper.eq(Topic::getTopicName, topicExcel.getTopicName());
                Topic topic = topicMapper.selectOne(topicLambdaQueryWrapper);
                if (ObjectUtil.isNull(topic)) {
                    // 不存在插入
                    Topic topicDb = new Topic();
                    BeanUtils.copyProperties(topicExcel, topicDb);
                    topicDb.setCreateBy(username);
                    if (topicExcel.getSubjectName() == null) {
                        throw new TopicException(ResultCodeEnum.TOPIC_SUBJECT_IS_NULL);
                    }
                    if (topicExcel.getLabelName() == null) {
                        throw new TopicException(ResultCodeEnum.TOPIC_LABEL_IS_NULL);
                    }
                    // 查询专题
                    LambdaQueryWrapper<TopicSubject> topicSubjectLambdaQueryWrapper = new LambdaQueryWrapper<>();
                    topicSubjectLambdaQueryWrapper.eq(TopicSubject::getSubjectName, topicExcel.getSubjectName());
                    TopicSubject topicSubjectDb = topicSubjectService.getOne(topicSubjectLambdaQueryWrapper);
                    if (ObjectUtil.isNull(topicSubjectDb)) {
                        throw new TopicException(ResultCodeEnum.TOPIC_SUBJECT_IS_NULL);
                    }
                    // 将标签分割 标签1:标签2:标签3
                    String[] labelNames = topicExcel.getLabelName().split(":");
                    // 校验labelNames是否存在相同的标签
                    for (int i = 0; i < labelNames.length; i++) {
                        for (int j = i + 1; j < labelNames.length; j++) {
                            if (labelNames[i].equals(labelNames[j])) {
                                throw new TopicException(ResultCodeEnum.TOPIC_LABEL_IS_NULL);
                            }
                        }
                    }
                    // 判断是否是开发者
                    if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
                        // 是开发者不需要审核
                        topicDb.setStatus(StatusEnums.NORMAL.getCode());
                        topicMapper.insert(topicDb);
                    }
                    List<TopicLabel> labels = new ArrayList<>();
                    List<TopicLabelTopic> topicLabelTopics = new ArrayList<>();
                    for (String labelName : labelNames) {
                        // 根据标签名称查询标签
                        LambdaQueryWrapper<TopicLabel> topicLabelLambdaQueryWrapper = new LambdaQueryWrapper<>();
                        topicLabelLambdaQueryWrapper.eq(TopicLabel::getLabelName, labelName);
                        TopicLabel topicLabelDb = topicLabelService.getOne(topicLabelLambdaQueryWrapper);
                        if (topicLabelDb == null) {
                            throw new TopicException(ResultCodeEnum.TOPIC_LABEL_IS_NULL);
                        }
                        topicLabelDb.setUseCount(topicLabelDb.getUseCount() + 1);
                        labels.add(topicLabelDb);
                        // 添加到题目标签关系表中
                        TopicLabelTopic topicLabelTopic = new TopicLabelTopic();
                        topicLabelTopic.setTopicId(topicDb.getId());
                        topicLabelTopic.setLabelId(topicLabelDb.getId());
                        topicLabelTopics.add(topicLabelTopic);
                    }
                    // 有标签修改
                    topicLabelService.updateBatchById(labels);
                    // 修改关系表
                    topicLabelTopicMapper.insertBatch(topicLabelTopics);
                    topicSubjectDb.setTopicCount(topicSubjectDb.getTopicCount() + 1);
                    topicSubjectService.updateById(topicSubjectDb);
                    // 添加到题目关联专题表中
                    TopicSubjectTopic topicSubject = new TopicSubjectTopic();
                    topicSubject.setTopicId(topicDb.getId());
                    topicSubject.setSubjectId(topicSubjectDb.getId());
                    topicSubjectTopicMapper.insert(topicSubject);
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("-题目：").append(topicDb.getTopicName()).append("-导入成功");
                } else if (updateSupport) {
                    if (topicExcel.getSubjectName() == null) {
                        throw new TopicException(ResultCodeEnum.TOPIC_SUBJECT_IS_NULL);
                    }
                    if (topicExcel.getLabelName() == null) {
                        throw new TopicException(ResultCodeEnum.TOPIC_LABEL_IS_NULL);
                    }
                    // 将标签分割 标签1:标签2:标签3
                    String[] labelNames = topicExcel.getLabelName().split(":");
                    // 校验labelNames是否存在相同的标签
                    for (int i = 0; i < labelNames.length; i++) {
                        for (int j = i + 1; j < labelNames.length; j++) {
                            if (labelNames[i].equals(labelNames[j])) {
                                throw new TopicException(ResultCodeEnum.TOPIC_LABEL_IS_NULL);
                            }
                        }
                    }
                    // 查询专题题目关系表
                    LambdaQueryWrapper<TopicSubjectTopic> topicSubjectTopicLambdaQueryWrapper = new LambdaQueryWrapper<>();
                    topicSubjectTopicLambdaQueryWrapper.eq(TopicSubjectTopic::getTopicId, topic.getId());
                    TopicSubjectTopic topicSubjectTopic = topicSubjectTopicMapper.selectOne(topicSubjectTopicLambdaQueryWrapper);
                    if (topicSubjectTopic != null) {
                        // 查询专题
                        TopicSubject topicSubject = topicSubjectService.getById(topicSubjectTopic.getSubjectId());
                        if (topicSubject != null) {
                            // 判断数据库的专题和当前要修改的专题是否一致
                            if (!topicSubject.getSubjectName().equals(topicExcel.getSubjectName())) {
                                // 不一致更新当前专题被使用次数-1
                                topicSubject.setTopicCount(topicSubject.getTopicCount() - 1);
                                topicSubjectService.updateById(topicSubject);
                                // 然后查询当前要添加的专题
                                LambdaQueryWrapper<TopicSubject> topicSubjectLambdaQueryWrapper = new LambdaQueryWrapper<>();
                                topicSubjectLambdaQueryWrapper.eq(TopicSubject::getSubjectName, topicExcel.getSubjectName());
                                TopicSubject topicSubjectDb = topicSubjectService.getOne(topicSubjectLambdaQueryWrapper);
                                if (topicSubjectDb != null) {
                                    topicSubjectDb.setTopicCount(topicSubjectDb.getTopicCount() + 1);
                                    topicSubjectService.updateById(topicSubjectDb);
                                    // 添加到题目关联专题表中
                                    TopicSubjectTopic topicSubjectTopicDb = new TopicSubjectTopic();
                                    topicSubjectTopicDb.setSubjectId(topicSubjectDb.getId());
                                    topicSubjectTopicDb.setTopicId(topic.getId());
                                    topicSubjectTopicMapper.insert(topicSubjectTopicDb);
                                }
                                // 删除以前的题目专题关联关系
                                topicSubjectTopicMapper.deleteById(topicSubjectTopic);
                            }
                        }
                    }

                    // 查询标签题目关系表
                    LambdaQueryWrapper<TopicLabelTopic> topicLabelTopicLambdaQueryWrapper = new LambdaQueryWrapper<>();
                    topicLabelTopicLambdaQueryWrapper.eq(TopicLabelTopic::getTopicId, topic.getId());
                    List<TopicLabelTopic> topicLabelTopics = topicLabelTopicMapper.selectList(topicLabelTopicLambdaQueryWrapper);
                    // 校验一下
                    if (CollectionUtils.isNotEmpty(topicLabelTopics)) {
                        // 获取所有的标签id
                        List<Long> labelIds = topicLabelTopics.stream()
                                .map(TopicLabelTopic::getLabelId)
                                .toList();
                        // 查询标签
                        List<TopicLabel> topicLabels = topicLabelService.listByIds(labelIds);
                        // 更新所有标签次数-1
                        topicLabels.forEach(topicLabel -> {
                            topicLabel.setUseCount(topicLabel.getUseCount() - 1);
                        });
                        topicLabelService.updateBatchById(topicLabels);
                        // 先删除题目关系表
                        topicLabelTopicMapper.delete(topicLabelTopicLambdaQueryWrapper);

                        // 校验要修改的标签名称是否与以前的名称是否一样
                        List<String> labelsName = Arrays.asList(labelNames);
                        // 然后查询当前要添加的标签
                        LambdaQueryWrapper<TopicLabel> topicLabelLambdaQueryWrapper = new LambdaQueryWrapper<>();
                        topicLabelLambdaQueryWrapper.in(TopicLabel::getLabelName, labelsName);
                        List<TopicLabel> newLabels = topicLabelService.list(topicLabelLambdaQueryWrapper);
                        if (CollectionUtils.isNotEmpty(newLabels)) {
                            newLabels.forEach(topicLabel -> topicLabel.setUseCount(topicLabel.getUseCount() + 1));
                            for (TopicLabel topicLabel : newLabels) {
                                // 添加到题目关联标签表中
                                TopicLabelTopic topicLabelTopicDb = new TopicLabelTopic();
                                topicLabelTopicDb.setLabelId(topicLabel.getId());
                                topicLabelTopicDb.setTopicId(topic.getId());
                                topicLabelTopicMapper.insert(topicLabelTopicDb);
                            }
                        }
                    }

                    // 判断是否是开发者
                    if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
                        // 是开发者不需要审核
                        topic.setStatus(StatusEnums.NORMAL.getCode());
                    }
                    topic.setFailMsg("");
                    // 更新
                    BeanUtils.copyProperties(topicExcel, topic);
                    topicMapper.updateById(topic);
                    successNum++;
                    successMsg.append("<br/>").append(successNum).append("-题目：").append(topic.getTopicName()).append("-更新成功");
                } else {
                    // 已存在
                    failureNum++;
                    failureMsg.append("<br/>").append(failureNum).append("-题目：").append(topic.getTopicName()).append("-已存在");
                }
            } catch (Exception e) {
                failureNum++;
                String msg = "<br/>" + failureNum + "-题目： " + topicExcel.getTopicName() + " 导入失败：";
                failureMsg.append(msg).append(e.getMessage());
                log.error(msg, e);
            }
        }
        if (failureNum > 0) {
            failureMsg.insert(0, "很抱歉，导入失败！共 " + failureNum + " 条数据格式不正确，错误如下：");
            throw new TopicException(failureMsg.toString());
        } else {
            successMsg.insert(0, "恭喜您，数据已全部导入成功！共 " + successNum + " 条，数据如下：");
        }
        return successMsg.toString();
    }
}
