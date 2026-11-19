package com.chengtan.session.dto;

import com.chengtan.session.entity.VenueSession;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class SessionVO implements Serializable {

    private Long id;
    private Long venueId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate sessionDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    private BigDecimal price;
    private Integer capacity;
    private Integer sold;
    private Integer remaining;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime saleStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime saleEndTime;

    /**
     * 前端直接用这个字段决定按钮是“立即抢票”还是“已售罄/未开售”
     */
    private Boolean onSale;

    public static SessionVO from(VenueSession session) {
        SessionVO vo = new SessionVO();
        vo.setId(session.getId());
        vo.setVenueId(session.getVenueId());
        vo.setSessionDate(session.getSessionDate());
        vo.setStartTime(session.getStartTime());
        vo.setEndTime(session.getEndTime());
        vo.setPrice(session.getPrice());
        vo.setCapacity(session.getCapacity());
        vo.setSold(session.getSold());
        int remaining = Math.max(0, session.getCapacity() - session.getSold());
        vo.setRemaining(remaining);
        vo.setStatus(session.getStatus());
        vo.setSaleStartTime(session.getSaleStartTime());
        vo.setSaleEndTime(session.getSaleEndTime());
        LocalDateTime now = LocalDateTime.now();
        boolean inWindow = !now.isBefore(session.getSaleStartTime()) && !now.isAfter(session.getSaleEndTime());
        vo.setOnSale(session.getStatus() == 1 && remaining > 0 && inWindow);
        return vo;
    }
}
