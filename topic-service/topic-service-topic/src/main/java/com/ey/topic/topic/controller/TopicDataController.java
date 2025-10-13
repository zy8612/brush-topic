package com.ey.topic.topic.controller;

import com.ey.common.result.Result;
import com.ey.model.vo.ai.AiTrendVo;
import com.ey.model.vo.system.SysUserTrentVo;
import com.ey.model.vo.topic.*;
import com.ey.topic.topic.service.TopicDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Description: 用于统计数据相关的控制器
 */
@RestController
@RequestMapping("/topic/data")
@RequiredArgsConstructor
public class TopicDataController {

    private final TopicDataService topicDataService;


    /**
     * 统计h5首页刷题数据以及用户数量和排名
     */
    @GetMapping("/webHomeCount")
    public Result<Map<String, Object>> webTopicCount() {
        Map<String, Object> map = topicDataService.webHomeCount();
        return Result.success(map);
    }

    /**
     * 查询排行榜
     */
    @GetMapping("/rank/{type}")
    public Result<List<TopicUserRankVo>> rank(@PathVariable Integer type) {
        List<TopicUserRankVo> topicUserRankVos = topicDataService.rank(type);
        return Result.success(topicUserRankVos);
    }

    /**
     * 获取当前用户排名信息
     */
    @GetMapping("/userRank/{type}")
    public Result<TopicUserRankVo> userRank(@PathVariable Integer type) {
        TopicUserRankVo topicUserRankVo = topicDataService.userRank(type);
        return Result.success(topicUserRankVo);
    }

    /**
     * 查询每日必刷
     */
    @GetMapping("/topicTodayVo")
    public Result<List<TopicTodayVo>> topicTodayVo() {
        List<TopicTodayVo> list = topicDataService.topicTodayVo();
        return Result.success(list);
    }

    /**
     * 已刷题目
     */
    @GetMapping("/flush/{id}")
    public Result<String> flushTopic(@PathVariable Long id) {
        topicDataService.flushTopic(id);
        return Result.success();
    }

    /**
     * 管理员首页左侧顶部系统数据
     */
    @GetMapping("/adminHomeCount")
    public Result<Map<String, Object>> adminHomeData() {
        Map<String, Object> map = topicDataService.adminHomeCount();
        return Result.success(map);
    }

    /**
     * 管理员首页右侧分类数据
     */
    @GetMapping("/adminHomeCategory")
    public Result<List<TopicCategoryDataVo>> adminHomeCategory() {
        List<TopicCategoryDataVo> list = topicDataService.adminHomeCategory();
        return Result.success(list);
    }

    /**
     * 刷题题目和刷题人数趋势图
     */
    @GetMapping("/topicTrend")
    public Result<TopicTrendVo> topicTrend() {
        TopicTrendVo topicTrend = topicDataService.topicTrend();
        return Result.success(topicTrend);
    }


    /**
     * 用户增长趋势图
     */
    @GetMapping("/userTrend")
    public Result<SysUserTrentVo> userTrend() {
        SysUserTrentVo SysUserTrentVo = topicDataService.userTrend();
        return Result.success(SysUserTrentVo);
    }

    /**
     * AI调用次数趋势图
     */
    @GetMapping("/aiTrend")
    public Result<AiTrendVo> aiTrend() {
        AiTrendVo aiTrendVo = topicDataService.aiTrend();
        return Result.success(aiTrendVo);
    }

    /**
     * 用户首页左侧顶部系统数据
     */
    @GetMapping("/userHomeCount")
    public Result<Map<String, Object>> userHomeData() {
        Map<String, Object> map = topicDataService.userHomeCount();
        return Result.success(map);
    }

    /**
     * 用户首页分类数量
     */
    @GetMapping("/userHomeCategory")
    public Result<List<TopicCategoryUserDataVo>> userHomeCategory() {
        List<TopicCategoryUserDataVo> list = topicDataService.userHomeCategory();
        return Result.success(list);
    }

    /**
     * 根据年份统计用户每日刷题次数
     */
    @GetMapping("/userTopicCount/{date}")
    public Result<List<TopicDataVo>> userTopicDateCount(@PathVariable String date) {
        List<TopicDataVo> list = topicDataService.userTopicDateCount(date);
        return Result.success(list);
    }

}
