package com.damai.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.damai.common.constant.MqConstant;
import com.damai.common.constant.RefundStatusConstant;
import com.damai.common.mq.RefundApplyMessage;
import com.damai.order.entity.RefundRecord;
import com.damai.order.entity.TicketOrder;
import com.damai.order.mapper.OrderMapper;
import com.damai.order.mapper.RefundRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRecordMapper refundRecordMapper;
    private final OrderMapper orderMapper;
    private final OutboxService outboxService;

    @Transactional
    public RefundRecord ensureRefund(TicketOrder order, String reason) {
        RefundRecord existing = latestRefund(order.getId());
        if (existing != null) {
            enqueueRefundApply(existing);
            return existing;
        }

        orderMapper.markRefundPending(order.getId());

        RefundRecord refund = new RefundRecord();
        refund.setId(IdWorker.getId());
        refund.setOrderId(order.getId());
        refund.setRefundNo("RF" + refund.getId());
        refund.setRefundAmount(order.getTotalAmount());
        refund.setStatus(RefundStatusConstant.INIT);
        refund.setReason(reason);
        refund.setRetryCount(0);
        refundRecordMapper.insert(refund);
        enqueueRefundApply(refund);
        return refund;
    }

    @Transactional
    public void processRefund(Long refundId) {
        RefundRecord refund = refundRecordMapper.selectById(refundId);
        if (refund == null || refund.getStatus() == RefundStatusConstant.SUCCESS) {
            return;
        }

        refund.setStatus(RefundStatusConstant.PROCESSING);
        refund.setRetryCount((refund.getRetryCount() == null ? 0 : refund.getRetryCount()) + 1);
        refundRecordMapper.updateById(refund);

        refund.setStatus(RefundStatusConstant.SUCCESS);
        refundRecordMapper.updateById(refund);
        orderMapper.markRefunded(refund.getOrderId());
        log.warn("模拟自动退款成功, orderId={}, refundNo={}", refund.getOrderId(), refund.getRefundNo());
    }

    @Transactional
    public void markRefundFailed(Long refundId, String reason) {
        RefundRecord refund = refundRecordMapper.selectById(refundId);
        if (refund == null) {
            return;
        }
        refund.setStatus(RefundStatusConstant.FAILED);
        refund.setReason(reason);
        refundRecordMapper.updateById(refund);
        orderMapper.markRefundFailed(refund.getOrderId());
    }

    public void retryPendingRefunds() {
        List<RefundRecord> refunds = refundRecordMapper.selectList(
                new LambdaQueryWrapper<RefundRecord>()
                        .in(RefundRecord::getStatus, RefundStatusConstant.INIT,
                                RefundStatusConstant.PROCESSING, RefundStatusConstant.FAILED));
        for (RefundRecord refund : refunds) {
            if (refund.getRetryCount() != null && refund.getRetryCount() >= 5) {
                continue;
            }
            enqueueRefundApply(refund);
        }
    }

    private RefundRecord latestRefund(Long orderId) {
        return refundRecordMapper.selectOne(new LambdaQueryWrapper<RefundRecord>()
                .eq(RefundRecord::getOrderId, orderId)
                .orderByDesc(RefundRecord::getCreatedAt)
                .last("LIMIT 1"));
    }

    private void enqueueRefundApply(RefundRecord refund) {
        RefundApplyMessage message = new RefundApplyMessage();
        message.setRefundId(refund.getId());
        message.setOrderId(refund.getOrderId());
        outboxService.saveEvent(MqConstant.EXCHANGE, MqConstant.REFUND_APPLY, message);
    }
}
