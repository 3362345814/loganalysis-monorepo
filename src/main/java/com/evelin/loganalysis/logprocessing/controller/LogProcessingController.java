package com.evelin.loganalysis.logprocessing.controller;

import com.evelin.loganalysis.logcommon.model.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 日志处理控制器
 *
 * @author Evelin
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/processing")
@RequiredArgsConstructor
public class LogProcessingController {

    /**
     * 获取处理管道状态
     *
     * @return 状态信息
     */
    @GetMapping("/status")
    public Result<Map<String, String>> getStatus() {
        return Result.success(Map.of(
                "status", "RUNNING",
                "module", "logprocessing"
        ));
    }
}
