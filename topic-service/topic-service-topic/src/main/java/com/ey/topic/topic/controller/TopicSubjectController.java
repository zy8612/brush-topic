package com.ey.topic.topic.controller;

import com.alibaba.excel.EasyExcel;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.exception.TopicException;
import com.ey.common.result.Result;
import com.ey.model.dto.topic.TopicSubjectDto;
import com.ey.model.dto.topic.TopicSubjectListDto;
import com.ey.model.entity.topic.TopicSubject;
import com.ey.model.excel.topic.TopicSubjectExcel;
import com.ey.model.excel.topic.TopicSubjectExcelExport;
import com.ey.model.vo.system.TopicSubjectWebVo;
import com.ey.model.vo.topic.TopicSubjectDetailAndTopicVo;
import com.ey.model.vo.topic.TopicSubjectVo;
import com.ey.service.utils.helper.MinioHelper;
import com.ey.service.utils.utils.ExcelUtil;
import com.ey.topic.topic.service.TopicSubjectService;
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
 * Description: 题目专题控制层
 */
@RestController
@RequestMapping("/topic/subject")
@RequiredArgsConstructor
public class TopicSubjectController {

    private final TopicSubjectService topicSubjectService;
    private final MinioHelper minioHelper;

    /**
     * 查询所有的专题名称以及id
     */
    @GetMapping("/getSubject")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<List<TopicSubjectVo>> getSubject() {
        List<TopicSubjectVo> list = topicSubjectService.getAllSubject();
        return Result.success(list);
    }

    /**
     * 获取专题列表
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<Map<String, Object>> listSubject(TopicSubjectListDto topicSubjectListDto) {
        Map<String, Object> map = topicSubjectService.subjectList(topicSubjectListDto);
        return Result.success(map);
    }

    /**
     * 添加题目专题
     */
    @PostMapping("/add")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<String> addSubject(@RequestBody TopicSubjectDto topicCategoryDto) {
        topicSubjectService.addSubject(topicCategoryDto);
        return Result.success();
    }

    /**
     * 删除题目专题
     */
    @DeleteMapping("/delete/{ids}")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<String> deleteSubject(@PathVariable Long[] ids) {
        topicSubjectService.deleteSubject(ids);
        return Result.success();
    }

    /**
     * 审核专题
     */
    @PutMapping("/audit")
    public void auditSubject(@RequestBody TopicSubject topicSubject) {
        topicSubjectService.auditSubject(topicSubject);
    }

    /**
     * 修改题目专题
     */
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<String> updateSubject(@RequestBody TopicSubjectDto topicSubjectDto) {
        topicSubjectService.updateSubject(topicSubjectDto);
        return Result.success();
    }

    /**
     * 根据专题id查询专题详细信息和题目列表
     */
    @GetMapping("/subjectDetail/{id}")
    public Result<TopicSubjectDetailAndTopicVo> subjectDetail(@PathVariable Long id) {
        TopicSubjectDetailAndTopicVo topicSubjectDetailAndTopicVo = topicSubjectService.subjectDetail(id);
        return Result.success(topicSubjectDetailAndTopicVo);
    }

    /**
     * h5根据分类id查询专题
     */
    @GetMapping("/subject/{categoryId}")
    public Result<List<TopicSubjectWebVo>> subject(@PathVariable Long categoryId) {
        List<TopicSubjectWebVo> list = topicSubjectService.subject(categoryId);
        return Result.success(list);
    }

    /**
     * 文件上传
     */
    @PostMapping("/img")
    @PreAuthorize("hasAuthority('admin')  || hasAuthority('member')")
    public Result<String> upload(@RequestParam("avatar") MultipartFile file) {
        // 上传文件
        String url = minioHelper.uploadFile(file, "subject");
        return Result.success(url);
    }

    /**
     * 导出excel
     */
    @GetMapping("/export/{ids}")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public void exportExcel(HttpServletResponse response, TopicSubjectListDto topicSubjectListDto, @PathVariable(required = false) Long[] ids) {
        List<TopicSubjectExcelExport> topicSubjectExcelExports = topicSubjectService.getExcelVo(topicSubjectListDto, ids);
        if (CollectionUtils.isEmpty(topicSubjectExcelExports)) {
            throw new TopicException(ResultCodeEnum.EXPORT_ERROR);
        }
        // 导出
        try {
            ExcelUtil.download(response, topicSubjectExcelExports, TopicSubjectExcelExport.class);
        } catch (IOException e) {
            throw new TopicException(ResultCodeEnum.EXPORT_ERROR);
        }
    }

    /**
     * 导入excel
     */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<String> importExcel(@RequestParam("file") MultipartFile multipartFile, @RequestParam("updateSupport") Boolean updateSupport) {
        try {
            // 读取数据
            List<TopicSubjectExcel> excelVoList = EasyExcel.read(multipartFile.getInputStream())
                    // 映射数据
                    .head(TopicSubjectExcel.class)
                    // 读取工作表
                    .sheet()
                    // 读取并同步返回数据
                    .doReadSync();
            // 导入数据
            String s = topicSubjectService.importExcel(excelVoList, updateSupport);
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
        // 存储模板数据
        List<TopicSubjectExcel> excelVoList = new ArrayList<>();
        // 组成模板数据
        TopicSubjectExcel excelVo = new TopicSubjectExcel();
        // 存放
        excelVoList.add(excelVo);
        try {
            // 导出
            ExcelUtil.download(response, excelVoList, TopicSubjectExcel.class);
        } catch (IOException e) {
            throw new TopicException(ResultCodeEnum.DOWNLOAD_ERROR);
        }

    }
}
