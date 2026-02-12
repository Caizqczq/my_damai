package com.damai.user.controller;

import com.damai.common.result.Result;
import com.damai.user.dto.LoginRequest;
import com.damai.user.dto.LoginResponse;
import com.damai.user.dto.RegisterRequest;
import com.damai.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.ok(userService.login(request));
    }

    @PostMapping("/register")
    public Result<?> register(@RequestBody RegisterRequest request) {
        userService.register(request);
        return Result.ok();
    }
}
