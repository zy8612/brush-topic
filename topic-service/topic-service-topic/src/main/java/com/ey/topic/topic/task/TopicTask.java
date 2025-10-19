package com.ey.topic.topic.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ey.client.security.SecurityFeignClient;
import com.ey.model.entity.system.SysUser;
import com.ey.model.entity.topic.*;
import com.ey.model.vo.topic.TopicUserRankVo;
import com.ey.service.utils.enums.StatusEnums;
import com.ey.topic.topic.mapper.*;
import com.ey.topic.topic.service.impl.TopicDataServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Description: 题目相关定时任务
 */
@EnableScheduling
@Component
@Slf4j
@RequiredArgsConstructor
public class TopicTask {

    private final TopicRecordMapper topicRecordMapper;
    private final TopicDataServiceImpl topicDataService;
    private final TopicMapper topicMapper;
    private final TopicDailyStagingMapper topicDailyStagingMapper;
    private final TopicSubjectTopicMapper topicSubjectTopicMapper;
    private final SecurityFeignClient securityFeignClient;
    private final TopicSubjectMapper topicSubjectMapper;
    private final TopicDailyBrushMapper topicDailyBrushMapper;

    /**
     * 每分钟执行 - 查询排行榜数据并重新写入Redis防止redis挂了导致数据丢失
     */
    @Scheduled(cron = "0 * * * * ?") // 1分钟
    public void refreshRankingToRedis() {
        log.info("更新排行榜数据");
        List<TopicUserRankVo> rankList = topicRecordMapper.getCountRank(null);
        if (CollectionUtils.isNotEmpty(rankList)) {
            // 写入今日用户信息和数据
            topicDataService.readRankTodayCache(rankList);
            // 写入全部排行榜数据
            topicDataService.readRankCache(rankList);
        }
    }
    /*private final TopicMapper topicMapper;
    @Scheduled(cron = "0 * * * * ?")
    public void delete() {
        Set<Long> topic = topicRecordMapper.selectByUserId();
        Set<Long> topicId = topicMapper.get();
        List<Long> no = new ArrayList<>();
        for (Long topicId1 : topic) {
            if(!topicId.contains(topicId1)) {
                no.add(topicId1);
                topicRecordMapper.deleteByTopicId(topicId1);
            }
        }
        System.out.println(no.toString());
    }*/

