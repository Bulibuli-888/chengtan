package com.chengtan.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 本地消息表：Redis 扣减成功后先落这张表，再由 Relay 投递 RabbitMQ。
 * 这样即使 MQ 暂时不可用，库存和消息也不会丢。
 */
@Data
@TableName("ct_order_outbox")
public class OrderOutbox implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String messageId;

    private String orderNo;

    private String exchangeName;

    private String routingKey;

    private String payload;

    /**
     * 0待发送 1已发送 2失败
     */
    private Integer status;

    private Integer retryCount;

    private LocalDateTime nextRetryTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
