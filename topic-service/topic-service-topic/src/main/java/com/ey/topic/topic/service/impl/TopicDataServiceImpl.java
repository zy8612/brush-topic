package com.ey.topic.topic.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ey.client.ai.AiFeignClient;
import com.ey.client.security.SecurityFeignClient;
import com.ey.common.constant.RedisConstant;
import com.ey.common.enums.RoleEnum;
import com.ey.common.security.utils.SecurityUtils;
import com.ey.model.entity.topic.*;
import com.ey.model.vo.ai.AiTrendVo;
import com.ey.model.vo.system.SysUserTrentVo;
import com.ey.model.vo.topic.*;
import com.ey.service.utils.enums.StatusEnums;
import com.ey.topic.topic.mapper.*;
import com.ey.topic.topic.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicDataServiceImpl implements TopicDataService {

    private final TopicRecordMapper topicRecordMapper;
    private final SecurityFeignClient securityFeignClient;
    private final TopicService topicService;
    private final TopicSubjectService topicSubjectService;
    private final TopicLabelService topicLabelService;
    private final TopicCategoryService topicCategoryService;
    private final TopicCategorySubjectMapper topicCategorySubjectMapper;
    private final AiFeignClient aiFeignClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final TopicDailyStagingMapper topicDailyStagingMapper;
    private final TopicDailyBrushMapper topicDailyBrushMapper;
    private final TopicLabelTopicMapper  topicLabelTopicMapper;


    /**
     * 查询题目刷题数据以及刷题排名和用户数量
     *
     * @return
     */
    @Override
    public Map<String, Object> webHomeCount() {
        // 获取当前用户信息
        String currentName = SecurityUtils.getCurrentName();
        String role = SecurityUtils.getCurrentRole();
        Long currentId = SecurityUtils.getCurrentId();
        // 获取总用户数
        Long userCount = securityFeignClient.countTotalUser();
        // 获取当前排名
        Long rank = topicRecordMapper.getRank(currentId);
        // 获取当日的刷题次数
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        LambdaQueryWrapper<TopicRecord> todayCountQueryWrapper = new LambdaQueryWrapper<>();
        todayCountQueryWrapper.eq(TopicRecord::getUserId, currentId)
                .eq(TopicRecord::getTopicTime, today);
        Long todayCount = topicRecordMapper.selectCount(todayCountQueryWrapper);
        // 获取今日刷题数量
        LambdaQueryWrapper<TopicRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TopicRecord::getUserId, currentId)
                .eq(TopicRecord::getTopicTime, LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        Long todayTopicCount = topicRecordMapper.selectCount(queryWrapper);
        // 统计题目数量总数
        LambdaQueryWrapper<Topic> topicQueryWrapper = new LambdaQueryWrapper<>();
        if (role.equals(RoleEnum.USER.getRoleKey())) {
            topicQueryWrapper.eq(Topic::getCreateBy, "admin");
        } else {
            topicQueryWrapper.in(Topic::getCreateBy, "admin", currentName);
        }
        topicQueryWrapper.eq(Topic::getStatus, StatusEnums.NORMAL.getCode());
        Long totalTopicCount = topicService.count(topicQueryWrapper);

        Long totalTopicRecordCountSize = topicRecordMapper.userTopicRecordCount(currentId);

        Map<String, Object> map = new HashMap<>();
        map.put("userCount", userCount);
        map.put("rank", rank);
        map.put("todayTopicCount", todayTopicCount);
        map.put("totalTopicCount", totalTopicCount);
        map.put("totalTopicRecordCount", totalTopicRecordCountSize);
        map.put("todayCount", todayCount);

        return map;
    }

    /**
     * 查询排行榜
     *
     * @param type
     * @return
     */
    @Override
    public List<TopicUserRankVo> rank(Integer type) {
        if (type == null) {
            return null;
        }
        // 封装返回数据
        List<TopicUserRankVo> rankList = new ArrayList<>();
        // 1代表今日排行，2代表总排行
        if (type == 1) {
            // 从redis中获取排行
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String todayKey = RedisConstant.TOPIC_RANK_TODAY;
            Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet().
                    reverseRangeWithScores(todayKey, 0, 99);
            // redis中不存在从数据库查询
            if (CollectionUtil.isEmpty(tuples)) {
                // 为空查数据库
                List<TopicUserRankVo> countRank = topicRecordMapper.getCountRank(date);
                if (CollectionUtils.isNotEmpty(countRank)) {
                    readRankTodayCache(countRank);
                }
                return countRank;
            }
            getRankVo(rankList, tuples);
        } else {
            // 总排行榜
            // 获取排名前100的用户ID和分数
            Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                    .reverseRangeWithScores(RedisConstant.TOPIC_RANK_TOTAL, 0, 99);
            if (CollectionUtils.isEmpty(tuples)) {
                // 为空查数据库
                List<TopicUserRankVo> countRank = topicRecordMapper.getCountRank(null);
                if (CollectionUtils.isNotEmpty(countRank)) {
                    readRankTodayCache(countRank);
                    countRank = readRankCache(countRank);
                }
                return countRank;
            }
            getRankVo(rankList, tuples);
        }
        return rankList;
    }

    /**
     * 获取当前用户排名信息
     *
     * @return TopicUserRankVo 包含用户排名、昵称、头像、分数等信息
     */
    @Override
    public TopicUserRankVo userRank(Integer type) {
        Long userId = SecurityUtils.getCurrentId();
        String key;
        if (type == 1) {
            key = RedisConstant.TOPIC_RANK_TODAY;
        } else {
            key = RedisConstant.TOPIC_RANK_TOTAL;
        }
        // 先从redis中查询
        Long rank = stringRedisTemplate.opsForZSet().reverseRank(key, userId.toString());
        if (type == 1) {
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            if (rank == null) {
                // redis中没有从数据库中查询
                return topicRecordMapper.getUserTodayRank(date, userId);
            }
        } else {
            // 判断用户排名是否为空
            if (rank == null) {
                //  为空查数据库
                return topicRecordMapper.getUserTotalRank(userId);
            }
        }
        // redis中存在
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        // 如果用户不在排行榜中（可能没有刷题记录）
        if (score == null) {
            return null;
        }
        // 获取用户详细信息
        Map<Object, Object> userInfo = stringRedisTemplate.opsForHash()
                .entries(RedisConstant.USER_RANK + userId);

        // 封装返回对象
        TopicUserRankVo topicUserRankVo = new TopicUserRankVo();
        topicUserRankVo.setUserId(userId);
        topicUserRankVo.setNickname((String) userInfo.get("nickname"));
        topicUserRankVo.setAvatar((String) userInfo.get("avatar"));
        topicUserRankVo.setScope(score.longValue());
        topicUserRankVo.setRank(rank + 1);
        topicUserRankVo.setRole((String) userInfo.get("role"));

        return topicUserRankVo;
    }

    /**
     * 查询每日必刷
     *
     * @return
     */
    @Override
    public List<TopicTodayVo> topicTodayVo() {
        // 查询公共的题目
        LambdaQueryWrapper<TopicDailyStaging> stagingQueryWrapper = new LambdaQueryWrapper<>();
        stagingQueryWrapper.eq(TopicDailyStaging::getIsPublic, 1);
        List<TopicDailyStaging> topicList = topicDailyStagingMapper.selectList(stagingQueryWrapper);
        // 查询用户的
        Long currentId = SecurityUtils.getCurrentId();
        LambdaQueryWrapper<TopicDailyStaging> userQueryWrapper = new LambdaQueryWrapper<>();
        userQueryWrapper.eq(TopicDailyStaging::getIsPublic, 2)
                .eq(TopicDailyStaging::getUserId, currentId);
        List<TopicDailyStaging> topicList1 = topicDailyStagingMapper.selectList(userQueryWrapper);
        topicList.addAll(topicList1);
        // 遍历封装返回数据
        return topicList.stream().map(topicDailyStaging -> {
            // 封装返回数据
            TopicTodayVo topicTodayVo = new TopicTodayVo();
            // 根据题目id查询题目
            Topic topic = topicService.getById(topicDailyStaging.getTopicId());
            // 根据题目id查询题目标签题目关系表
            LambdaQueryWrapper<TopicLabelTopic> topicLabelTopicLambdaQueryWrapper = new LambdaQueryWrapper<>();
            topicLabelTopicLambdaQueryWrapper.eq(TopicLabelTopic::getTopicId, topicDailyStaging.getTopicId());
            List<TopicLabelTopic> topicLabelTopics = topicLabelTopicMapper.selectList(topicLabelTopicLambdaQueryWrapper);
            if (CollectionUtils.isEmpty(topicLabelTopics)) {
                return null;
            }
            // 有标签
            // 收集所有的id
            List<Long> labelIds = topicLabelTopics.stream().map(TopicLabelTopic::getLabelId).toList();
            // 存放标签名称
            List<String> labelNames = topicLabelService.getLabelNamesByIds(labelIds);
            // 查询用户刷过的每日刷题
            LambdaQueryWrapper<TopicDailyBrush> topicDailyBrushLambdaQueryWrapper = new LambdaQueryWrapper<>();
            topicDailyBrushLambdaQueryWrapper.eq(TopicDailyBrush::getUserId, currentId);
            topicDailyBrushLambdaQueryWrapper.eq(TopicDailyBrush::getDailyId, topicDailyStaging.getId());
            if (topicDailyBrushMapper.selectOne(topicDailyBrushLambdaQueryWrapper) != null) {
                // 1代表刷过
                topicTodayVo.setStatus(1);
            } else {
                topicTodayVo.setStatus(0);
            }
            topicTodayVo.setTopicName(topic.getTopicName());
            topicTodayVo.setLabelNames(labelNames);
            topicTodayVo.setId(topicDailyStaging.getId());
            topicTodayVo.setSubjectId(topicDailyStaging.getSubjectId());
            topicTodayVo.setTopicId(topicDailyStaging.getTopicId());
            return topicTodayVo;
        }).toList();
    }

    /**
     * 刷每日题
     * @param id
     */
    @Override
    public void flushTopic(Long id) {
        // 查询
        LambdaQueryWrapper<TopicDailyBrush> topicDailyBrushLambdaQueryWrapper = new LambdaQueryWrapper<>();
        topicDailyBrushLambdaQueryWrapper.eq(TopicDailyBrush::getDailyId, id);
        topicDailyBrushLambdaQueryWrapper.eq(TopicDailyBrush::getUserId, SecurityUtils.getCurrentId());
        TopicDailyBrush topicDailyBrush = topicDailyBrushMapper.selectOne(topicDailyBrushLambdaQueryWrapper);
        if (topicDailyBrush == null) {
            topicDailyBrush = new TopicDailyBrush();
            topicDailyBrush.setDailyId(id);
            topicDailyBrush.setUserId(SecurityUtils.getCurrentId());
            topicDailyBrushMapper.insert(topicDailyBrush);
        }
    }

    // 从redis中获取用户信息数据，封装返回数据
    private void getRankVo(List<TopicUserRankVo> rankList, Set<ZSetOperations.TypedTuple<String>> tuples) {
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                // 遍历排名100的用户
                // 获取用户ID和分数
                String userId = tuple.getValue();
                Double score = tuple.getScore();
                log.info("用户ID:{},分数:{}", userId, score);
                // 从Hash中获取用户详细信息
                Map<Object, Object> userInfo = stringRedisTemplate.opsForHash()
                        .entries(RedisConstant.USER_RANK + userId);
                // 封装
                TopicUserRankVo topicUserRankVo = new TopicUserRankVo();
                if (score != null) {
                    topicUserRankVo.setScope(score.longValue());
                }
                if (userInfo.get("avatar") == null) {
                    topicUserRankVo.setAvatar(null);
                } else {
                    topicUserRankVo.setAvatar((String) userInfo.get("avatar"));
                }
                topicUserRankVo.setNickname((String) userInfo.get("nickname"));
                if (userId != null) {
                    topicUserRankVo.setUserId(Long.valueOf(userId));
                }
                topicUserRankVo.setRole((String) userInfo.get("role"));

                rankList.add(topicUserRankVo);
            }
        }
    }

    // 将总排行写入redis
    public List<TopicUserRankVo> readRankCache(List<TopicUserRankVo> countRank) {
        Map<Long, TopicUserRankVo> userScope = new HashMap<>();
        for (TopicUserRankVo topicUserRankVo : countRank) {
            userScope.merge(topicUserRankVo.getUserId(),
                    topicUserRankVo,
                    (existVo, newVo) -> {
                        existVo.setScope(existVo.getScope() + newVo.getScope());
                        return existVo;
                    });
        }
        List<TopicUserRankVo> totalRank = userScope.values().stream()
                .sorted((o1, o2) -> Long.compare(o2.getScope(), o1.getScope())) // 降序排序
                .toList();
        for (TopicUserRankVo topicUserRankVo : totalRank) {
            // 存全部信息
            stringRedisTemplate.opsForZSet().add(RedisConstant.TOPIC_RANK_TOTAL, String.valueOf(topicUserRankVo.getUserId()), topicUserRankVo.getScope());
        }
        return totalRank;
    }

    // 将今日排行榜缓存数据写入redis
    public void readRankTodayCache(List<TopicUserRankVo> countRank) {
        // 获取今日日期
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Set<Long> processedUsers = new HashSet<>();
        for (TopicUserRankVo topicUserRankVo : countRank) {
            if (processedUsers.add(topicUserRankVo.getUserId())) {
                // 存储用户信息到Hash
                Map<String, String> userInfo = new HashMap<>();
                userInfo.put("nickname", topicUserRankVo.getNickname());
                userInfo.put("avatar", topicUserRankVo.getAvatar());
                userInfo.put("role", topicUserRankVo.getRole());
                stringRedisTemplate.opsForHash().putAll(RedisConstant.USER_RANK + topicUserRankVo.getUserId(), userInfo);
            }
            // 存今日信息
            if (date.equals(topicUserRankVo.getTopicTime())) {
                stringRedisTemplate.opsForZSet().add(RedisConstant.TOPIC_RANK_TODAY, String.valueOf(topicUserRankVo.getUserId()), topicUserRankVo.getScope());
            }
        }
    }


    /**
     * 管理员顶部左侧数据统计
     *
     * @return
     */
    @Override
    public Map<String, Object> adminHomeCount() {
        // 封装返回数据
        Map<String, Object> map = new HashMap<>();
        // 获取当前时间
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        // 获取昨日时间
        String yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // 1.刷题总数和比昨日少或多多少
        // 获取刷题数据
        Long totalTopicFrequency = topicRecordMapper.countTopicFrequency(null);
        // 获取昨日和今日数据
        Long todayTopicFrequency = topicRecordMapper.countTopicFrequency(today);
        todayTopicFrequency = todayTopicFrequency == null ? 0L : todayTopicFrequency;
        Long yesterdayTopicFrequency = topicRecordMapper.countTopicFrequency(yesterday);
        yesterdayTopicFrequency = yesterdayTopicFrequency == null ? 0L : yesterdayTopicFrequency;
        // 计算新增刷题数量
        long topicGrowthRate = todayTopicFrequency - yesterdayTopicFrequency;

        // 2.AI调用总次数和比昨日少或多多少
        Long aiCount = aiFeignClient.count();
        // 查询今日
        Long todayAiCount = aiFeignClient.countDate(today);
        // 查询昨日
        Long yesterdayAiCount = aiFeignClient.countDate(yesterday);
        // 计算差值（今天 - 昨天）
        long aiGrowthRate = todayAiCount - yesterdayAiCount;

        // 3.用户总数和昨日幅度
        // 获取总用户数
        Long userCount = securityFeignClient.countTotalUser();
        // 获取新增用户数量
        Long todayUserCount = securityFeignClient.countUser(today);
        todayUserCount = todayUserCount == null ? 0L : todayUserCount;
        Long yesterdayUserCount = securityFeignClient.countUser(yesterday);
        yesterdayUserCount = yesterdayUserCount == null ? 0L : yesterdayUserCount;

        // 计算用户增长数
        long userGrowthRate = todayUserCount - yesterdayUserCount;

        // 4.题目总数量
        // 用户直接查询系统数量
        LambdaQueryWrapper<Topic> topicLambdaQueryWrapper = new LambdaQueryWrapper<>();
        topicLambdaQueryWrapper.eq(Topic::getStatus, StatusEnums.NORMAL.getCode());
        topicLambdaQueryWrapper.eq(Topic::getCreateBy, RoleEnum.ADMIN.getRoleKey());
        Long totalTopicCount = topicService.count(topicLambdaQueryWrapper);

        // 5.专题总数量
        LambdaQueryWrapper<TopicSubject> subjectLambdaQueryWrapper = new LambdaQueryWrapper<>();
        subjectLambdaQueryWrapper.eq(TopicSubject::getStatus, StatusEnums.NORMAL.getCode());
        subjectLambdaQueryWrapper.eq(TopicSubject::getCreateBy, RoleEnum.ADMIN.getRoleKey());
        Long totalSubjectCount = topicSubjectService.count(subjectLambdaQueryWrapper);

        // 6.标签总数量
        LambdaQueryWrapper<TopicLabel> labelLambdaQueryWrapper = new LambdaQueryWrapper<>();
        labelLambdaQueryWrapper.eq(TopicLabel::getStatus, StatusEnums.NORMAL.getCode());
        labelLambdaQueryWrapper.eq(TopicLabel::getCreateBy, RoleEnum.ADMIN.getRoleKey());
        Long topicLabelCount = topicLabelService.count(labelLambdaQueryWrapper);

        map.put("countTodayFrequency", totalTopicFrequency);
        map.put("topicGrowthRate", topicGrowthRate);
        map.put("userCount", userCount);
        map.put("userGrowthRate", userGrowthRate);
        map.put("totalTopicCount", totalTopicCount);
        map.put("totalSubjectCount", totalSubjectCount);
        map.put("topicLabelCount", topicLabelCount);
        map.put("aiCount", aiCount);
        map.put("aiGrowthRate", aiGrowthRate);

        return map;
    }

    /**
     * 查询分类名称和分类名称下的题目总数量
     *
     * @return
     */
    @Override
    public List<TopicCategoryDataVo> adminHomeCategory() {
        // 1.查询所有的分类
        LambdaQueryWrapper<TopicCategory> topicCategoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
        topicCategoryLambdaQueryWrapper.eq(TopicCategory::getStatus, StatusEnums.NORMAL.getCode());
        topicCategoryLambdaQueryWrapper.eq(TopicCategory::getCreateBy, RoleEnum.ADMIN.getRoleKey());
        List<TopicCategory> categoryList = topicCategoryService.list(topicCategoryLambdaQueryWrapper);
        if (CollectionUtil.isEmpty(categoryList)) {
            return null;
        }
        return categoryList.stream().map(category -> {
            // 2.查询分类专题表
            TopicCategoryDataVo topicCategoryDataVo = new TopicCategoryDataVo();
            topicCategoryDataVo.setCategoryName(category.getCategoryName());
            LambdaQueryWrapper<TopicCategorySubject> topicCategorySubjectLambdaQueryWrapper = new LambdaQueryWrapper<>();
            topicCategorySubjectLambdaQueryWrapper.eq(TopicCategorySubject::getCategoryId, category.getId());
            List<TopicCategorySubject> categorySubjects = topicCategorySubjectMapper.selectList(topicCategorySubjectLambdaQueryWrapper);
            if (CollectionUtil.isEmpty(categorySubjects)) {
                topicCategoryDataVo.setCount(0L);
                return topicCategoryDataVo;
            }
            // 3.查询专题表
            long count = 0L;
            List<Long> subjectIds = categorySubjects.stream().map(TopicCategorySubject::getSubjectId).toList();
            List<TopicSubject> subjectList = topicSubjectService.listByIds(subjectIds);
            for (TopicSubject topicSubject : subjectList) {
                count += topicSubject.getTopicCount();
            }
            topicCategoryDataVo.setCount(count);
            return topicCategoryDataVo;
        }).toList();
    }

    /**
     * 刷题题目和刷题人数趋势图
     *
     * @return
     */
    @Override
    public TopicTrendVo topicTrend() {
        // 查询近15日的刷题统计数据
        List<TopicDataVo> topicDataTopicList = topicRecordMapper.countTopicDay15();
        // 查询近15日的刷题人数数据
        List<TopicDataVo> topicDataUserList = topicRecordMapper.countUserDay15();
        // 映射日期
        List<String> dateList = topicDataTopicList.stream().map(TopicDataVo::getDate).toList();
        // 映射刷题数据
        List<Integer> countTopicList = topicDataTopicList.stream().map(TopicDataVo::getCount).toList();
        // 映射刷题人数数据
        List<Integer> countUserList = topicDataUserList.stream().map(TopicDataVo::getCount).toList();
        // 封装返回对象
        TopicTrendVo topicTrendVo = new TopicTrendVo();
        topicTrendVo.setDateList(dateList);
        topicTrendVo.setCountTopicList(countTopicList);
        topicTrendVo.setCountUserList(countUserList);

        return topicTrendVo;
    }

    /**
     * 用户趋势图
     *
     * @return
     */
    @Override
    public SysUserTrentVo userTrend() {
        // 获取新增用户数据
        List<TopicDataVo> topicDataUserList = securityFeignClient.countUserDay7();
        // 映射日期
        List<String> dateList = topicDataUserList.stream().map(TopicDataVo::getDate).toList();
        List<Integer> countList = topicDataUserList.stream().map(TopicDataVo::getCount).toList();
        SysUserTrentVo sysUserTrentVo = new SysUserTrentVo();
        sysUserTrentVo.setDateList(dateList);
        sysUserTrentVo.setCountList(countList);
        return sysUserTrentVo;
    }

    /**
     * ai调用趋势图
     *
     * @return
     */
    @Override
    public AiTrendVo aiTrend() {
        List<TopicDataVo> topicDataVoList = aiFeignClient.countAiDay7();
        List<String> dateList = topicDataVoList.stream().map(TopicDataVo::getDate).toList();
        List<Integer> countList = topicDataVoList.stream().map(TopicDataVo::getCount).toList();
        AiTrendVo aiTrendVo = new AiTrendVo();
        aiTrendVo.setDateList(dateList);
        aiTrendVo.setCountList(countList);
        return aiTrendVo;
    }

    @Override
    public Map<String, Object> userHomeCount() {
        // 封装返回数据
        Map<String, Object> map = new HashMap<>();
        Long currentId = SecurityUtils.getCurrentId();
        String role = SecurityUtils.getCurrentRole();
        String currentName = SecurityUtils.getCurrentName();

        // 查询已刷题次数
        Long topicFrequencyCount = topicRecordMapper.countTopicUserRecord(currentId);
        // 获取日期
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        // 查询用户昨日和今日刷题次数
        Long todayTopicCount = topicRecordMapper.countTopicFrequency(today);
        todayTopicCount = todayTopicCount == null ? 0L : todayTopicCount;
        Long yesterdayTopicCount = topicRecordMapper.countTopicFrequency(yesterday);
        yesterdayTopicCount = yesterdayTopicCount == null ? 0L : yesterdayTopicCount;
        // 计算差值（今天 - 昨天）
        Long topicFrequencyGrowthRate = todayTopicCount - yesterdayTopicCount;

        // 获取排名
        Long rank = topicRecordMapper.getRank(currentId);
        Long todayRank = topicRecordMapper.getDateRank(currentId, today);
        todayRank = todayRank == null ? 0L : todayRank;
        Long yesterdayRank = topicRecordMapper.getDateRank(currentId, yesterday);
        yesterdayRank = yesterdayRank == null ? 0L : yesterdayRank;
        // 计算差值
        long rankGrowthRate = todayRank - yesterdayRank;

        // 查询用户已刷题目的数量
        Long totalTopicRecordCountSize = topicRecordMapper.userTopicRecordCount(currentId);
        // 统计题目数量总数
        LambdaQueryWrapper<Topic> topicLambdaQueryWrapper = new LambdaQueryWrapper<>();
        topicLambdaQueryWrapper.eq(Topic::getStatus, StatusEnums.NORMAL.getCode());
        if (role.equals("user")) {
            // 用户直接查询系统数量
            topicLambdaQueryWrapper.eq(Topic::getCreateBy, "admin");
        } else {
            // 管理员和会员可以查自己的
            topicLambdaQueryWrapper.in(Topic::getCreateBy, "admin", currentName);
        }
        long totalTopicCount = topicService.count(topicLambdaQueryWrapper);

        // 7.用户ai总次数
        Long aiCount = aiFeignClient.countAi(currentId);

        // 8.最长连续刷题次数
        Long maxConsecutiveCount = topicRecordMapper.selectMaximumCount(currentId);

        // 9.最近连续刷题次数
        Long recentConsecutiveCount = topicRecordMapper.selectRecentConsecutiveCount(currentId);

        map.put("topicFrequencyCount", topicFrequencyCount);
        map.put("topicFrequencyGrowthRate", topicFrequencyGrowthRate);
        map.put("rank", rank);
        map.put("rankGrowthRate", rankGrowthRate);
        map.put("totalTopicRecordCountSize", totalTopicRecordCountSize);
        map.put("totalTopicCount", totalTopicCount);
        map.put("aiCount", aiCount == null ? 0L : aiCount);
        map.put("maxConsecutiveCount", maxConsecutiveCount == null ? 0L : maxConsecutiveCount);
        map.put("recentConsecutiveCount", recentConsecutiveCount == null ? 0L : recentConsecutiveCount);

        return map;
    }

    @Override
    public List<TopicCategoryUserDataVo> userHomeCategory() {
        // 封装返回数据
        List<TopicCategoryUserDataVo> topicCategoryUserDataVos = new ArrayList<>();
        Long currentId = SecurityUtils.getCurrentId();
        String role = SecurityUtils.getCurrentRole();
        String currentName = SecurityUtils.getCurrentName();
        // 1.查询所有的分类
        LambdaQueryWrapper<TopicCategory> topicCategoryLambdaQueryWrapper = new LambdaQueryWrapper<>();
        topicCategoryLambdaQueryWrapper.eq(TopicCategory::getStatus, StatusEnums.NORMAL.getCode());
        topicCategoryLambdaQueryWrapper.eq(TopicCategory::getCreateBy, RoleEnum.ADMIN.getRoleKey());
        if (role.equals(RoleEnum.MEMBER.getRoleKey())) {
            // 会员查看自定义分类
            topicCategoryLambdaQueryWrapper.in(TopicCategory::getCreateBy, "admin", currentName);
        } else {
            // 用户查看系统自带的
            topicCategoryLambdaQueryWrapper.eq(TopicCategory::getCreateBy, "admin");
        }
        List<TopicCategory> categoryList = topicCategoryService.list(topicCategoryLambdaQueryWrapper);
        if (CollectionUtil.isEmpty(categoryList)) {
            return null;
        }
        return categoryList.stream().map(category -> {
            // 2.查询分类专题表
            TopicCategoryUserDataVo topicCategoryUserDataVo = new TopicCategoryUserDataVo();
            topicCategoryUserDataVo.setCategoryName(category.getCategoryName());
            LambdaQueryWrapper<TopicCategorySubject> topicCategorySubjectLambdaQueryWrapper = new LambdaQueryWrapper<>();
            topicCategorySubjectLambdaQueryWrapper.eq(TopicCategorySubject::getCategoryId, category.getId());
            List<TopicCategorySubject> categorySubjects = topicCategorySubjectMapper.selectList(topicCategorySubjectLambdaQueryWrapper);
            if (CollectionUtil.isEmpty(categorySubjects)) {
                topicCategoryUserDataVo.setCount(0L);
                return topicCategoryUserDataVo;
            }
            // 3.查询专题表
            long totalCount = 0L;
            long count = 0L;
            List<Long> subjectIds = categorySubjects.stream().map(TopicCategorySubject::getSubjectId).toList();
            List<TopicSubject> subjectList = topicSubjectService.listByIds(subjectIds);
            for (TopicSubject topicSubject : subjectList) {
                totalCount += topicSubject.getTopicCount();
            }
            // 分类的总题目数量
            topicCategoryUserDataVo.setTotalCount(totalCount);
            // 分类中的所有题目的id
            Set<Long> categoryTopicIds = topicSubjectService.getTopicIdsBySubjectIds(subjectIds);
            // 用户刷的所有题目id
            List<Long> recordTopicIds = topicRecordMapper.getTopicRecordIds(subjectIds, currentId);
            count = recordTopicIds.stream().filter(categoryTopicIds::contains)
                    .count();
            topicCategoryUserDataVo.setCount(count);
            return topicCategoryUserDataVo;
        }).toList();

    }

    @Override
    public List<TopicDataVo> userTopicDateCount(String date) {
        // 获取当前登录用户id
        Long currentId = SecurityUtils.getCurrentId();
        String startDate = date + "-01-01"; //2025-01-01
        String endDate = date + "-12-31";   //2025-12-31
        // 开始查询
        return topicRecordMapper.userTopicDateCount(startDate, endDate, currentId);
    }

}
