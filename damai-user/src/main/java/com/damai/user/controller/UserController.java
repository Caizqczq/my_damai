package com.damai.user.controller;

import com.damai.common.result.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    // TODO: 注入 UserService

    @PostMapping("/login")
    public Result<?> login(@RequestBody Object loginRequest) {
        // TODO: 用户登录，返回Token
        return Result.ok();
    }

    @PostMapping("/register")
    public Result<?> register(@RequestBody Object registerRequest) {
        // TODO: 用户注册
        return Result.ok();
    }
}
