package com.damai.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.damai.order.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {
}
