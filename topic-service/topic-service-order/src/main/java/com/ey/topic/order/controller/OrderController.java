package com.ey.topic.order.controller;

import com.ey.common.result.Result;
import com.ey.model.dto.order.MemberOrderDto;
import com.ey.model.entity.order.MemberOrder;
import com.ey.topic.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public Result<MemberOrder> createOrder(@RequestBody MemberOrderDto memberOrderDto) {
        MemberOrder memberOrder = orderService.createOrder(memberOrderDto);
        return Result.success(memberOrder);
    }

    @PostMapping("/paySuccess")
    public Result<String> paySuccess(@RequestBody MemberOrderDto memberOrderDto) {
        orderService.paySuccess(memberOrderDto);
        return Result.success();
    }
}
