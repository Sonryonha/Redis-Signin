package com.mrhelloworld.signdemo.controller;

import com.mrhelloworld.signdemo.server.SignService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("sign")
public class SignController {

    @Resource
    private SignService signService;

    /**
     * 用户签到，可以补签
     *
     * @param userId  用户ID
     * @param dateStr 查询的日期，默认当天 yyyy-MM-dd
     * @return 连续签到次数和总签到次数
     */
    @PostMapping
    public Map<String, Object> doSign(Integer userId, String dateStr) {
        return signService.doSign(userId, dateStr);
    }

    /**
     * 获取用户当天签到情况
     *
     * @param userId  用户ID
     * @param dateStr 查询的日期，默认当天 yyyy-MM-dd
     * @return 当天签到情况，连续签到次数和总签到次数
     */
    @GetMapping("today")
    public Map<String, Object> getSignByDate(Integer userId, String dateStr) {
        return signService.getSignByDate(userId, dateStr);
    }

    /**
     * 获取用户当月签到情况
     *
     * @param userId  用户ID
     * @param dateStr 查询的日期，默认当月 yyyy-MM
     * @return
     */
    @GetMapping
    public Map<String, Object> getSignInfo(Integer userId, String dateStr) {
        return signService.getSignInfo(userId, dateStr);
    }

}
