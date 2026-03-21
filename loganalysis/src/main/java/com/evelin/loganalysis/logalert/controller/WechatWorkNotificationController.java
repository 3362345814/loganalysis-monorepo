package com.evelin.loganalysis.logalert.controller;

import com.evelin.loganalysis.logcommon.model.Result;
import com.evelin.loganalysis.logalert.service.WechatWorkNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/alert/wechatwork")
@RequiredArgsConstructor
public class WechatWorkNotificationController {

    private final WechatWorkNotificationService wechatWorkNotificationService;

    @PostMapping("/test")
    public Result<Boolean> testConnection(@RequestBody Map<String, String> config) {
        String webhookUrl = config.get("webhookUrl");

        log.info("测试企业微信连接: webhookUrl={}", webhookUrl != null ? "****" : "null");

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return Result.error("企业微信 webhook URL 不能为空");
        }

        boolean success = wechatWorkNotificationService.testWechatWorkConnection(webhookUrl);

        if (success) {
            return Result.success(true, "企业微信连接测试成功");
        } else {
            return Result.error("企业微信连接测试失败，请检查 webhook URL 是否正确");
        }
    }
}
