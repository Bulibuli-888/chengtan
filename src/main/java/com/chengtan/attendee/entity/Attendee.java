package com.chengtan.attendee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("ct_attendee")
public class Attendee implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    /**
     * 证件号掩码，如 3301**********1234
     */
    private String idCardMask;

    /**
     * SHA-256(userId + 证件号 + salt)，用于唯一约束，不存明文
     */
    private String idCardHash;

    private String phone;

    private LocalDateTime createTime;
}
