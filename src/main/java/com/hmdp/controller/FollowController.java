package com.hmdp.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@Api(tags = "关注模块")
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    @ApiOperation("关注/取消关注用户")

    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    @GetMapping("/or/not/{id}")
    @ApiOperation("查询是否已关注")

    public Result isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id){
        return followService.followCommons(id);
    }
}
