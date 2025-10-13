package com.ey.topic.system.controller;

import com.ey.common.result.Result;
import com.ey.model.dto.system.SysFeedbackReplyDto;
import com.ey.model.entity.system.SysFeedback;
import com.ey.model.vo.system.SysFeedbackUserVo;
import com.ey.topic.system.service.SysFeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system/feedback")
@RequiredArgsConstructor
public class SysFeedbackController {

    private final SysFeedbackService sysFeedbackService;

    /**
     * 发送反馈
     */
    @PostMapping("/send")
    public Result<String> sendFeedback(@RequestBody SysFeedback sysFeedback) {
        sysFeedbackService.sendFeedback(sysFeedback);
        return Result.success();
    }

    /**
     * 系统端查询反馈列表
     *
     * @return
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('admin')")
    public Result<Map<String, Object>> list(SysFeedback sysFeedback) {
        Map<String, Object> map = sysFeedbackService.feedbackList(sysFeedback);
        return Result.success(map);
    }

    /**
     * 系统端回复
     *
     * @param sysFeedbackReplyDto
     * @return
     */
    @PostMapping("/reply")
    public Result<String> reply(@RequestBody SysFeedbackReplyDto sysFeedbackReplyDto) {
        sysFeedbackService.reply(sysFeedbackReplyDto);
        return Result.success();
    }

    /**
     * h5查询反馈列表
     */
    @GetMapping("/feedback")
    public Result<List<SysFeedbackUserVo>> feedback() {
        List<SysFeedbackUserVo> list = sysFeedbackService.feedback();
        return Result.success(list);
    }
}
