package com.evelin.loganalysis.logprocessing.desensitization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感信息脱敏服务
 *
 * @author Evelin
 */
@Slf4j
@Component
public class DesensitizationService {

    /**
     * 脱敏规则列表
     */
    private final List<DesensitizeRule> rules;

    public DesensitizationService() {
        this.rules = buildDefaultRules();
    }

    /**
     * 脱敏文本
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    public String desensitize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        for (DesensitizeRule rule : rules) {
            if (rule.isEnabled()) {
                result = applyRule(result, rule);
            }
        }

        return result;
    }

    /**
     * 脱敏日志消息
     *
     * @param message 原始消息
     * @return 脱敏后的消息
     */
    public String desensitizeMessage(String message) {
        return desensitize(message);
    }

    /**
     * 应用脱敏规则
     */
    private String applyRule(String text, DesensitizeRule rule) {
        try {
            Pattern pattern = Pattern.compile(rule.getPattern(), Pattern.CASE_INSENSITIVE);

            return switch (rule.getMaskType().toUpperCase()) {
                case "FULL" -> pattern.matcher(text).replaceAll(rule.getReplacement());
                case "PARTIAL" -> maskPartial(text, pattern, rule.getReplacement());
                case "HASH" -> hashValue(text, pattern);
                default -> text;
            };
        } catch (Exception e) {
            log.warn("Failed to apply desensitization rule: {}", rule.getName(), e);
            return text;
        }
    }

    /**
     * 部分脱敏
     */
    private String maskPartial(String text, Pattern pattern, String replacement) {
        java.util.regex.Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        // 判断是否有捕获组
        boolean hasCaptureGroup = pattern.matcher("").groupCount() > 0;

        if (hasCaptureGroup) {
            // 有捕获组，直接使用 replacement 字符串进行替换
            while (matcher.find()) {
                matcher.appendReplacement(sb, replacement);
            }
            matcher.appendTail(sb);
        } else {
            // 无捕获组，使用 maskString 方法对整个匹配进行脱敏
            while (matcher.find()) {
                String match = matcher.group();
                String masked = maskString(match);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(masked));
            }
            matcher.appendTail(sb);
        }

        return sb.toString();
    }

    /**
     * 脱敏字符串（保留首尾）
     */
    private String maskString(String str) {
        if (str == null || str.length() <= 2) {
            return "**";
        }

        int visibleChars = Math.min(2, str.length() / 4);
        return str.substring(0, visibleChars) +
                "*".repeat(str.length() - visibleChars * 2) +
                str.substring(str.length() - visibleChars);
    }

    /**
     * 哈希处理（简化版）
     */
    private String hashValue(String text, Pattern pattern) {
        java.util.regex.Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String match = matcher.group();
            String hashed = "HASH_" + String.valueOf(match.hashCode()).substring(0, 8);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(hashed));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 构建默认脱敏规则
     */
    private List<DesensitizeRule> buildDefaultRules() {
        List<DesensitizeRule> defaultRules = new ArrayList<>();

        // 手机号脱敏 - 保留前3位和后4位
        defaultRules.add(new DesensitizeRule(
                "phone",
                "手机号脱敏",
                "(1[3-9]\\d)(\\d{4})(\\d{4})",
                "PARTIAL",
                "$1****$3",
                100,
                true
        ));

        // 邮箱脱敏
        defaultRules.add(new DesensitizeRule(
                "email",
                "邮箱脱敏",
                "([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})",
                "PARTIAL",
                "$1***@$2",
                100,
                true
        ));

        // 身份证号脱敏
        defaultRules.add(new DesensitizeRule(
                "idcard",
                "身份证号脱敏",
                "([1-9]\\d{5})(18|19|20)(\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])(\\d{3})(\\d|X|x)",
                "PARTIAL",
                "$1***********$7",
                100,
                true
        ));

        // 密码脱敏 - 使用更灵活的模式
        defaultRules.add(new DesensitizeRule(
                "password",
                "密码脱敏",
                "(password|pwd|passwd)[=:]\\s*([^\\s]+)",
                "FULL",
                "$1=******",
                200,
                true
        ));

        // Token脱敏 - 使用更灵活的模式
        defaultRules.add(new DesensitizeRule(
                "token",
                "Token脱敏",
                "(token|api_key|apikey|secret|authorization)[=:]\\s*([^\\s]+)",
                "FULL",
                "$1=******",
                200,
                true
        ));

        // IP地址脱敏 - 保留前两位
        defaultRules.add(new DesensitizeRule(
                "ip",
                "IP地址脱敏",
                "(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})",
                "PARTIAL",
                "$1.$2.*.*",
                50,
                true
        ));

        // 银行卡号脱敏
        defaultRules.add(new DesensitizeRule(
                "bankcard",
                "银行卡号脱敏",
                "([1-9]\\d{9,19})",
                "PARTIAL",
                "****$1",
                100,
                true
        ));

        return defaultRules;
    }

    /**
     * 脱敏规则内部类
     */
    public static class DesensitizeRule {
        private final String id;
        private final String name;
        private final String pattern;
        private final String maskType;
        private final String replacement;
        private final int priority;
        private final boolean enabled;

        public DesensitizeRule(String id, String name, String pattern, String maskType,
                               String replacement, int priority, boolean enabled) {
            this.id = id;
            this.name = name;
            this.pattern = pattern;
            this.maskType = maskType;
            this.replacement = replacement;
            this.priority = priority;
            this.enabled = enabled;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getPattern() {
            return pattern;
        }

        public String getMaskType() {
            return maskType;
        }

        public String getReplacement() {
            return replacement;
        }

        public int getPriority() {
            return priority;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
