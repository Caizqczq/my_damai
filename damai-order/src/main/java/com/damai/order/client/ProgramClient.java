package com.damai.order.client;

import com.damai.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "damai-program", path = "/api/program/internal")
public interface ProgramClient {

    @PostMapping("/releaseSeats")
    Result<?> releaseSeats(@RequestParam("programId") Long programId,
                           @RequestParam("categoryId") Long categoryId,
                           @RequestBody List<Long> seatIds);

    @PostMapping("/confirmSeats")
    Result<?> confirmSeats(@RequestParam("programId") Long programId,
                           @RequestParam("categoryId") Long categoryId,
                           @RequestBody List<Long> seatIds);
}
