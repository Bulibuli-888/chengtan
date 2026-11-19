package com.chengtan.attendee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AttendeeCreateRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 32, message = "姓名过长")
    private String name;

    @NotBlank(message = "证件号不能为空")
    @Pattern(regexp = "^\\d{17}[\\dXx]$", message = "身份证号格式不正确")
    private String idCard;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
