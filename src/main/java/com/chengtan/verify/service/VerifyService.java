package com.chengtan.verify.service;

import com.chengtan.verify.dto.VerifyRequest;
import com.chengtan.verify.dto.VerifyResultVO;

public interface VerifyService {

    VerifyResultVO verify(VerifyRequest request);
}
