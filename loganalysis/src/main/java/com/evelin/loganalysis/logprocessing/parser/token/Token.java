package com.evelin.loganalysis.logprocessing.parser.token;

import com.evelin.loganalysis.logprocessing.parser.ParseResult;
import com.evelin.loganalysis.logprocessing.parser.context.ParseContext;

/**
 * 日志格式 Token 基类
 * 支持 %d{} / %thread / %X{} / %level / %-5level / %logger{} / %msg / %n 等占位符
 *
 * 核心方法：
 * - toRegex(): 生成正则表达式（保留用于检测日志格式）
 * - parse(context, result): 基于 delimiter 解析日志内容
 * - getFieldName(): 获取字段名称
 *
 * @author Evelin
 */
public abstract class Token {

    /**
     * Token 类型
     */
    protected String type;

    /**
     * 结束分隔符（自动推导）
     * 例如 DATE token 后面紧跟 " ["，则 endDelimiter = " ["
     */
    protected String endDelimiter;

    /**
     * 开始分隔符（可选，用于处理特殊格式如 [%thread]）
     * 当 endDelimiter 的开头与实际的 leading 不一致时使用
     * 例如：[%thread] 的 endDelimiter 是 "] "，leading 是 "["
     */
    protected String leadingDelimiter;

    /**
     * 下一个 Token 的 delimiter 信息（用于传递 leading/end）
     * 例如：DATE 后面的 literal 是 " ["，我们需要把 "[" 传给 ThreadToken
     */
    protected String nextLeadingDelimiter;
    protected String nextEndDelimiter;

    public String getType() {
        return type;
    }

    public String getEndDelimiter() {
        return endDelimiter;
    }

    public void setEndDelimiter(String endDelimiter) {
        this.endDelimiter = endDelimiter;
    }

    public String getLeadingDelimiter() {
        return leadingDelimiter;
    }

    public void setLeadingDelimiter(String leadingDelimiter) {
        this.leadingDelimiter = leadingDelimiter;
    }

    public boolean hasNextTokenDelimiter() {
        return nextLeadingDelimiter != null || nextEndDelimiter != null;
    }

    public void setNextTokenDelimiter(String leading, String end) {
        this.nextLeadingDelimiter = leading;
        this.nextEndDelimiter = end;
    }

    public String[] getNextTokenDelimiter() {
        return new String[]{nextLeadingDelimiter, nextEndDelimiter};
    }

    /**
     * 生成用于正则表达式的模式（保留用于格式检测）
     */
    public abstract String toRegex();

    /**
     * 获取字段名称（用于解析结果 Map）
     */
    public abstract String getFieldName();

    /**
     * 基于 delimiter 解析日志内容
     * 子类可以重写此方法实现自定义解析逻辑
     *
     * @param context 解析上下文
     * @param result  解析结果（用于填充字段）
     */
    public void parse(ParseContext context, ParseResult result) {
        String rawValue;

        // 处理 leading delimiter（如跳过 "["）
        if (leadingDelimiter != null && !leadingDelimiter.isEmpty()) {
            context.skipLiteral(leadingDelimiter);
            rawValue = context.readUntil(endDelimiter);
        } else {
            rawValue = context.readUntil(endDelimiter);
        }

        // 处理 endDelimiter（跳过它）
        if (endDelimiter != null) {
            context.skipLiteral(endDelimiter);
        }

        // 填充解析结果
        fillResult(result, rawValue);
    }

    /**
     * 填充解析结果
     * 子类可以重写此方法实现自定义的填充逻辑
     *
     * @param result   解析结果
     * @param rawValue 原始值
     */
    protected void fillResult(ParseResult result, String rawValue) {
        String fieldName = getFieldName();
        if (fieldName != null && rawValue != null) {
            result.getFields().put(fieldName, rawValue.trim());
        }
    }
}
