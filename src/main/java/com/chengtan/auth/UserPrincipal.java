package com.chengtan.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String phone;
    private String nickName;
    private Integer role;
}
