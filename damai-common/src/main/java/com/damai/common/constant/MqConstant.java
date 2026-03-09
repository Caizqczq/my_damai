package com.damai.common.constant;

public class MqConstant {

    public static final String EXCHANGE = "damai.direct";
    public static final String DLX = "damai.dlx";

    // 队列名 = 路由键（direct exchange 惯例）
    public static final String ORDER_CREATE = "order.create";
    public static final String ORDER_CREATE_DLQ = "order.create.dlq";
    public static final String STOCK_RESTORE = "stock.restore";
    public static final String STOCK_RESTORE_DLQ = "stock.restore.dlq";
    public static final String SEAT_ALLOCATE = "seat.allocate";
    public static final String SEAT_ALLOCATE_DLQ = "seat.allocate.dlq";
    public static final String REFUND_APPLY = "refund.apply";
    public static final String REFUND_APPLY_DLQ = "refund.apply.dlq";

    // 订单延迟取消（TTL 到期自动进 check 队列）
    public static final String ORDER_DELAY = "order.delay";
    public static final String ORDER_DELAY_CHECK = "order.delay.check";
    public static final int ORDER_EXPIRE_MINUTES = 15;

    private MqConstant() {}
}
