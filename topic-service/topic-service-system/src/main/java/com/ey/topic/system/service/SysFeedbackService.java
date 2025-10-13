package com.ey.topic.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ey.model.dto.system.SysFeedbackReplyDto;
import com.ey.model.entity.system.SysFeedback;
import com.ey.model.vo.system.SysFeedbackUserVo;

import java.util.List;
import java.util.Map;

public interface SysFeedbackService extends IService<SysFeedback> {

    Map<String, Object> feedbackList(SysFeedback sysFeedback);

    void reply(SysFeedbackReplyDto sysFeedbackReplyDto);

    List<SysFeedbackUserVo> feedback();

    void sendFeedback(SysFeedback sysFeedback);
}
