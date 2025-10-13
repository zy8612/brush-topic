package com.ey.topic.system.service.impl;

import com.alibaba.fastjson2.util.DateUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.enums.RoleEnum;
import com.ey.common.exception.TopicException;
import com.ey.common.security.utils.SecurityUtils;
import com.ey.model.dto.system.SysNoticeDto;
import com.ey.model.dto.system.SysNoticeReadDto;
import com.ey.model.entity.system.SysNotice;
import com.ey.model.vo.system.SysNoticeVo;
import com.ey.service.utils.constant.TimeUtils;
import com.ey.topic.system.constant.NoticeConstant;
import com.ey.topic.system.enums.NoticeEnums;
import com.ey.topic.system.mapper.SysNoticeMapper;
import com.ey.topic.system.service.SysNoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SysNoticeServiceImpl extends ServiceImpl<SysNoticeMapper, SysNotice> implements SysNoticeService {

    private final SysNoticeMapper sysNoticeMapper;

    @Override
    public List<SysNoticeVo> listNotice() {
        // 获取当前登录用户
        Long currentId = SecurityUtils.getCurrentId();
        // 获取当前登录用户角色
        String currentRole = SecurityUtils.getCurrentRole();
        List<SysNotice> sysNoticeList = null;
        if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
            // 是管理员那就查全部
            LambdaQueryWrapper<SysNotice> sysNoticeLambdaQueryWrapper = new LambdaQueryWrapper<>();
            sysNoticeLambdaQueryWrapper.orderByDesc(SysNotice::getCreateTime);
            sysNoticeLambdaQueryWrapper.eq(SysNotice::getIsRead, 0);
            sysNoticeList = sysNoticeMapper.selectList(sysNoticeLambdaQueryWrapper);
        } else {
            // 不是管理员那就查接收人是不是自己哦
            LambdaQueryWrapper<SysNotice> sysNoticeLambdaQueryWrapper = new LambdaQueryWrapper<>();
            sysNoticeLambdaQueryWrapper.eq(SysNotice::getRecipientsId, currentId);
            sysNoticeLambdaQueryWrapper.eq(SysNotice::getIsRead, 0);
            sysNoticeLambdaQueryWrapper.orderByDesc(SysNotice::getCreateTime);
            // 非管理员只能接收状态为2，回复内容的通知
            sysNoticeLambdaQueryWrapper.eq(SysNotice::getStatus, NoticeEnums.REPLY.getCode());
            sysNoticeList = sysNoticeMapper.selectList(sysNoticeLambdaQueryWrapper);
        }
        return sysNoticeList.stream().map(item -> {
            SysNoticeVo sysNoticeVo = new SysNoticeVo();
            BeanUtils.copyProperties(item, sysNoticeVo);
            // 处理一下时间 判断是否是当天额外处理一下
            String today = DateUtils.format(item.getCreateTime(), "yyyy-MM-dd");
            if (today.equals(DateUtils.format(new Date(), "yyyy-MM-dd"))) {
                sysNoticeVo.setTimeDesc(TimeUtils.formatTimeAgo(item.getCreateTime()));
            } else {
                // 不是当天直接返回 格式化一下
                sysNoticeVo.setTimeDesc(DateUtils.format(item.getCreateTime(), "yyyy-MM-dd HH:mm:ss"));
            }
            return sysNoticeVo;
        }).toList();
    }

    @Override
    public void read(SysNoticeReadDto sysNoticeReadDto) {
        if (CollectionUtils.isEmpty(sysNoticeReadDto.getIdsList())) {
            throw new TopicException(ResultCodeEnum.FAIL);
        }
        sysNoticeReadDto.getIdsList().forEach(item -> {
            SysNotice sysNotice = new SysNotice();
            sysNotice.setId(item);
            sysNotice.setIsRead(1);
            sysNoticeMapper.updateById(sysNotice);
        });
    }

    /**
     * 查询是否有消息
     */
    @Override
    public Boolean has() {
        // 获取当前登录用户
        Long currentId = SecurityUtils.getCurrentId();
        // 获取当前登录用户角色
        String currentRole = SecurityUtils.getCurrentRole();
        LambdaQueryWrapper<SysNotice> sysNoticeLambdaQueryWrapper = new LambdaQueryWrapper<>();
        if (currentRole.equals(RoleEnum.ADMIN.getRoleKey())) {
            // 是管理员那就查全部
            sysNoticeLambdaQueryWrapper.orderByDesc(SysNotice::getCreateTime);
            sysNoticeLambdaQueryWrapper.eq(SysNotice::getIsRead, 0);
            Long count = sysNoticeMapper.selectCount(sysNoticeLambdaQueryWrapper);
            return count > 0;
        } else {
            // 不是管理员那就查接收人是不是自己哦
            sysNoticeLambdaQueryWrapper.eq(SysNotice::getRecipientsId, currentId);
            sysNoticeLambdaQueryWrapper.eq(SysNotice::getIsRead, 0);
            sysNoticeLambdaQueryWrapper.orderByDesc(SysNotice::getCreateTime);
            // 非管理员只能接受到2回复内容
            sysNoticeLambdaQueryWrapper.eq(SysNotice::getStatus, NoticeEnums.REPLY.getCode());
            Long count = sysNoticeMapper.selectCount(sysNoticeLambdaQueryWrapper);
            return count > 0;
        }
    }

    /**
     * 清空通知
     */
    @Override
    public void clearNotice() {
        // 获取当前登录用户
        Long currentId = SecurityUtils.getCurrentId();
        // 查询接收人的通知
        // 是管理员那就查全部
        LambdaQueryWrapper<SysNotice> sysNoticeLambdaQueryWrapper = new LambdaQueryWrapper<>();
        sysNoticeLambdaQueryWrapper.eq(SysNotice::getIsRead, 0);
        sysNoticeLambdaQueryWrapper.eq(SysNotice::getRecipientsId, currentId);
        List<SysNotice> sysNotices = sysNoticeMapper.selectList(sysNoticeLambdaQueryWrapper);
        if (CollectionUtils.isEmpty(sysNotices)) {
            return;
        }
        sysNotices.forEach(item -> {
            item.setIsRead(1);
            sysNoticeMapper.updateById(item);
        });
    }

    @Override
    public void recordNotice(SysNoticeDto sysNoticeDto) {
        if (sysNoticeDto.getStatus() == null) {
            throw new TopicException(ResultCodeEnum.FAIL);
        }
        Long currentId = SecurityUtils.getCurrentId();
        // 查询message
        String message = NoticeEnums.getMessageByCode(sysNoticeDto.getStatus());
        // 开始记录
        SysNotice sysNoticeDb = new SysNotice();
        sysNoticeDb.setAccount(SecurityUtils.getCurrentName());
        sysNoticeDb.setUserId(sysNoticeDto.getUserId());
        sysNoticeDb.setUserId(currentId);
        sysNoticeDb.setStatus(sysNoticeDto.getStatus());
        // 是否是支付通知
        if (Objects.equals(sysNoticeDto.getStatus(), NoticeEnums.MEMBER_PAY.getCode())) {
            // 判断这个人是否记录过支付了
            LambdaQueryWrapper<SysNotice> sysNoticeLambdaQueryWrapper = new LambdaQueryWrapper<>();
            sysNoticeLambdaQueryWrapper.eq(SysNotice::getStatus, NoticeEnums.MEMBER_PAY.getCode());
            sysNoticeLambdaQueryWrapper.eq(SysNotice::getUserId, currentId);
            SysNotice sysNotice = sysNoticeMapper.selectOne(sysNoticeLambdaQueryWrapper);
            if (sysNotice != null) {
                // 已存在用户支付通知
                return;
            }
            // 不存在记录支付内容
            sysNoticeDb.setContent(NoticeConstant.RECHARGE_MEMBER);
        } else {
            // 不是支付
            sysNoticeDb.setContent(message);
        }
        sysNoticeMapper.insert(sysNoticeDb);
    }


}
