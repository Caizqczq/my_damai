package com.damai.program.client;

import com.damai.common.result.Result;
import com.damai.program.dto.OrderCreateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "damai-order", path = "/api/order/internal")
public interface OrderClient {

    @PostMapping("/create")
    Result<Map<String, Object>> createOrder(@RequestBody OrderCreateRequest request);
}