    /**
     * 每天凌晨 12:00 执行 - 删除用户每日必刷并将所有用户的每日必刷题数据写入Redis和数据库
     */
    @Scheduled(cron = "0 0 0 * * ?")
    //@Scheduled(cron = "0 * * * * ?") // 1分钟
    public void refreshUserTopicToRedis() {
        // 删除所有数据
        int delete1 = topicDailyBrushMapper.delete(null);
        int delete = topicDailyStagingMapper.delete(null);

        // 查询管理员是否设置了每日必刷
        LambdaQueryWrapper<Topic> topicLambdaQueryWrapper = new LambdaQueryWrapper<>();
        topicLambdaQueryWrapper.eq(Topic::getIsEveryday, 1);
        topicLambdaQueryWrapper.eq(Topic::getIsMember, 0);
        topicLambdaQueryWrapper.eq(Topic::getStatus, StatusEnums.NORMAL.getCode());
        List<Topic> topicList = topicMapper.selectList(topicLambdaQueryWrapper);
        // 查询免费的题目
        LambdaQueryWrapper<Topic> userTopicQueryWrapper = new LambdaQueryWrapper<>();
        userTopicQueryWrapper.eq(Topic::getStatus, StatusEnums.NORMAL.getCode());
        userTopicQueryWrapper.eq(Topic::getIsDeleted, 0);
        userTopicQueryWrapper.eq(Topic::getIsMember, 0);
        List<Topic> userTopics = topicMapper.selectList(userTopicQueryWrapper);
        // 查询会员所有的题目
        LambdaQueryWrapper<Topic> memberTopicQueryWrapper = new LambdaQueryWrapper<>();
        memberTopicQueryWrapper.eq(Topic::getStatus, StatusEnums.NORMAL.getCode());
        memberTopicQueryWrapper.eq(Topic::getIsDeleted, 0);
        memberTopicQueryWrapper.eq(Topic::getIsMember, 1);
        List<Topic> memberTopics = topicMapper.selectList(memberTopicQueryWrapper);
        // 查询出所有的用户id
        List<SysUser> allUser = securityFeignClient.getAllUser();
        if (CollectionUtils.isNotEmpty(topicList)) {
            // 获取到数量
            int size = topicList.size();
            // 不为空判断数量是否大于9个
            // 等于9个说明当日必刷全是管理员选的直接存公共的
            for (Topic topic : topicList) {
                // 查询题目专题表
                LambdaQueryWrapper<TopicSubjectTopic> topicSubjectTopicLambdaQueryWrapper = new LambdaQueryWrapper<>();
                topicSubjectTopicLambdaQueryWrapper.eq(TopicSubjectTopic::getTopicId, topic.getId());
                TopicSubjectTopic topicSubjectTopicDb = topicSubjectTopicMapper.selectOne(topicSubjectTopicLambdaQueryWrapper);
                TopicDailyStaging topicDailyStaging = new TopicDailyStaging();
                topicDailyStaging.setTopicId(topic.getId());
                topicDailyStaging.setSubjectId(topicSubjectTopicDb.getSubjectId());
                topicDailyStaging.setIsPublic(1);
                // 插入到每日必刷
                topicDailyStagingMapper.insert(topicDailyStaging);
            }
            if (size == 9) {
                return;
            }
            // 计算需要补充的数量
            int restSize = 9 - size;
            // 提取userTopics中所有题目ID（去重，避免同一题目多次处理）
            Set<Long> allTopicIds = userTopics.stream()
                    .map(Topic::getId)
                    .collect(Collectors.toSet()); // 去重，避免重复处理同一题目
            // 从候选集中随机选择actualNeed个题目ID
            List<Long> selectedIds = new ArrayList<>(allTopicIds);
            Collections.shuffle(selectedIds); // 随机打乱
            selectedIds = selectedIds.subList(0, restSize);
            // 批量查询这些题目的subjectId（一次查询，减少数据库交互）
            LambdaQueryWrapper<TopicSubjectTopic> subjectWrapper = new LambdaQueryWrapper<>();
            subjectWrapper.in(TopicSubjectTopic::getTopicId, selectedIds);
            List<TopicSubjectTopic> subjectList = topicSubjectTopicMapper.selectList(subjectWrapper);
            // 转为Map便于快速查询：topicId -> subjectId
            Map<Long, Long> topicSubjectMap = subjectList.stream()
                    .collect(Collectors.toMap(
                            TopicSubjectTopic::getTopicId,
                            TopicSubjectTopic::getSubjectId
                    ));
            // 批量插入到TopicDailyStaging
            List<TopicDailyStaging> insertList = selectedIds.stream()
                    .map(topicId -> {
                        TopicDailyStaging staging = new TopicDailyStaging();
                        staging.setTopicId(topicId);
                        staging.setSubjectId(topicSubjectMap.get(topicId)); // 从Map获取，无需重复查询
                        staging.setIsPublic(1);
                        return staging;
                    })
                    .collect(Collectors.toList());
            // 批量插入（效率高于单条插入）
            if (!insertList.isEmpty()) {
                topicDailyStagingMapper.insertBatch(insertList); // 需确保Mapper支持批量插入
            }
        }

        List<Topic> allTopics = new ArrayList<>(userTopics);
        allTopics.addAll(memberTopics);
        for (SysUser sysUser : allUser) {
            if (sysUser.getMemberTime() == null) {
                continue;
            }
            // 根据用户id查询用户刷题表
            LambdaQueryWrapper<TopicRecord> topicRecordLambdaQueryWrapper = new LambdaQueryWrapper<>();
            topicRecordLambdaQueryWrapper.eq(TopicRecord::getUserId, sysUser.getId());
            List<TopicRecord> topicRecords = topicRecordMapper.selectList(topicRecordLambdaQueryWrapper);
            if (CollectionUtils.isEmpty(topicRecords)) {
                // 1.分配9道随机题目
                int restSize = 9;
                insertStaging(restSize, allTopics, sysUser.getId());
            } else {
                // 1.分配5道随机题目
                int restSize = 5;
                insertStaging(restSize, userTopics, sysUser.getId());
                // 4道常刷题
                Long frequentSubjectId = topicRecordMapper.selectMaxSubject(sysUser.getId());
                LambdaQueryWrapper<TopicSubjectTopic> subjectTopicWrapper = new LambdaQueryWrapper<>();
                subjectTopicWrapper.eq(TopicSubjectTopic::getSubjectId, frequentSubjectId);
                List<TopicSubjectTopic> subjectTopicList = topicSubjectTopicMapper.selectList(subjectTopicWrapper);
                if (CollectionUtils.isEmpty(subjectTopicList)) {
                    return; // 专题下无题目，直接返回
                }
                Set<Long> topicIds = subjectTopicList.stream()
                        .map(TopicSubjectTopic::getTopicId)
                        .collect(Collectors.toSet());
                LambdaQueryWrapper<TopicDailyStaging> userExistsWrapper = new LambdaQueryWrapper<>();
                userExistsWrapper.eq(TopicDailyStaging::getUserId, sysUser.getId())
                        .eq(TopicDailyStaging::getIsPublic, 2);
                Set<Long> userExistsTopicIds = topicDailyStagingMapper.selectList(userExistsWrapper).stream()
                        .map(TopicDailyStaging::getTopicId)
                        .collect(Collectors.toSet());
                List<Long> availableTopics = topicIds.stream()
                        .filter(topicId -> !userExistsTopicIds.contains(topicId)) // 排除用户已有的
                        .collect(Collectors.toCollection(ArrayList::new));
                Collections.shuffle(availableTopics);
                List<Long> selectedTopicIds = availableTopics.subList(0, 4);
                List<Topic> selectedTopics = topicMapper.selectBatchIds(selectedTopicIds);
                List<TopicDailyStaging> insertList = new ArrayList<>();
                // 8. 批量插入：1次插入所有选中的题目（减少IO）
                for (Topic topic : selectedTopics) {
                    TopicDailyStaging staging = new TopicDailyStaging();
                    staging.setTopicId(topic.getId());
                    staging.setSubjectId(frequentSubjectId); // 复用常刷专题ID，无需再查
                    staging.setUserId(sysUser.getId());
                    staging.setIsPublic(2);
                    insertList.add(staging);
                }
                if (!insertList.isEmpty()) {
                    topicDailyStagingMapper.insertBatch(insertList); // 批量插入
                }
            }
        }
    }

