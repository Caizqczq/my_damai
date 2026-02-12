package com.damai.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.damai.common.constant.RedisKeyConstant;
import com.damai.common.exception.BizException;
import com.damai.user.dto.LoginRequest;
import com.damai.user.dto.LoginResponse;
import com.damai.user.dto.RegisterRequest;
import com.damai.user.entity.User;
import com.damai.user.mapper.UserMapper;
import com.damai.user.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername()));

        if (user == null || !BCrypt.checkpw(request.getPassword(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            throw new BizException(403, "账号已被禁用");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // token 存入 Redis，支持主动失效（登出）
        redisTemplate.opsForValue().set(
                RedisKeyConstant.USER_TOKEN + user.getId(),
                token, 24, TimeUnit.HOURS);

        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .build();
    }

    public void register(RegisterRequest request) {
        boolean exists = userMapper.exists(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername()));
        if (exists) {
            throw new BizException(400, "用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
        user.setPhone(request.getPhone());
        user.setStatus(1);
        userMapper.insert(user);
    }
}