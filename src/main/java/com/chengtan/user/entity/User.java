package com.chengtan.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("ct_user")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String phone;

    private String nickName;

    private String icon;

    /**
     * 0 普通用户，1 场馆运营
     */
    private Integer role;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