    private void insertStaging(int restSize, List<Topic> allTopics, Long userId) {
        Set<Long> allTopicIds = allTopics.stream()
                .map(Topic::getId)
                .collect(Collectors.toSet()); // 去重，避免重复处理同一题目
        // 从候选集中随机选择actualNeed个题目ID
        List<Long> selectedIds = new ArrayList<>(allTopicIds);
        Collections.shuffle(selectedIds); // 随机打乱
        selectedIds = selectedIds.subList(0, restSize); // 取前size个
        // 批量查询这些题目的subjectId（一次查询，减少数据库交互）
        LambdaQueryWrapper<TopicSubjectTopic> subjectWrapper = new LambdaQueryWrapper<>();
        subjectWrapper.in(TopicSubjectTopic::getTopicId, selectedIds);
        List<TopicSubjectTopic> subjectList = topicSubjectTopicMapper.selectList(subjectWrapper);
        // 转为Map便于快速查询：topicId -> subjectId
        Map<Long, Long> topicSubjectMap = subjectList.stream()
                .collect(Collectors.toMap(
                        TopicSubjectTopic::getTopicId,
                        TopicSubjectTopic::getSubjectId
                ));
        // 批量插入到TopicDailyStaging
        List<TopicDailyStaging> insertList = selectedIds.stream()
                .map(topicId -> {
                    TopicDailyStaging staging = new TopicDailyStaging();
                    staging.setTopicId(topicId);
                    staging.setSubjectId(topicSubjectMap.get(topicId)); // 从Map获取，无需重复查询
                    staging.setUserId(userId);
                    staging.setIsPublic(2);
                    return staging;
                })
                .collect(Collectors.toList());
        // 批量插入（效率高于单条插入）
        if (!insertList.isEmpty()) {
            topicDailyStagingMapper.insertBatch(insertList); // 需确保Mapper支持批量插入
        }
    }
}
