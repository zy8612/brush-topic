package com.ey.topic.topic.controller;

import com.alibaba.excel.EasyExcel;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.exception.TopicException;
import com.ey.common.result.Result;
import com.ey.model.dto.topic.TopicCategoryDto;
import com.ey.model.dto.topic.TopicCategoryListDto;
import com.ey.model.entity.topic.TopicCategory;
import com.ey.model.excel.topic.TopicCategoryExcel;
import com.ey.model.excel.topic.TopicCategoryExcelExport;
import com.ey.model.vo.topic.TopicCategoryVo;
import com.ey.service.utils.utils.ExcelUtil;
import com.ey.topic.topic.service.TopicCategoryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Description: 题目分类控制器
 */
@RestController
@RequestMapping("/topic/category")
@AllArgsConstructor
public class TopicCategoryController {

    private final TopicCategoryService topicCategoryService;

    /**
     * 获取题目分类列表
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<Map<String, Object>> listCategory(TopicCategoryListDto topicCategoryDto) {
        Map<String, Object> map = topicCategoryService.categoryList(topicCategoryDto);
        return Result.success(map);
    }

    /**
     * 添加题目分类
     */
    @PostMapping("/add")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<String> addCategory(@RequestBody TopicCategoryDto topicCategoryDto) {
        topicCategoryService.addCategory(topicCategoryDto);
        return Result.success();
    }

    /**
     * 删除题目分类
     */
    @DeleteMapping("/delete/{ids}")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<String> deleteCategory(@PathVariable Long[] ids) {
        topicCategoryService.deleteCategory(ids);
        return Result.success();
    }

    /**
     * 审核修改题目分类
     */
    @PutMapping("/audit")
    public void auditCategory(@RequestBody TopicCategory topicCategory) {
        topicCategoryService.auditCategory(topicCategory);
    }

    /**
     * 修改题目分类
     */
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<String> updateCategory(@RequestBody TopicCategoryDto topicCategoryDto) {
        topicCategoryService.updateCategory(topicCategoryDto);
        return Result.success();
    }

    /**
     * h5查询分类名称和id
     */
    @GetMapping("/category/{isCustomQuestion}")
    public Result<List<TopicCategoryVo>> category(@PathVariable Boolean isCustomQuestion) {
        List<TopicCategoryVo> list = topicCategoryService.category(isCustomQuestion);
        return Result.success(list);
    }

    /**
     * 导出excel
     */
    @GetMapping("/export/{ids}")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public void exportExcel(HttpServletResponse response, TopicCategoryListDto topicCategoryListDto, @PathVariable(required = false) Long[] ids) {
        List<TopicCategoryExcelExport> topicCategoryExcelExports = topicCategoryService.getExcelVo(topicCategoryListDto, ids);
        if (CollectionUtils.isEmpty(topicCategoryExcelExports)) {
            throw new TopicException(ResultCodeEnum.EXPORT_ERROR);
        }
        // 导出
        try {
            ExcelUtil.download(response, topicCategoryExcelExports, TopicCategoryExcelExport.class);
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
            List<TopicCategoryExcel> excelVoList = EasyExcel.read(multipartFile.getInputStream())
                    // 映射数据
                    .head(TopicCategoryExcel.class)
                    // 读取工作表
                    .sheet()
                    // 读取并同步返回数据
                    .doReadSync();
            // 校验
            if (CollectionUtils.isEmpty(excelVoList)) {
                throw new TopicException(ResultCodeEnum.IMPORT_ERROR);
            }
            // 封装数据插入到数据库中
            String s = topicCategoryService.importExcel(excelVoList, updateSupport);
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
        List<TopicCategoryExcel> excelVoList = new ArrayList<>();
        // 组成模板数据
        TopicCategoryExcel excelVo = new TopicCategoryExcel();
        // 存放
        excelVoList.add(excelVo);
        try {
            // 导出
            ExcelUtil.download(response, excelVoList, TopicCategoryExcel.class);
        } catch (IOException e) {
            throw new TopicException(ResultCodeEnum.DOWNLOAD_ERROR);
        }
    }
}
