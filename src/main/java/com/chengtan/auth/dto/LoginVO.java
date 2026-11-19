package com.chengtan.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginVO {

    private String token;
    private Long userId;
    private String nickName;
    private Integer role;
}
