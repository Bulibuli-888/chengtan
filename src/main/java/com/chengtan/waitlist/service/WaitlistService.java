package com.chengtan.waitlist.service;

import com.chengtan.order.dto.CreateOrderVO;
import com.chengtan.waitlist.dto.WaitlistClaimRequest;
import com.chengtan.waitlist.dto.WaitlistJoinRequest;
import com.chengtan.waitlist.dto.WaitlistVO;

import java.util.List;

public interface WaitlistService {

    WaitlistVO join(WaitlistJoinRequest request);

    List<WaitlistVO> my();

    void cancel(Long id);

    CreateOrderVO claim(Long id, WaitlistClaimRequest request);

    void onStockReleased(Long sessionId);

    void expireOffers();
}
