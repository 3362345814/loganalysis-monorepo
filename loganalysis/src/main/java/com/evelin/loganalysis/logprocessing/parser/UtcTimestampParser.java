package com.evelin.loganalysis.logprocessing.parser;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

/**
 * UTC 时间解析工具。
 * 规则：
 * 1. 带时区时间 -> 转换为 UTC
 * 2. 不带时区时间 -> 按 UTC 解释
 */
public final class UtcTimestampParser {

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    private UtcTimestampParser() {
    }

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(UTC);
    }

    public static LocalDateTime parseUtc(String value, DateTimeFormatter... formatters) {
        if (value == null || value.trim().isEmpty()) {
            return nowUtc();
        }

        String trimmed = value.trim();

        LocalDateTime fromEpoch = tryParseEpoch(trimmed);
        if (fromEpoch != null) {
            return fromEpoch;
        }

        try {
            return LocalDateTime.ofInstant(Instant.parse(trimmed), UTC);
        } catch (Exception ignored) {
        }

        try {
            return OffsetDateTime.parse(trimmed).withOffsetSameInstant(UTC).toLocalDateTime();
        } catch (Exception ignored) {
        }

        try {
            return ZonedDateTime.parse(trimmed).withZoneSameInstant(UTC).toLocalDateTime();
        } catch (Exception ignored) {
        }

        if (formatters != null) {
            for (DateTimeFormatter formatter : formatters) {
                if (formatter == null) {
                    continue;
                }
                try {
                    TemporalAccessor parsed = formatter.parseBest(trimmed,
                            OffsetDateTime::from,
                            ZonedDateTime::from,
                            LocalDateTime::from);
                    if (parsed instanceof OffsetDateTime odt) {
                        return odt.withOffsetSameInstant(UTC).toLocalDateTime();
                    }
                    if (parsed instanceof ZonedDateTime zdt) {
                        return zdt.withZoneSameInstant(UTC).toLocalDateTime();
                    }
                    if (parsed instanceof LocalDateTime ldt) {
                        return ldt;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return nowUtc();
    }

    private static LocalDateTime tryParseEpoch(String value) {
        try {
            if (!value.matches("^-?\\d+$")) {
                return null;
            }
            long epoch = Long.parseLong(value);
            Instant instant = value.length() <= 10
                    ? Instant.ofEpochSecond(epoch)
                    : Instant.ofEpochMilli(epoch);
            return LocalDateTime.ofInstant(instant, UTC);
        } catch (Exception ignored) {
            return null;
        }
    }
}
