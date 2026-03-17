package com.evelin.loganalysis.logalert.controller;

import com.evelin.loganalysis.logalert.dto.AlertRuleCreateRequest;
import com.evelin.loganalysis.logalert.dto.AlertRuleResponse;
import com.evelin.loganalysis.logalert.dto.AlertRuleUpdateRequest;
import com.evelin.loganalysis.logalert.service.AlertRuleService;
import com.evelin.loganalysis.logcommon.model.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 告警规则控制器
 *
 * @author Evelin
 */
@RestController
@RequestMapping("/api/v1/alert/rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    /**
     * 创建告警规则
     */
    @PostMapping
    public Result<AlertRuleResponse> createRule(@Valid @RequestBody AlertRuleCreateRequest request) {
        AlertRuleResponse response = alertRuleService.createRule(request);
        return Result.success(response);
    }

    /**
     * 更新告警规则
     */
    @PutMapping("/{id}")
    public Result<AlertRuleResponse> updateRule(@PathVariable UUID id,
                                                @RequestBody AlertRuleUpdateRequest request) {
        AlertRuleResponse response = alertRuleService.updateRule(id, request);
        return Result.success(response);
    }

    /**
     * 删除告警规则
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteRule(@PathVariable UUID id) {
        alertRuleService.deleteRule(id);
        return Result.success(null);
    }

    /**
     * 获取规则详情
     */
    @GetMapping("/{id}")
    public Result<AlertRuleResponse> getRule(@PathVariable UUID id) {
        AlertRuleResponse response = alertRuleService.getRule(id);
        return Result.success(response);
    }

    /**
     * 获取所有规则
     */
    @GetMapping
    public Result<List<AlertRuleResponse>> getAllRules() {
        List<AlertRuleResponse> response = alertRuleService.getAllRules();
        return Result.success(response);
    }

    /**
     * 分页查询规则
     */
    @GetMapping("/page")
    public Result<Page<AlertRuleResponse>> getRules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<AlertRuleResponse> response = alertRuleService.getRules(pageable);
        return Result.success(response);
    }

    /**
     * 启用/禁用规则
     */
    @PutMapping("/{id}/toggle")
    public Result<AlertRuleResponse> toggleRule(@PathVariable UUID id) {
        AlertRuleResponse response = alertRuleService.toggleRule(id);
        return Result.success(response);
    }

    /**
     * 根据规则类型查询
     */
    @GetMapping("/type/{ruleType}")
    public Result<List<AlertRuleResponse>> getRulesByType(@PathVariable String ruleType) {
        List<AlertRuleResponse> response = alertRuleService.getRulesByType(
                com.evelin.loganalysis.logalert.enums.RuleType.valueOf(ruleType.toUpperCase()));
        return Result.success(response);
    }

    /**
     * 根据告警级别查询
     */
    @GetMapping("/level/{alertLevel}")
    public Result<List<AlertRuleResponse>> getRulesByLevel(@PathVariable String alertLevel) {
        List<AlertRuleResponse> response = alertRuleService.getRulesByLevel(
                com.evelin.loganalysis.logalert.enums.AlertLevel.valueOf(alertLevel.toUpperCase()));
        return Result.success(response);
    }
}
