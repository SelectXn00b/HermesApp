package com.operit.apkreverse.runtime;

import org.json.JSONObject;

import java.io.File;
import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ApktoolBridgeSupport {
    private ApktoolBridgeSupport() {
    }

    static String decodeApk(
            String inputApkPath,
            String outputDir,
            String frameworkJarPath,
            String apktoolVersion,
            Integer jobs,
            String framePath,
            String frameTag,
            boolean force,
            boolean noSrc,
            boolean noRes,
            boolean onlyManifest,
            boolean noAssets,
            boolean verbose,
            boolean quiet
    ) throws Exception {
        File inputApk = SearchSupport.requireExistingFile(inputApkPath, "input_apk_path");
        File outputDirectory = new File(outputDir);
        Object context = createExecutionContext(apktoolVersion, jobs, framePath, frameTag, force, verbose, quiet, true, noSrc, noRes, onlyManifest, noAssets);
        Object config = ReflectionSupport.invoke(context, "get", "config");
        JSONObject appliedConfig = (JSONObject) ReflectionSupport.invoke(context, "get", "appliedConfig");
        JSONObject frameworkInfo = ensureFrameworkInstalled(config, frameworkJarPath);

        Object decoder = ReflectionSupport.newInstance("brut.androlib.ApkDecoder", inputApk, config);
        ReflectionSupport.invoke(decoder, "decode", outputDirectory);

        JSONObject payload = new JSONObject();
        payload.put("inputApkPath", inputApk.getAbsolutePath());
        payload.put("outputDir", outputDirectory.getAbsolutePath());
        payload.put("frameworkInfo", frameworkInfo);
        payload.put("appliedConfig", appliedConfig);
        return payload.toString();
    }

    static String buildApk(
            String decodedDir,
            String outputApkPath,
            String frameworkJarPath,
            String apktoolVersion,
            Integer jobs,
            String framePath,
            String frameTag,
            boolean force,
            boolean verbose,
            boolean quiet
    ) throws Exception {
        File decodedDirectory = requireExistingDirectory(decodedDir, "decoded_dir");
        File outputApk = new File(outputApkPath);
        File outputParent = outputApk.getParentFile();
        if (outputParent != null && !outputParent.exists() && !outputParent.mkdirs()) {
            throw new IllegalStateException("Failed to create output directory: " + outputParent.getAbsolutePath());
        }

        Object context = createExecutionContext(apktoolVersion, jobs, framePath, frameTag, force, verbose, quiet, false, false, false, false, false);
        Object config = ReflectionSupport.invoke(context, "get", "config");
        JSONObject appliedConfig = (JSONObject) ReflectionSupport.invoke(context, "get", "appliedConfig");
        JSONObject frameworkInfo = ensureFrameworkInstalled(config, frameworkJarPath);

        Object builder = ReflectionSupport.newInstance("brut.androlib.ApkBuilder", decodedDirectory, config);
        ReflectionSupport.invoke(builder, "build", outputApk);

        JSONObject payload = new JSONObject();
        payload.put("decodedDir", decodedDirectory.getAbsolutePath());
        payload.put("outputApkPath", outputApk.getAbsolutePath());
        payload.put("frameworkInfo", frameworkInfo);
        payload.put("appliedConfig", appliedConfig);
        return payload.toString();
    }

    private static JSONObject ensureFrameworkInstalled(Object config, String frameworkJarPath) throws Exception {
        if (frameworkJarPath == null || frameworkJarPath.isBlank()) {
            throw new IllegalArgumentException("framework_jar_path must not be blank");
        }
        File frameworkJar = SearchSupport.requireExistingFile(frameworkJarPath, "framework_jar_path");
        Object framework = ReflectionSupport.newInstance("brut.androlib.res.Framework", config);
        File frameworkDirectory = (File) ReflectionSupport.invoke(framework, "getDirectory");
        File frameworkApk = new File(frameworkDirectory, "1.apk");
        boolean frameworkExists = frameworkApk.exists();
        long frameworkSize = frameworkExists ? frameworkApk.length() : 0L;

        JSONObject info = new JSONObject();
        info.put("frameworkDirectory", frameworkDirectory.getAbsolutePath());
        info.put("frameworkApkPath", frameworkApk.getAbsolutePath());
        info.put("frameworkSize", frameworkSize);

        if (frameworkExists && frameworkSize > 0L) {
            info.put("installed", false);
            return info;
        }
        if (frameworkExists && !frameworkApk.delete()) {
            throw new IllegalStateException("Failed to delete invalid framework apk: " + frameworkApk.getAbsolutePath());
        }
        ReflectionSupport.invoke(framework, "install", frameworkJar);
        info.put("installed", true);
        info.put("sourceJarPath", frameworkJar.getAbsolutePath());
        info.put("frameworkSize", frameworkApk.length());
        return info;
    }

    private static Object createExecutionContext(
            String apktoolVersion,
            Integer jobs,
            String framePath,
            String frameTag,
            boolean force,
            boolean verbose,
            boolean quiet,
            boolean decodeMode,
            boolean noSrc,
            boolean noRes,
            boolean onlyManifest,
            boolean noAssets
    ) throws Exception {
        if (verbose && quiet) {
            throw new IllegalArgumentException("verbose cannot be used together with quiet");
        }
        configureJavaLogging(quiet ? "quiet" : verbose ? "verbose" : "normal");

        Object config = ReflectionSupport.newInstance("brut.androlib.Config", apktoolVersion);
        JSONObject appliedConfig = new JSONObject();
        appliedConfig.put("version", apktoolVersion);

        if (jobs != null) {
            ReflectionSupport.invoke(config, "setJobs", jobs);
            appliedConfig.put("jobs", jobs);
        }
        if (framePath != null && !framePath.isBlank()) {
            ReflectionSupport.invoke(config, "setFrameworkDirectory", framePath);
            appliedConfig.put("frame_path", framePath);
        }
        if (frameTag != null && !frameTag.isBlank()) {
            ReflectionSupport.invoke(config, "setFrameworkTag", frameTag);
            appliedConfig.put("frame_tag", frameTag);
        }
        if (force) {
            ReflectionSupport.invoke(config, "setForced", true);
            appliedConfig.put("force", true);
        }
        if (verbose) {
            ReflectionSupport.invoke(config, "setVerbose", true);
            appliedConfig.put("verbose", true);
        }
        if (quiet) {
            appliedConfig.put("quiet", true);
        }

        if (decodeMode) {
            if (noSrc) {
                ReflectionSupport.invoke(config, "setDecodeSources", enumConstant("brut.androlib.Config$DecodeSources", "NONE"));
                appliedConfig.put("decode_sources", "none");
            }
            if (onlyManifest) {
                ReflectionSupport.invoke(config, "setDecodeResources", enumConstant("brut.androlib.Config$DecodeResources", "ONLY_MANIFEST"));
                appliedConfig.put("decode_resources", "only_manifest");
            } else if (noRes) {
                ReflectionSupport.invoke(config, "setDecodeResources", enumConstant("brut.androlib.Config$DecodeResources", "NONE"));
                appliedConfig.put("decode_resources", "none");
            }
            if (noAssets) {
                ReflectionSupport.invoke(config, "setDecodeAssets", enumConstant("brut.androlib.Config$DecodeAssets", "NONE"));
                appliedConfig.put("decode_assets", "none");
            }
        }

        return new ExecutionContext(config, appliedConfig);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumConstant(String className, String name) throws Exception {
        Class enumClass = Class.forName(className);
        return Enum.valueOf(enumClass, name);
    }

    private static File requireExistingDirectory(String path, String parameterName) {
        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalArgumentException(parameterName + " does not exist: " + file.getAbsolutePath());
        }
        if (!file.isDirectory()) {
            throw new IllegalArgumentException(parameterName + " is not a directory: " + file.getAbsolutePath());
        }
        return file;
    }

    private static void configureJavaLogging(String mode) {
        Logger root = Logger.getLogger("");
        Level level = "quiet".equals(mode) ? Level.OFF : "verbose".equals(mode) ? Level.ALL : Level.INFO;
        root.setLevel(level);
        for (Handler handler : root.getHandlers()) {
            handler.setLevel(level);
        }
    }

    private static final class ExecutionContext {
        private final Object config;
        private final JSONObject appliedConfig;

        private ExecutionContext(Object config, JSONObject appliedConfig) {
            this.config = config;
            this.appliedConfig = appliedConfig;
        }

        public Object get(String key) {
            String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
            if ("config".equals(normalized)) {
                return config;
            }
            if ("appliedconfig".equals(normalized)) {
                return appliedConfig;
            }
            return null;
        }
    }
}
