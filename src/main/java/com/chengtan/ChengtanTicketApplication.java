package com.chengtan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 城探票务 - 城市文旅场馆分时段预约票务平台
 */
@EnableScheduling
@SpringBootApplication
public class ChengtanTicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChengtanTicketApplication.class, args);
    }
}
