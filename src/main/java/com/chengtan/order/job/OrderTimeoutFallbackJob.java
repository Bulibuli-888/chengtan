package com.chengtan.order.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengtan.order.entity.TicketOrder;
import com.chengtan.order.mapper.TicketOrderMapper;
import com.chengtan.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时兜底：即使 MQ 延迟消息丢了，也能靠扫表把未支付订单取消。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutFallbackJob {

    private final TicketOrderMapper orderMapper;
    private final OrderService orderService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void scan() {
        List<TicketOrder> list = orderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getStatus, 10)
                .lt(TicketOrder::getExpireTime, LocalDateTime.now())
                .orderByAsc(TicketOrder::getId)
                .last("LIMIT 100"));
        if (list.isEmpty()) {
            return;
        }
        log.info("超时兜底扫描到 {} 笔未支付订单", list.size());
        for (TicketOrder order : list) {
            try {
                orderService.cancelExpired(order.getOrderNo());
            } catch (Exception e) {
                log.error("兜底取消失败, orderNo={}", order.getOrderNo(), e);
            }
        }
    }
}
