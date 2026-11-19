package com.chengtan.auth.service;

import com.chengtan.auth.UserPrincipal;
import com.chengtan.auth.dto.LoginRequest;
import com.chengtan.auth.dto.LoginVO;

public interface AuthService {

    void sendCode(String phone);

    LoginVO login(LoginRequest request);

    void logout(String token);

    UserPrincipal current();
}
