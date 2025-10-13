package com.ey.topic.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ey.model.dto.order.MemberOrderDto;
import com.ey.model.entity.order.MemberOrder;

public interface OrderService extends IService<MemberOrder> {
    MemberOrder createOrder(MemberOrderDto memberOrderDto);

    void paySuccess(MemberOrderDto memberOrderDto);
}
