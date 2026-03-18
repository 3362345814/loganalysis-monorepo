package com.evelin.loganalysis.logcollection.validation;

import com.evelin.loganalysis.logcollection.enums.LogFormat;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class LogPathValidator {

    private static final Pattern WILDCARD_PATTERN = Pattern.compile(".*[\\*\\?].*");

    public ValidationResult validatePaths(LogFormat logFormat, List<String> paths) {
        ValidationResult result = new ValidationResult();
        
        if (paths == null || paths.isEmpty()) {
            result.addError("paths", "日志路径不能为空");
            return result;
        }

        switch (logFormat) {
            case SPRING_BOOT:
                validateSpringBootPaths(paths, result);
                break;
            case NGINX:
            case NGINX_ACCESS:
            case NGINX_ERROR:
                validateNginxPaths(paths, result);
                break;
            case JSON:
            case LOG4J:
            case PLAIN_TEXT:
                validateGenericPaths(paths, result);
                break;
            case CUSTOM:
                validateCustomPaths(paths, result);
                break;
            default:
                result.addError("logFormat", "不支持的日志格式: " + logFormat);
        }

        return result;
    }

    private void validateSpringBootPaths(List<String> paths, ValidationResult result) {
        if (paths.size() != 1) {
            result.addError("paths", "Spring Boot日志只支持单个文件路径");
            return;
        }

        String path = paths.get(0);
        if (containsWildcard(path)) {
            result.addError("paths", "Spring Boot日志不支持文件模式匹配，请使用单个具体文件路径");
        }

        if (!isValidFilePath(path)) {
            result.addError("paths", "文件路径格式不正确: " + path);
        }
    }

    private void validateNginxPaths(List<String> paths, ValidationResult result) {
        if (paths.size() != 2) {
            result.addError("paths", "Nginx日志需要两个文件路径（access.log和error.log）");
            return;
        }

        for (String path : paths) {
            if (containsWildcard(path)) {
                result.addError("paths", "Nginx日志不支持文件模式匹配，请使用具体文件路径");
                break;
            }
            if (!isValidFilePath(path)) {
                result.addError("paths", "文件路径格式不正确: " + path);
                break;
            }
        }
    }

    private void validateGenericPaths(List<String> paths, ValidationResult result) {
        if (paths.isEmpty()) {
            result.addError("paths", "请至少提供一个日志文件路径");
            return;
        }

        for (String path : paths) {
            if (!isValidFilePath(path)) {
                result.addError("paths", "文件路径格式不正确: " + path);
                break;
            }
        }
    }

    private void validateCustomPaths(List<String> paths, ValidationResult result) {
        if (paths.isEmpty()) {
            result.addError("paths", "请至少提供一个日志文件路径");
            return;
        }

        for (String path : paths) {
            if (!isValidFilePath(path)) {
                result.addError("paths", "文件路径格式不正确: " + path);
                break;
            }
        }
    }

    private boolean containsWildcard(String path) {
        return WILDCARD_PATTERN.matcher(path).matches();
    }

    private boolean isValidFilePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        return path.startsWith("/") || path.matches("^[a-zA-Z]:\\\\.*");
    }

    public static class ValidationResult {
        private final Map<String, String> errors = new HashMap<>();

        public void addError(String field, String message) {
            errors.put(field, message);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public Map<String, String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            if (errors.isEmpty()) {
                return "";
            }
            return errors.values().iterator().next();
        }
    }
}
