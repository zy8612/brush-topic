package com.ey.topic.system.controller;

import com.ey.common.result.Result;
import com.ey.model.dto.system.SysNoticeDto;
import com.ey.model.dto.system.SysNoticeReadDto;
import com.ey.model.vo.system.SysNoticeVo;
import com.ey.topic.system.service.SysNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Description: 通知控制层
 */
@Slf4j
@RestController
@RequestMapping("/system/notice")
@RequiredArgsConstructor
public class SysNoticeController {

    private final SysNoticeService sysNoticeService;

    /**
     * 查询反馈通知列表
     */
    @GetMapping("/list")
    public Result<List<SysNoticeVo>> listNotice() {
        List<SysNoticeVo> sysNoticeVos = sysNoticeService.listNotice();
        return Result.success(sysNoticeVos);
    }

    /**
     * 已读通知
     */
    @PutMapping("/read")
    public Result<String> read(@RequestBody SysNoticeReadDto sysNoticeReadDto) {
        sysNoticeService.read(sysNoticeReadDto);
        return Result.success();
    }

    /**
     * 查询反馈通知是否有
     */
    @GetMapping("/has")
    public Result<Boolean> has() {
        Boolean isHas = sysNoticeService.has();
        return Result.success(isHas);
    }

    /**
     * h5清空通知
     */
    @PutMapping("/clear")
    public Result<String> clearNotice() {
        sysNoticeService.clearNotice();
        return Result.success();
    }

    /**
     * 记录通知
     */
    @PostMapping("/record")
    public Result<String> recordNotice(@RequestBody SysNoticeDto sysNoticeDto) {
        sysNoticeService.recordNotice(sysNoticeDto);
        return Result.success();
    }
}
