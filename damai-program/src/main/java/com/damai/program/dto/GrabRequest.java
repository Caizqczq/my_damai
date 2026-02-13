package com.damai.program.dto;


import lombok.Data;

@Data
public class GrabRequest {
    private Long programId;
    private Long categoryId;
    private Long quantity;
}
