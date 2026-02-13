package com.damai.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.damai.order.entity.TicketOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<TicketOrder> {
}
