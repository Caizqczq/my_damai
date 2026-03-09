package com.damai.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.damai.order.entity.TicketOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OrderMapper extends BaseMapper<TicketOrder> {

    @Update("UPDATE ticket_order SET status = 1, pay_time = #{now}, updated_at = NOW() " +
            "WHERE id = #{id} AND status = 0 AND expire_time > #{now}")
    int payOrder(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE ticket_order SET status = 2, cancel_time = #{now}, updated_at = NOW() " +
            "WHERE id = #{id} AND status = 0")
    int cancelOrder(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE ticket_order SET seat_info = #{seatInfo}, updated_at = NOW() WHERE id = #{id}")
    int updateSeatInfo(@Param("id") Long id, @Param("seatInfo") String seatInfo);

    @Update("UPDATE ticket_order SET status = 3, updated_at = NOW() WHERE id = #{id} AND status IN (1,2,5)")
    int markRefundPending(@Param("id") Long id);

    @Update("UPDATE ticket_order SET status = 4, updated_at = NOW() WHERE id = #{id} AND status = 3")
    int markRefunded(@Param("id") Long id);

    @Update("UPDATE ticket_order SET status = 5, updated_at = NOW() WHERE id = #{id} AND status = 3")
    int markRefundFailed(@Param("id") Long id);
}
