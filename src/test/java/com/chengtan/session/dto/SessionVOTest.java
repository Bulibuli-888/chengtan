package com.chengtan.session.dto;

import com.chengtan.session.entity.VenueSession;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class SessionVOTest {

    @Test
    void onSale_shouldBeTrueWithinWindowAndWithStock() {
        SessionVO vo = SessionVO.from(session(100, 40, 1,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1)));
        assertThat(vo.getRemaining()).isEqualTo(60);
        assertThat(vo.getOnSale()).isTrue();
    }

    @Test
    void onSale_shouldBeFalseWhenSoldOut() {
        SessionVO vo = SessionVO.from(session(100, 100, 1,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1)));
        assertThat(vo.getRemaining()).isZero();
        assertThat(vo.getOnSale()).isFalse();
    }

    @Test
    void onSale_shouldBeFalseBeforeSaleStart() {
        SessionVO vo = SessionVO.from(session(100, 10, 1,
                LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2)));
        assertThat(vo.getOnSale()).isFalse();
    }

    @Test
    void onSale_shouldBeFalseAfterSaleEnd() {
        SessionVO vo = SessionVO.from(session(100, 10, 1,
                LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1)));
        assertThat(vo.getOnSale()).isFalse();
    }

    @Test
    void onSale_shouldBeFalseWhenSessionNotOnSale() {
        SessionVO vo = SessionVO.from(session(100, 10, 0,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1)));
        assertThat(vo.getOnSale()).isFalse();
    }

    private VenueSession session(int capacity, int sold, int status,
                                 LocalDateTime saleStart, LocalDateTime saleEnd) {
        VenueSession session = new VenueSession();
        session.setId(1L);
        session.setVenueId(1L);
        session.setSessionDate(LocalDate.now().plusDays(1));
        session.setStartTime(LocalTime.of(10, 0));
        session.setEndTime(LocalTime.of(12, 0));
        session.setCapacity(capacity);
        session.setSold(sold);
        session.setPrice(new BigDecimal("88.00"));
        session.setStatus(status);
        session.setSaleStartTime(saleStart);
        session.setSaleEndTime(saleEnd);
        return session;
    }
}
