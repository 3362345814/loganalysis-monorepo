package com.evelin.loganalysis.logcollection.util;

import com.evelin.loganalysis.logcommon.enums.LogFormat;
import com.evelin.loganalysis.logcommon.model.LogSource;

import java.util.*;

public class LogPathSerializer {

    public static Map<String, Object> serializePaths(LogFormat logFormat, List<String> paths, String logFormatPattern) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", logFormat.name());
        
        if (paths != null && !paths.isEmpty()) {
            result.put("paths", paths);
        }

        if (logFormatPattern != null && !logFormatPattern.trim().isEmpty()) {
            result.put("logFormatPattern", logFormatPattern);
        }

        return result;
    }

    public static Map<String, Object> serializeFromLegacy(String path, String filePattern, String logFormatPattern) {
        Map<String, Object> result = new HashMap<>();
        
        List<String> paths = new ArrayList<>();
        
        if (path != null && !path.trim().isEmpty()) {
            paths.add(path);
        }
        
        if (filePattern != null && !filePattern.trim().isEmpty()) {
            String[] patterns = filePattern.split(",");
            for (String pattern : patterns) {
                String trimmed = pattern.trim();
                if (!trimmed.isEmpty() && !paths.contains(trimmed)) {
                    paths.add(trimmed);
                }
            }
        }
        
        if (!paths.isEmpty()) {
            result.put("paths", paths);
        }
        
        if (logFormatPattern != null && !logFormatPattern.trim().isEmpty()) {
            result.put("logFormatPattern", logFormatPattern);
        }
        
        return result;
    }

    @SuppressWarnings("unchecked")
    public static List<String> deserializePaths(Map<String, Object> pathsMap) {
        if (pathsMap == null || pathsMap.isEmpty()) {
            return Collections.emptyList();
        }
        
        Object pathsObj = pathsMap.get("paths");
        if (pathsObj instanceof List) {
            return (List<String>) pathsObj;
        }
        
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public static String deserializeLogFormatPattern(Map<String, Object> pathsMap) {
        if (pathsMap == null) {
            return null;
        }
        
        Object patternObj = pathsMap.get("logFormatPattern");
        if (patternObj instanceof String) {
            return (String) patternObj;
        }
        
        return null;
    }

    public static Map<String, Object> createPathsMap(LogFormat logFormat, List<String> paths) {
        return createPathsMap(logFormat, paths, null);
    }

    public static Map<String, Object> createPathsMap(LogFormat logFormat, List<String> paths, String logFormatPattern) {
        Map<String, Object> map = new HashMap<>();
        
        if (logFormat != null) {
            map.put("type", logFormat.name());
        }
        
        if (paths != null && !paths.isEmpty()) {
            map.put("paths", paths);
        }
        
        if (logFormatPattern != null && !logFormatPattern.isEmpty()) {
            map.put("logFormatPattern", logFormatPattern);
        }
        
        return map;
    }

    public static String getFirstPath(Map<String, Object> pathsMap) {
        List<String> paths = deserializePaths(pathsMap);
        return paths.isEmpty() ? null : paths.get(0);
    }

    public static String getSecondPath(Map<String, Object> pathsMap) {
        List<String> paths = deserializePaths(pathsMap);
        return paths.size() > 1 ? paths.get(1) : null;
    }

    public static boolean hasMultiplePaths(Map<String, Object> pathsMap) {
        List<String> paths = deserializePaths(pathsMap);
        return paths.size() > 1;
    }
}
