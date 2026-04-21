package com.operit.apkreverse.runtime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class ApkArchiveSupport {
    private ApkArchiveSupport() {
    }

    static String inspectApk(String inputApkPath) throws Exception {
        File apkFile = SearchSupport.requireExistingFile(inputApkPath, "input_apk_path");
        try (ZipFile zipFile = new ZipFile(apkFile)) {
            ManifestSupport.ManifestInfo manifestInfo = readManifestInfo(zipFile);
            JSONArray dexEntries = new JSONArray();
            JSONArray soEntries = new JSONArray();
            JSONArray signatureEntries = new JSONArray();
            JSONArray abis = new JSONArray();
            java.util.LinkedHashSet<String> abiSet = new java.util.LinkedHashSet<>();
            int dexCount = 0;
            int soCount = 0;
            int resEntryCount = 0;
            int assetEntryCount = 0;
            int metaInfEntryCount = 0;

            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name == null || entry.isDirectory()) {
                    continue;
                }
                if (name.startsWith("res/")) {
                    resEntryCount += 1;
                } else if (name.startsWith("assets/")) {
                    assetEntryCount += 1;
                } else if (name.startsWith("META-INF/")) {
                    metaInfEntryCount += 1;
                }
                if (name.endsWith(".dex")) {
                    dexCount += 1;
                    dexEntries.put(new JSONObject()
                            .put("name", name)
                            .put("size", Math.max(0L, entry.getSize())));
                }
                if (name.matches("(?i)^lib/[^/]+/.+\\.so$")) {
                    soCount += 1;
                    String abi = name.split("/")[1];
                    abiSet.add(abi);
                    soEntries.put(new JSONObject()
                            .put("name", name)
                            .put("abi", abi)
                            .put("size", Math.max(0L, entry.getSize())));
                }
                if (name.matches("(?i)^META-INF/.+\\.(RSA|DSA|EC|SF)$")) {
                    byte[] bytes = readAllBytes(zipFile, entry);
                    signatureEntries.put(new JSONObject()
                            .put("name", name)
                            .put("size", Math.max(0L, entry.getSize()))
                            .put("sha256", digestBytes(bytes, "SHA-256")));
                }
            }

            for (String abi : abiSet) {
                abis.put(abi);
            }

            JSONObject manifestJson = ManifestSupport.toJson(manifestInfo);
            JSONObject payload = new JSONObject();
            payload.put("inputApkPath", apkFile.getAbsolutePath());
            payload.put("packageName", manifestJson.getString("packageName"));
            payload.put("applicationLabel", manifestJson.getString("applicationLabel"));
            payload.put("versionName", manifestJson.getString("versionName"));
            payload.put("versionCode", manifestJson.getString("versionCode"));
            payload.put("minSdkVersion", manifestJson.getString("minSdkVersion"));
            payload.put("targetSdkVersion", manifestJson.getString("targetSdkVersion"));
            payload.put("maxSdkVersion", manifestJson.getString("maxSdkVersion"));
            payload.put("permissions", manifestJson.getJSONArray("permissions"));
            payload.put("features", manifestJson.getJSONArray("features"));
            payload.put("components", new JSONObject()
                    .put("activities", manifestJson.getJSONArray("activities"))
                    .put("activityAliases", manifestJson.getJSONArray("activityAliases"))
                    .put("services", manifestJson.getJSONArray("services"))
                    .put("receivers", manifestJson.getJSONArray("receivers"))
                    .put("providers", manifestJson.getJSONArray("providers")));
            payload.put("dexCount", dexCount);
            payload.put("dexEntries", dexEntries);
            payload.put("abis", abis);
            payload.put("soCount", soCount);
            payload.put("soEntries", soEntries);
            payload.put("signatureEntries", signatureEntries);
            payload.put("resourceStats", new JSONObject()
                    .put("resEntryCount", resEntryCount)
                    .put("assetEntryCount", assetEntryCount)
                    .put("metaInfEntryCount", metaInfEntryCount));
            payload.put("manifestPreview", manifestJson.getString("manifestPreview"));
            return payload.toString();
        }
    }

    static int resolveMinSdkVersion(File apkFile, int fallbackValue) {
        try (ZipFile zipFile = new ZipFile(apkFile)) {
            ZipEntry entry = zipFile.getEntry("AndroidManifest.xml");
            if (entry == null) {
                return fallbackValue;
            }
            return ManifestSupport.resolveMinSdkVersion(readAllBytes(zipFile, entry), fallbackValue);
        } catch (Exception ignored) {
            return fallbackValue;
        }
    }

    static String readManifestPreview(ZipFile zipFile) throws Exception {
        return ManifestSupport.renderManifestText(readManifestInfo(zipFile));
    }

    static ManifestSupport.ManifestInfo readManifestInfo(ZipFile zipFile) throws Exception {
        ZipEntry manifestEntry = zipFile.getEntry("AndroidManifest.xml");
        if (manifestEntry == null) {
            return new ManifestSupport.ManifestInfo();
        }
        return ManifestSupport.parseManifest(readAllBytes(zipFile, manifestEntry));
    }

    static byte[] readAllBytes(ZipFile zipFile, ZipEntry entry) throws Exception {
        try (InputStream input = zipFile.getInputStream(entry);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    static String digestBytes(byte[] bytes, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] result = digest.digest(bytes);
        StringBuilder builder = new StringBuilder();
        for (byte value : result) {
            builder.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return builder.toString();
    }
}
