package com.evelin.loganalysis.logcommon.constant;

/**
 * 错误码常量定义
 *
 * @author Evelin
 */
public interface ErrorCode {

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    int getCode();

    /**
     * 获取错误信息
     *
     * @return 错误信息
     */
    String getMessage();

    /**
     * 成功
     */
    ErrorCode SUCCESS = new ErrorCode() {
        @Override
        public int getCode() {
            return 0;
        }

        @Override
        public String getMessage() {
            return "成功";
        }
    };

    /**
     * 错误码前缀
     */
    int CODE_PREFIX_SUCCESS = 0;
    int CODE_PREFIX_COMMON = 4;
    int CODE_PREFIX_BUSINESS = 1;
    int CODE_PREFIX_COLLECTION = 2;
    int CODE_PREFIX_AI = 3;
    int CODE_PREFIX_ALERT = 4;
    int CODE_PREFIX_RULE = 5;
}
