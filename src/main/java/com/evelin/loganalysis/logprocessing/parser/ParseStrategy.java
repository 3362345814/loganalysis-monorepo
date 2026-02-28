package com.evelin.loganalysis.logprocessing.parser;

/**
 * 日志解析策略接口
 *
 * @author Evelin
 */
public interface ParseStrategy {

    /**
     * 解析日志内容
     *
     * @param content 原始日志内容
     * @return 解析结果
     */
    ParseResult parse(String content);

    /**
     * 获取支持的日志格式
     *
     * @return 格式名称
     */
    String getFormatName();

    /**
     * 判断是否支持解析此日志
     *
     * @param content 原始日志内容
     * @return 是否支持
     */
    boolean supports(String content);
}
