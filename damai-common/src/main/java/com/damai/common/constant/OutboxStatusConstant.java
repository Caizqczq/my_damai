package com.damai.common.constant;

public final class OutboxStatusConstant {
    public static final int PENDING = 0;
    public static final int PUBLISHED = 1;
    public static final int FAILED = 2;

    private OutboxStatusConstant() {
    }
}
