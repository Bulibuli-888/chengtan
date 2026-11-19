package com.chengtan.waitlist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("ct_waitlist")
public class Waitlist implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long userId;

    private Integer quantity;

    /**
     * 0排队中 1已通知待支付 2已转订单 3已取消 4已过期
     */
    private Integer status;

    private LocalDateTime notifyTime;

    private LocalDateTime offerExpireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
