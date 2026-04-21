package com.operit.apkreverse.runtime;

import org.json.JSONArray;
import org.json.JSONObject;
import pxb.android.axml.Axml;
import pxb.android.axml.AxmlReader;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ManifestSupport {
    private ManifestSupport() {
    }

    static ManifestInfo parseManifest(byte[] bytes) throws Exception {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.trim().startsWith("<")) {
            return parseTextManifest(text);
        }
        return parseBinaryManifest(bytes);
    }

    static String renderManifestText(ManifestInfo info) {
        StringBuilder builder = new StringBuilder();
        builder.append("<manifest package=\"").append(safe(info.packageName))
                .append("\" android:versionName=\"").append(safe(info.versionName))
                .append("\" android:versionCode=\"").append(safe(info.versionCode))
                .append("\">\n");
        builder.append("  <uses-sdk android:minSdkVersion=\"").append(safe(info.minSdkVersion))
                .append("\" android:targetSdkVersion=\"").append(safe(info.targetSdkVersion))
                .append("\" android:maxSdkVersion=\"").append(safe(info.maxSdkVersion))
                .append("\" />\n");
        appendTagLines(builder, "uses-permission", info.permissions);
        appendTagLines(builder, "uses-feature", info.features);
        builder.append("  <application android:label=\"").append(safe(info.applicationLabel)).append("\">\n");
        appendComponentLines(builder, "activity", info.activities);
        appendComponentLines(builder, "activity-alias", info.activityAliases);
        appendComponentLines(builder, "service", info.services);
        appendComponentLines(builder, "receiver", info.receivers);
        appendComponentLines(builder, "provider", info.providers);
        builder.append("  </application>\n");
        builder.append("</manifest>");
        return builder.toString();
    }

    static JSONObject toJson(ManifestInfo info) throws Exception {
        JSONObject object = new JSONObject();
        object.put("packageName", info.packageName);
        object.put("versionName", info.versionName);
        object.put("versionCode", info.versionCode);
        object.put("minSdkVersion", info.minSdkVersion);
        object.put("targetSdkVersion", info.targetSdkVersion);
        object.put("maxSdkVersion", info.maxSdkVersion);
        object.put("applicationLabel", info.applicationLabel);
        object.put("permissions", new JSONArray(info.permissions));
        object.put("features", new JSONArray(info.features));
        object.put("activities", new JSONArray(info.activities));
        object.put("activityAliases", new JSONArray(info.activityAliases));
        object.put("services", new JSONArray(info.services));
        object.put("receivers", new JSONArray(info.receivers));
        object.put("providers", new JSONArray(info.providers));
        object.put("manifestPreview", renderManifestText(info));
        return object;
    }

    static int resolveMinSdkVersion(byte[] bytes, int fallbackValue) {
        try {
            ManifestInfo info = parseManifest(bytes);
            if (info.minSdkVersion == null || info.minSdkVersion.isBlank()) {
                return fallbackValue;
            }
            int parsed = Integer.parseInt(info.minSdkVersion.trim());
            return parsed > 0 ? parsed : fallbackValue;
        } catch (Exception ignored) {
            return fallbackValue;
        }
    }

    private static ManifestInfo parseTextManifest(String manifestText) {
        ManifestInfo info = new ManifestInfo();
        info.packageName = firstGroup(manifestText, "<manifest\\b[^>]*\\bpackage=\"([^\"]+)\"");
        info.versionName = firstGroup(manifestText, "\\bandroid:versionName=\"([^\"]+)\"");
        info.versionCode = firstGroup(manifestText, "\\bandroid:versionCode=\"([^\"]+)\"");
        info.minSdkVersion = firstGroup(manifestText, "<uses-sdk\\b[^>]*\\bandroid:minSdkVersion=\"([^\"]+)\"");
        info.targetSdkVersion = firstGroup(manifestText, "<uses-sdk\\b[^>]*\\bandroid:targetSdkVersion=\"([^\"]+)\"");
        info.maxSdkVersion = firstGroup(manifestText, "<uses-sdk\\b[^>]*\\bandroid:maxSdkVersion=\"([^\"]+)\"");
        info.applicationLabel = firstGroup(manifestText, "<application\\b[^>]*\\bandroid:label=\"([^\"]+)\"");
        info.permissions.addAll(collectNameMatches(manifestText, "<uses-permission\\b[^>]*\\bandroid:name=\"([^\"]+)\"", info.packageName, false));
        info.features.addAll(collectNameMatches(manifestText, "<uses-feature\\b[^>]*\\bandroid:name=\"([^\"]+)\"", info.packageName, false));
        info.activities.addAll(collectNameMatches(manifestText, "<activity\\b[^>]*\\bandroid:name=\"([^\"]+)\"", info.packageName, true));
        info.activityAliases.addAll(collectNameMatches(manifestText, "<activity-alias\\b[^>]*\\bandroid:name=\"([^\"]+)\"", info.packageName, true));
        info.services.addAll(collectNameMatches(manifestText, "<service\\b[^>]*\\bandroid:name=\"([^\"]+)\"", info.packageName, true));
        info.receivers.addAll(collectNameMatches(manifestText, "<receiver\\b[^>]*\\bandroid:name=\"([^\"]+)\"", info.packageName, true));
        info.providers.addAll(collectNameMatches(manifestText, "<provider\\b[^>]*\\bandroid:name=\"([^\"]+)\"", info.packageName, true));
        return info;
    }

    private static ManifestInfo parseBinaryManifest(byte[] bytes) throws Exception {
        AxmlReader reader = new AxmlReader(bytes);
        Axml axml = new Axml();
        reader.accept(axml);

        ManifestInfo info = new ManifestInfo();
        for (Object root : asList(readField(axml, "firsts"))) {
            if ("manifest".equals(asString(readField(root, "name")))) {
                walkManifestNode(root, info);
                break;
            }
        }
        return info;
    }

    private static void walkManifestNode(Object node, ManifestInfo info) throws Exception {
        info.packageName = trim(asString(getAttrValue(node, "package")));
        info.versionName = trim(asString(getAttrValue(node, "versionName")));
        info.versionCode = trim(asString(getAttrValue(node, "versionCode")));

        for (Object child : asList(readField(node, "children"))) {
            String nodeName = asString(readField(child, "name"));
            if ("uses-sdk".equals(nodeName)) {
                info.minSdkVersion = trim(asString(getAttrValue(child, "minSdkVersion")));
                info.targetSdkVersion = trim(asString(getAttrValue(child, "targetSdkVersion")));
                info.maxSdkVersion = trim(asString(getAttrValue(child, "maxSdkVersion")));
                continue;
            }
            if ("uses-permission".equals(nodeName)) {
                appendIfPresent(info.permissions, asString(getAttrValue(child, "name")), false, info.packageName);
                continue;
            }
            if ("uses-feature".equals(nodeName)) {
                appendIfPresent(info.features, asString(getAttrValue(child, "name")), false, info.packageName);
                continue;
            }
            if ("application".equals(nodeName)) {
                info.applicationLabel = trim(asString(getAttrValue(child, "label")));
                for (Object component : asList(readField(child, "children"))) {
                    String componentName = asString(readField(component, "name"));
                    String rawName = asString(getAttrValue(component, "name"));
                    if ("activity".equals(componentName)) {
                        appendIfPresent(info.activities, rawName, true, info.packageName);
                    } else if ("activity-alias".equals(componentName)) {
                        appendIfPresent(info.activityAliases, rawName, true, info.packageName);
                    } else if ("service".equals(componentName)) {
                        appendIfPresent(info.services, rawName, true, info.packageName);
                    } else if ("receiver".equals(componentName)) {
                        appendIfPresent(info.receivers, rawName, true, info.packageName);
                    } else if ("provider".equals(componentName)) {
                        appendIfPresent(info.providers, rawName, true, info.packageName);
                    }
                }
            }
        }
    }

    private static Object getAttrValue(Object node, String name) throws Exception {
        for (Object attr : asList(readField(node, "attrs"))) {
            if (name.equals(asString(readField(attr, "name")))) {
                return readField(attr, "value");
            }
        }
        return null;
    }

    private static Object readField(Object target, String name) throws Exception {
        if (target == null) {
            return null;
        }
        java.lang.reflect.Field field = target.getClass().getField(name);
        return field.get(target);
    }

    private static List<Object> asList(Object value) {
        List<Object> output = new ArrayList<>();
        if (value == null) {
            return output;
        }
        if (value instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) value) {
                output.add(item);
            }
            return output;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index += 1) {
                output.add(java.lang.reflect.Array.get(value, index));
            }
        }
        return output;
    }

    private static void appendTagLines(StringBuilder builder, String tagName, List<String> values) {
        for (String value : values) {
            builder.append("  <").append(tagName).append(" android:name=\"").append(safe(value)).append("\" />\n");
        }
    }

    private static void appendComponentLines(StringBuilder builder, String tagName, List<String> values) {
        for (String value : values) {
            builder.append("    <").append(tagName).append(" android:name=\"").append(safe(value)).append("\" />\n");
        }
    }

    private static void appendIfPresent(List<String> values, String rawValue, boolean normalizeComponent, String packageName) {
        String normalized = trim(rawValue);
        if (normalized.isEmpty()) {
            return;
        }
        if (normalizeComponent) {
            normalized = normalizeComponentName(normalized, packageName);
        }
        values.add(normalized);
    }

    private static List<String> collectNameMatches(String manifestText, String regex, String packageName, boolean normalizeComponent) {
        List<String> matches = new ArrayList<>();
        Matcher matcher = Pattern.compile(regex).matcher(manifestText);
        while (matcher.find()) {
            appendIfPresent(matches, matcher.group(1), normalizeComponent, packageName);
        }
        return matches;
    }

    private static String normalizeComponentName(String rawName, String packageName) {
        String normalized = trim(rawName);
        if (normalized.isEmpty()) {
            return normalized;
        }
        if (normalized.startsWith(".")) {
            return packageName == null || packageName.isBlank() ? normalized : packageName + normalized;
        }
        if (!normalized.contains(".") && packageName != null && !packageName.isBlank()) {
            return packageName + "." + normalized;
        }
        return normalized;
    }

    private static String firstGroup(String input, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(input);
        return matcher.find() ? trim(matcher.group(1)) : "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    static final class ManifestInfo {
        String packageName = "";
        String versionName = "";
        String versionCode = "";
        String minSdkVersion = "";
        String targetSdkVersion = "";
        String maxSdkVersion = "";
        String applicationLabel = "";
        final List<String> permissions = new ArrayList<>();
        final List<String> features = new ArrayList<>();
        final List<String> activities = new ArrayList<>();
        final List<String> activityAliases = new ArrayList<>();
        final List<String> services = new ArrayList<>();
        final List<String> receivers = new ArrayList<>();
        final List<String> providers = new ArrayList<>();
    }
}
