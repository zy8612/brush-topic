package com.ey.topic.topic.controller;

import com.alibaba.excel.EasyExcel;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.exception.TopicException;
import com.ey.common.result.Result;
import com.ey.model.dto.topic.TopicLabelDto;
import com.ey.model.dto.topic.TopicLabelListDto;
import com.ey.model.excel.topic.TopicLabelExcel;
import com.ey.model.excel.topic.TopicLabelExcelExport;
import com.ey.model.vo.topic.TopicLabelVo;
import com.ey.service.utils.utils.ExcelUtil;
import com.ey.topic.topic.service.TopicLabelService;
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
 * Description: 题目标签控制层
 */
@RestController
@RequestMapping("/topic/label")
@RequiredArgsConstructor
public class TopicLabelController {

    private final TopicLabelService topicLabelService;

    /**
     * 查询所有的标签名称以及id
     * @return
     */
    @GetMapping("/getLabel")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<List<TopicLabelVo>> getLabel() {
        List<TopicLabelVo> list = topicLabelService.getAllLabel();
        return Result.success(list);
    }

    /**
     * 获取题目标签列表
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<Map<String, Object>> listLabel(TopicLabelListDto topicLabelListDto) {
        Map<String, Object> map = topicLabelService.labelList(topicLabelListDto);
        return Result.success(map);
    }

    /**
     * 添加题目标签
     */
    @PostMapping("/add")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<String> addLabel(@RequestBody TopicLabelDto topicLabelDto) {
        topicLabelService.addLabel(topicLabelDto);
        return Result.success();
    }

    /**
     * 删除题目标签
     */
    @DeleteMapping("/delete/{ids}")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<String> deleteLabel(@PathVariable Long[] ids) {
        topicLabelService.deleteLabel(ids);
        return Result.success();
    }

    /**
     * 修改题目标签
     */
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public Result<String> updateLabel(@RequestBody TopicLabelDto topicLabelDto) {
        topicLabelService.updateLabel(topicLabelDto);
        return Result.success();
    }

    /**
     * 导出excel
     */
    @GetMapping("/export/{ids}")
    @PreAuthorize("hasAuthority('admin') || hasAuthority('member')")
    public void exportExcel(HttpServletResponse response, TopicLabelListDto topicLabelListDto, @PathVariable(required = false) Long[] ids) {
        List<TopicLabelExcelExport> topicLabelExcelExport = topicLabelService.getExcelVo(topicLabelListDto, ids);
        if (CollectionUtils.isEmpty(topicLabelExcelExport)) {
            throw new TopicException(ResultCodeEnum.EXPORT_ERROR);
        }
        // 导出
        try {
            ExcelUtil.download(response, topicLabelExcelExport, TopicLabelExcelExport.class);
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
            List<TopicLabelExcel> excelVoList = EasyExcel.read(multipartFile.getInputStream())
                    // 映射数据
                    .head(TopicLabelExcel.class)
                    // 读取工作表
                    .sheet()
                    // 读取并同步返回数据
                    .doReadSync();
            // 校验
            if (CollectionUtils.isEmpty(excelVoList)) {
                throw new TopicException(ResultCodeEnum.IMPORT_ERROR);
            }
            // 导入数据
            String s = topicLabelService.importExcel(excelVoList, updateSupport);
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
        List<TopicLabelExcel> excelVoList = new ArrayList<>();
        // 组成模板数据
        TopicLabelExcel excelVo = new TopicLabelExcel();
        // 存放
        excelVoList.add(excelVo);
        try {
            // 导出
            ExcelUtil.download(response, excelVoList, TopicLabelExcel.class);
        } catch (IOException e) {
            throw new TopicException(ResultCodeEnum.DOWNLOAD_ERROR);
        }

    }
}
