package com.ey.topic.topic.controller;

import com.alibaba.excel.EasyExcel;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.exception.TopicException;
import com.ey.common.result.Result;
import com.ey.common.security.utils.SecurityUtils;
import com.ey.model.dto.topic.TopicDto;
import com.ey.model.dto.topic.TopicListDto;
import com.ey.model.dto.topic.TopicRecordCountDto;
import com.ey.model.entity.topic.Topic;
import com.ey.model.excel.topic.TopicExcel;
import com.ey.model.excel.topic.TopicExcelExport;
import com.ey.model.excel.topic.TopicMemberExcel;
import com.ey.model.vo.topic.TopicAnswerVo;
import com.ey.model.vo.topic.TopicCollectionVo;
import com.ey.model.vo.topic.TopicDetailVo;
import com.ey.service.utils.utils.ExcelUtil;
import com.ey.topic.topic.service.TopicService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Description: 题目控制层
 */
@RestController
@RequestMapping("/topic/topic")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    /**
     * 查询题目列表
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<Map<String, Object>> topicList(TopicListDto topicListDto) {
        Map<String, Object> map = topicService.topicList(topicListDto);
        return Result.success(map);
    }

    /**
     * 新增题目
     */
    @PostMapping("/add")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<String> addTopic(@RequestBody TopicDto topicDto) {
        topicService.addTopic(topicDto);
        return Result.success();
    }

    /**
     * 删除题目
     */
    @DeleteMapping("/delete/{ids}")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<String> deleteTopic(@PathVariable Long[] ids) {
        topicService.deleteTopic(ids);
        return Result.success();
    }

    /**
     * 根据题目id查询题目详情和标签
     */
    @GetMapping("/detail/{id}")
    public Result<TopicDetailVo> detail(@PathVariable Long id) {
        TopicDetailVo topicDetailVo = topicService.detail(id);
        return Result.success(topicDetailVo);
    }

    /**
     * 修改题目
     */
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<String> update(@RequestBody TopicDto topicDto) {
        topicService.updateTopic(topicDto);
        return Result.success();
    }

    /**
     * 发送消息生成ai答案
     */
    @GetMapping("/generateAnswer/{id}")
    public void generateAnswer(@PathVariable Long id) {
        topicService.generateAnswer(id);
    }

    /**
     * 修改ai答案
     */
    @PutMapping("/answer")
    void updateAiAnswer(@RequestBody Topic topic) {
        topicService.updateAiAnswer(topic);
    }


    /**
     * 计算用户刷题次数
     */
    @PostMapping("/count")
    public Result<String> count(@RequestBody TopicRecordCountDto topicRecordCountDto) {
        topicService.setCount(topicRecordCountDto);
        return Result.success();
    }

    /**
     * 获取答案
     */
    @GetMapping("/answer/{id}")
    public Result<TopicAnswerVo> getAnswer(@PathVariable Long id) {
        TopicAnswerVo topicAnswerVo = topicService.getAnswer(id);
        return Result.success(topicAnswerVo);
    }

    /**
     * 根据题目id收藏题目
     */
    @GetMapping("/collection/{id}")
    public Result<String> collection(@PathVariable Long id) {
        topicService.collection(id);
        return Result.success();
    }

    /**
     * 查询收藏的题目
     */
    @GetMapping("/collection/list")
    public Result<List<TopicCollectionVo>> collectionList() {
        List<TopicCollectionVo> topicCollectionVos = topicService.collectionList();
        return Result.success(topicCollectionVos);
    }

    /**
     * 导出excel
     *
     * @param response
     */
    @GetMapping("/export/{ids}")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public void exportExcel(HttpServletResponse response, TopicListDto topicListDto, @PathVariable(required = false) Long[] ids) {
        List<TopicExcelExport> topicExcelExports = topicService.getExcelVo(topicListDto, ids);
        if (CollectionUtils.isEmpty(topicExcelExports)) {
            throw new TopicException(ResultCodeEnum.EXPORT_ERROR);
        }
        // 导出
        try {
            ExcelUtil.download(response, topicExcelExports, TopicExcelExport.class);
        } catch (IOException e) {
            throw new TopicException(ResultCodeEnum.EXPORT_ERROR);
        }
    }


    /**
     * 导入excel会员
     */
    @PostMapping("/memberImport")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<String> memberImport(@RequestParam("file") MultipartFile multipartFile, @RequestParam("updateSupport") Boolean updateSupport) {
        try {
            // 读取数据
            List<TopicMemberExcel> excelVoList = EasyExcel.read(multipartFile.getInputStream())
                    // 映射数据
                    .head(TopicMemberExcel.class)
                    // 读取工作表
                    .sheet()
                    // 读取并同步返回数据
                    .doReadSync();
            // 导入数据
            String s = topicService.memberImport(excelVoList, updateSupport);
            return Result.success(s);
        } catch (Exception e) {
            return Result.fail(e.getMessage(), ResultCodeEnum.IMPORT_ERROR);
        }
    }

    /**
     * 导入excel管理员
     */
    @PostMapping("/adminImport")
    @PreAuthorize("hasAuthority('admin')")
    public Result<String> adminImport(@RequestParam("file") MultipartFile multipartFile, @RequestParam("updateSupport") Boolean updateSupport) {
        try {
            // 读取数据
            List<TopicExcel> excelVoList = EasyExcel.read(multipartFile.getInputStream())
                    // 映射数据
                    .head(TopicExcel.class)
                    // 读取工作表
                    .sheet()
                    // 读取并同步返回数据
                    .doReadSync();
            // 导入数据
            String s = topicService.adminImport(excelVoList, updateSupport);
            return Result.success(s);
        } catch (Exception e) {
            return Result.fail(e.getMessage(), ResultCodeEnum.IMPORT_ERROR);
        }
    }

    /**
     * 下载excel模板
     */
    @GetMapping("/template")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public void getExcelTemplate(HttpServletResponse response) {
        // 获取当前用户身份
        String role = SecurityUtils.getCurrentRole();
        if (role.equals("admin")) {
            List<TopicExcel> topicExcels = new ArrayList<>();
            // 组成模板数据
            TopicExcel topicExcel = new TopicExcel();
            // 存放
            topicExcels.add(topicExcel);
            try {
                // 导出
                ExcelUtil.download(response, topicExcels, TopicExcel.class);
            } catch (IOException e) {
                throw new TopicException(ResultCodeEnum.DOWNLOAD_ERROR);
            }
        } else {
            // 存储模板数据
            List<TopicMemberExcel> excelVoList = new ArrayList<>();
            // 组成模板数据
            TopicMemberExcel excelVo = new TopicMemberExcel();
            // 存放
            excelVoList.add(excelVo);
            try {
                // 导出
                ExcelUtil.download(response, excelVoList, TopicMemberExcel.class);
            } catch (IOException e) {
                throw new TopicException(ResultCodeEnum.DOWNLOAD_ERROR);
            }
        }
    }
}
