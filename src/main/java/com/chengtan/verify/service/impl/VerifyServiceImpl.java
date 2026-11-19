package com.chengtan.verify.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chengtan.auth.UserContext;
import com.chengtan.auth.UserPrincipal;
import com.chengtan.common.BizException;
import com.chengtan.common.ErrorCode;
import com.chengtan.common.enums.OrderStatus;
import com.chengtan.common.enums.TicketStatus;
import com.chengtan.order.entity.TicketOrder;
import com.chengtan.order.mapper.TicketOrderMapper;
import com.chengtan.ticket.entity.Ticket;
import com.chengtan.ticket.mapper.TicketMapper;
import com.chengtan.verify.dto.VerifyRequest;
import com.chengtan.verify.dto.VerifyResultVO;
import com.chengtan.verify.entity.VerifyLog;
import com.chengtan.verify.mapper.VerifyLogMapper;
import com.chengtan.verify.service.VerifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyServiceImpl implements VerifyService {

    private final TicketMapper ticketMapper;
    private final TicketOrderMapper orderMapper;
    private final VerifyLogMapper verifyLogMapper;

    @Override
    @Transactional
    public VerifyResultVO verify(VerifyRequest request) {
        UserPrincipal operator = UserContext.requireOperator();
        Ticket ticket = ticketMapper.selectOne(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getQrToken, request.getQrToken()));
        if (ticket == null) {
            throw new BizException(ErrorCode.TICKET_NOT_FOUND, "核销码无效");
        }
        if (ticket.getStatus() == TicketStatus.VERIFIED.getCode()) {
            return result(ticket, true);
        }
        if (ticket.getStatus() != TicketStatus.UNUSED.getCode()) {
            throw new BizException(ErrorCode.TICKET_NOT_REFUNDABLE, "该票已退款或已作废，不能核销");
        }

        LocalDateTime now = LocalDateTime.now();
        VerifyLog verifyLog = new VerifyLog();
        verifyLog.setTicketId(ticket.getId());
        verifyLog.setTicketNo(ticket.getTicketNo());
        verifyLog.setOperatorId(operator.getId());
        verifyLog.setRequestId(request.getRequestId());
        verifyLog.setVerifyTime(now);
        try {
            verifyLogMapper.insert(verifyLog);
        } catch (DuplicateKeyException e) {
            VerifyLog existing = verifyLogMapper.selectOne(new LambdaQueryWrapper<VerifyLog>()
                    .eq(VerifyLog::getRequestId, request.getRequestId()));
            if (existing == null) {
                existing = verifyLogMapper.selectOne(new LambdaQueryWrapper<VerifyLog>()
                        .eq(VerifyLog::getTicketId, ticket.getId()));
            }
            if (existing != null) {
                Ticket verified = ticketMapper.selectById(existing.getTicketId());
                return result(verified, true);
            }
            throw new BizException(ErrorCode.CONFLICT, "核销冲突，请重试");
        }

        int updated = ticketMapper.update(null, new LambdaUpdateWrapper<Ticket>()
                .eq(Ticket::getId, ticket.getId())
                .eq(Ticket::getStatus, TicketStatus.UNUSED.getCode())
                .set(Ticket::getStatus, TicketStatus.VERIFIED.getCode())
                .set(Ticket::getVerifyTime, now)
                .set(Ticket::getVerifyBy, operator.getId())
                .set(Ticket::getUpdateTime, now));
        if (updated == 0) {
            Ticket latest = ticketMapper.selectById(ticket.getId());
            return result(latest, true);
        }

        long unused = ticketMapper.selectCount(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getOrderId, ticket.getOrderId())
                .eq(Ticket::getStatus, TicketStatus.UNUSED.getCode()));
        if (unused == 0) {
            orderMapper.update(null, new LambdaUpdateWrapper<TicketOrder>()
                    .eq(TicketOrder::getId, ticket.getOrderId())
                    .eq(TicketOrder::getStatus, OrderStatus.PAID.getCode())
                    .set(TicketOrder::getStatus, OrderStatus.COMPLETED.getCode())
                    .set(TicketOrder::getUpdateTime, now));
        }
        log.info("核销成功, ticketNo={}, orderNo={}, operator={}",
                ticket.getTicketNo(), ticket.getOrderNo(), operator.getId());
        ticket.setStatus(TicketStatus.VERIFIED.getCode());
        ticket.setVerifyTime(now);
        return result(ticket, false);
    }

    private VerifyResultVO result(Ticket ticket, boolean idempotent) {
        return new VerifyResultVO(ticket.getTicketNo(), ticket.getOrderNo(),
                ticket.getVerifyTime(), idempotent);
    }
}
