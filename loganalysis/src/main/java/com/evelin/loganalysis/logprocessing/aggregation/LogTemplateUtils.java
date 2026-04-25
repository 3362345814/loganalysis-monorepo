package com.evelin.loganalysis.logprocessing.aggregation;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志模板化与相似度计算工具。
 */
public final class LogTemplateUtils {

    private static final Pattern STRUCTURED_LOG_MESSAGE_PATTERN = Pattern.compile(
            "^.*\\b(?:TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|CRITICAL)\\b\\s+[\\w.$]+\\s+-\\s+(.*)$"
    );

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "\\b[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}\\b"
    );

    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b"
    );

    private static final Pattern URL_PATTERN = Pattern.compile(
            "\\b(?:https?|ftp)://[^\\s\"']+"
    );

    private static final Pattern PATH_PATTERN = Pattern.compile(
            "(?<!:)\\b(?:/[\\w.\\-{}<>]+)+/?\\b"
    );

    private static final Pattern JSON_BODY_PATTERN = Pattern.compile(
            "\\b(body|payload|response|request)=\\{.*?}",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DYNAMIC_KEY_VALUE_PATTERN = Pattern.compile(
            "\\b([A-Za-z][A-Za-z0-9_.-]*(?:id|Id|ID|no|No|NO|number|Number|NUMBER|code|Code|CODE|token|Token|TOKEN|trace|Trace|TRACE|span|Span|SPAN|url|Url|URL|uri|Uri|URI|path|Path|PATH|body|Body|BODY|payload|Payload|PAYLOAD|total|Total|TOTAL|amount|Amount|AMOUNT|price|Price|PRICE|cost|Cost|COST|time|Time|TIME|timestamp|Timestamp|TIMESTAMP|date|Date|DATE|offset|Offset|OFFSET|line|Line|LINE|port|Port|PORT|host|Host|HOST|addr|Addr|ADDR))=([^\\s,;]+)"
    );

    private static final Pattern LONG_ALPHA_NUMERIC_PATTERN = Pattern.compile(
            "\\b(?=[A-Za-z0-9_-]*\\d)(?=[A-Za-z0-9_-]*[A-Za-z])[A-Za-z0-9_-]{6,}\\b"
    );

    private LogTemplateUtils() {
    }

    public static String normalizeMessage(String message) {
        if (message == null) {
            return "";
        }
        String trimmed = message.trim();
        Matcher matcher = STRUCTURED_LOG_MESSAGE_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return trimmed;
    }

    public static String extractTemplate(String message) {
        if (message == null) {
            return "";
        }

        String template = normalizeMessage(message);
        template = UUID_PATTERN.matcher(template).replaceAll("<UUID>");
        template = IP_PATTERN.matcher(template).replaceAll("<IP>");
        template = URL_PATTERN.matcher(template).replaceAll("<URL>");
        template = JSON_BODY_PATTERN.matcher(template).replaceAll("$1=<JSON>");
        template = DYNAMIC_KEY_VALUE_PATTERN.matcher(template).replaceAll("$1=<VAR>");
        template = PATH_PATTERN.matcher(template).replaceAll("<PATH>");
        template = LONG_ALPHA_NUMERIC_PATTERN.matcher(template).replaceAll("<ID>");
        template = template.replaceAll("\\b\\d+(?:\\.\\d+)?\\b", "<N>");
        template = template.replaceAll("\\s+", " ").trim();

        return template.toLowerCase(Locale.ROOT);
    }

    public static double calculateSimilarity(String message1, String message2) {
        if (message1 == null || message2 == null) {
            return 0.0;
        }

        String template1 = extractTemplate(message1);
        String template2 = extractTemplate(message2);

        if (template1.equals(template2)) {
            return 1.0;
        }

        int distance = levenshteinDistance(template1, template2);
        int maxLength = Math.max(template1.length(), template2.length());
        if (maxLength == 0) {
            return 1.0;
        }

        return 1.0 - (double) distance / maxLength;
    }

    private static int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j], Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }
}
