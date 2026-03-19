package com.evelin.loganalysis.logalert.controller;

import com.evelin.loganalysis.logcommon.model.Result;
import com.evelin.loganalysis.logalert.service.FeishuNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/alert/feishu")
@RequiredArgsConstructor
public class FeishuNotificationController {

    private final FeishuNotificationService feishuNotificationService;

    @PostMapping("/test")
    public Result<Boolean> testConnection(@RequestBody Map<String, String> config) {
        String webhookUrl = config.get("webhookUrl");
        String secret = config.get("secret");

        log.info("测试飞书连接: webhookUrl={}", webhookUrl != null ? "****" : "null");

        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return Result.error("飞书 webhook URL 不能为空");
        }

        boolean success = feishuNotificationService.testFeishuConnection(webhookUrl, secret);

        if (success) {
            return Result.success(true, "飞书连接测试成功");
        } else {
            return Result.error("飞书连接测试失败，请检查 webhook URL 和 secret 是否正确");
        }
    }
}
