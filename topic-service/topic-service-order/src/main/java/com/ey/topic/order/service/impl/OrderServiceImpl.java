package com.ey.topic.order.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ey.client.security.SecurityFeignClient;
import com.ey.client.system.SystemFeignClient;
import com.ey.common.enums.ResultCodeEnum;
import com.ey.common.exception.TopicException;
import com.ey.common.interceptor.TokenInterceptor;
import com.ey.common.security.utils.SecurityUtils;
import com.ey.model.dto.order.MemberOrderDto;
import com.ey.model.dto.system.SysNoticeDto;
import com.ey.model.dto.system.SysUserDto;
import com.ey.model.entity.order.MemberOrder;
import com.ey.model.entity.system.SysUser;
import com.ey.topic.order.mapper.OrderMapper;
import com.ey.topic.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, MemberOrder> implements OrderService {

    private final OrderMapper orderMapper;
    private final SecurityFeignClient securityFeignClient;
    private final SystemFeignClient systemFeignClient;

    /**
     * 创建订单
     *
     * @param memberOrderDto
     * @return
     */
    @Override
    public MemberOrder createOrder(MemberOrderDto memberOrderDto) {
        LambdaQueryWrapper<MemberOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MemberOrder::getUserId, memberOrderDto.getUserId());
        // 创建过了就直接返回
        MemberOrder memberOrder = orderMapper.selectOne(queryWrapper);
        if (ObjectUtil.isNull(memberOrder)) {
            memberOrder = new MemberOrder();
            memberOrder.setUserId(memberOrderDto.getUserId());
            memberOrder.setPrice(memberOrderDto.getPrice());
            orderMapper.insert(memberOrder);
        }
        return memberOrder;
    }

    /**
     * 支付成功逻辑
     *
     * @param memberOrderDto
     */
    @Transactional
    @Override
    public void paySuccess(MemberOrderDto memberOrderDto) {
        // 将订单状态改为已支付
        LambdaQueryWrapper<MemberOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MemberOrder::getUserId, memberOrderDto.getUserId())
                .eq(MemberOrder::getId, memberOrderDto.getId());
        MemberOrder memberOrder = orderMapper.selectOne(queryWrapper);
        if (ObjectUtil.isNull(memberOrder)) {
            throw new TopicException(ResultCodeEnum.ORDER_NOT_EXIST);
        }
        memberOrder.setStatus(1);
        orderMapper.updateById(memberOrder);
        // 将用户权限修改为会员
        SysUser sysUser = securityFeignClient.getByUserId(memberOrderDto.getUserId());
        SysUserDto sysUserDto = new SysUserDto();
        sysUserDto.setRoleId(3L);
        sysUserDto.setRoleName("会员");
        BeanUtil.copyProperties(sysUser, sysUserDto);
        securityFeignClient.updateUser(sysUserDto);
        // 发送一个通知记录用户支付会员
        SysNoticeDto sysNoticeDto = new SysNoticeDto();
        sysNoticeDto.setStatus(0);
        sysNoticeDto.setUserId(memberOrderDto.getUserId());
        
        // 获取当前请求的token并设置到Feign拦截器中
        String token = SecurityUtils.getToken();
        TokenInterceptor.setToken(token);
        
        systemFeignClient.recordNotice(sysNoticeDto);
        
        // 请求完成后清除token，避免线程安全问题
        TokenInterceptor.clearToken();
    }

}
