package com.damai.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.damai.order.entity.TicketOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OrderMapper extends BaseMapper<TicketOrder> {

    /** 状态机：待支付(0) → 已支付(1)，同时校验未过期 */
    @Update("UPDATE ticket_order SET status = 1, pay_time = #{now}, updated_at = NOW() " +
            "WHERE id = #{id} AND status = 0 AND expire_time > #{now}")
    int payOrder(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 状态机：待支付(0) → 已取消(2) */
    @Update("UPDATE ticket_order SET status = 2, cancel_time = #{now}, updated_at = NOW() " +
            "WHERE id = #{id} AND status = 0")
    int cancelOrder(@Param("id") Long id, @Param("now") LocalDateTime now);
}
