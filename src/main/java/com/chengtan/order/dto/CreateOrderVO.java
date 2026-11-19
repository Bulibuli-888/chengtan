package com.chengtan.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class CreateOrderVO implements Serializable {

    private String orderNo;
    private String status;
}
