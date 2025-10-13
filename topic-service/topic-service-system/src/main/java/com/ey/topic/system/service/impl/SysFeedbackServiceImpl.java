package com.ey.topic.system.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.exception.TopicException;
import com.ey.common.security.utils.SecurityUtils;
import com.ey.model.dto.system.SysFeedbackReplyDto;
import com.ey.model.entity.system.SysFeedback;
import com.ey.model.entity.system.SysNotice;
import com.ey.model.vo.system.SysFeedbackUserVo;
import com.ey.model.vo.system.SysFeedbackVo;
import com.ey.topic.system.enums.NoticeEnums;
import com.ey.topic.system.mapper.SysFeedbackMapper;
import com.ey.topic.system.service.SysFeedbackService;
import com.ey.topic.system.service.SysNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SysFeedbackServiceImpl extends ServiceImpl<SysFeedbackMapper, SysFeedback> implements SysFeedbackService {

    private final SysFeedbackMapper sysFeedbackMapper;
    private final SysNoticeService sysNoticeService;

    @Override
    public void sendFeedback(SysFeedback sysFeedbackDto) {
        // 校验反馈内容不能为空
        if (StringUtils.isEmpty(sysFeedbackDto.getFeedbackContent())) {
            throw new TopicException(ResultCodeEnum.PARAM_ACCOUNT_ERROR);
        }
        // 获取用户id
        Long currentId = SecurityUtils.getCurrentId();
        String currentName = SecurityUtils.getCurrentName();
        SysFeedback sysFeedback = new SysFeedback();
        sysFeedback.setUserId(currentId);
        sysFeedback.setAccount(currentName);
        sysFeedback.setFeedbackContent(sysFeedbackDto.getFeedbackContent());
        sysFeedback.setStatus(0);
        sysFeedbackMapper.insert(sysFeedback);
        // 添加到通知表中
        SysNotice sysNotice = new SysNotice();
        sysNotice.setAccount(currentName);
        sysNotice.setUserId(currentId);
        sysNotice.setContent(sysFeedbackDto.getFeedbackContent());
        sysNotice.setStatus(sysFeedbackDto.getStatus());
        sysNoticeService.save(sysNotice);
    }

    /**
     * 获取反馈信息列表
     *
     * @param sysFeedback
     * @return
     */
    @Override
    public Map<String, Object> feedbackList(SysFeedback sysFeedback) {
        // 设置分页参数
        Page<SysFeedback> page = new Page<>(sysFeedback.getPageNum(), sysFeedback.getPageSize());
        // 构造查询条件
        LambdaQueryWrapper<SysFeedback> queryWrapper = new LambdaQueryWrapper<>();
        if (sysFeedback.getStatus() != null) {
            queryWrapper.eq(SysFeedback::getStatus, sysFeedback.getStatus());
        }
        if (StrUtil.isNotBlank(sysFeedback.getAccount())) {
            queryWrapper.eq(SysFeedback::getAccount, sysFeedback.getAccount());
        }
        if (StrUtil.isNotBlank(sysFeedback.getFeedbackContent())) {
            queryWrapper.eq(SysFeedback::getFeedbackContent, sysFeedback.getFeedbackContent());
        }
        // 降序方便查询最新回复
        queryWrapper.orderByDesc(SysFeedback::getCreateTime);
        // 分页查询
        Page<SysFeedback> feedbackPage = sysFeedbackMapper.selectPage(page, queryWrapper);
        return Map.of(
                "total", feedbackPage.getTotal(),
                "rows", feedbackPage.getRecords().stream()
                        .map(pageItem -> {
                            SysFeedbackVo sysFeedbackVo = new SysFeedbackVo();
                            BeanUtils.copyProperties(pageItem, sysFeedbackVo);
                            return sysFeedbackVo;
                        }).toList()
        );
    }

    /**
     * 系统端回复信息
     * @param sysFeedbackReplyDto
     */
    @Override
    @Transactional
    public void reply(SysFeedbackReplyDto sysFeedbackReplyDto) {
        // 校验一下参数，不能回复空数据
        if (sysFeedbackReplyDto.getId() == null || StrUtil.isBlank(sysFeedbackReplyDto.getReplyContent())) {
            throw new TopicException(ResultCodeEnum.FEEDBACK_CONTENT_IS_NULL);
        }
        // 查询一下这条反馈记录
        SysFeedback sysFeedback = sysFeedbackMapper.selectById(sysFeedbackReplyDto.getId());
        if (ObjectUtil.isEmpty(sysFeedback)) {
            throw new TopicException(ResultCodeEnum.FEEDBACK_NOT_EXIST);
        }
        // 反馈记录存在
        sysFeedback.setReplyContent(sysFeedbackReplyDto.getReplyContent());
        sysFeedback.setReplyAccount(SecurityUtils.getCurrentName());
        sysFeedback.setReplyTime(LocalDateTime.now());
        sysFeedback.setReplyId(SecurityUtils.getCurrentId());
        // 修改状态为已回复
        sysFeedback.setStatus(1);
        // 修改
        sysFeedbackMapper.updateById(sysFeedback);

        // 记录到通知表中通知到这个反馈用户已经回复了
        SysNotice sysNotice = new SysNotice();
        // 创建人
        sysNotice.setAccount(sysFeedback.getReplyAccount());
        sysNotice.setUserId(sysFeedback.getReplyId());
        // 通知内容
        sysNotice.setContent(sysFeedback.getReplyContent());
        sysNotice.setStatus(NoticeEnums.REPLY.getCode());
        // 接收人
        sysNotice.setRecipientsId(sysFeedback.getUserId());
        // 插入到通知表
        sysNoticeService.save(sysNotice);
    }

    /**
     * h5查询反馈列表
     * @return
     */
    @Override
    public List<SysFeedbackUserVo> feedback() {
        LambdaQueryWrapper<SysFeedback> sysFeedbackLambdaQueryWrapper = new LambdaQueryWrapper<>();
        sysFeedbackLambdaQueryWrapper.eq(SysFeedback::getUserId, SecurityUtils.getCurrentId());
        sysFeedbackLambdaQueryWrapper.orderByDesc(SysFeedback::getCreateTime);
        List<SysFeedback> sysFeedbacks = sysFeedbackMapper.selectList(sysFeedbackLambdaQueryWrapper);
        return sysFeedbacks.stream().map(sysFeedback -> {
            SysFeedbackUserVo sysFeedbackUserVo = new SysFeedbackUserVo();
            BeanUtils.copyProperties(sysFeedback, sysFeedbackUserVo);
            return sysFeedbackUserVo;
        }).toList();
    }

}
