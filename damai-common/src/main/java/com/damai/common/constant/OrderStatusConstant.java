package com.damai.common.constant;

public final class OrderStatusConstant {
    public static final int WAIT_PAY = 0;
    public static final int PAID = 1;
    public static final int CLOSED = 2;
    public static final int REFUND_PENDING = 3;
    public static final int REFUNDED = 4;
    public static final int REFUND_FAILED = 5;

    private OrderStatusConstant() {
    }
}
