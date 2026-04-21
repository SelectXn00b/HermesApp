package com.operit.apkreverse.runtime;

import org.json.JSONObject;

import java.io.File;
import java.util.Set;

final class JadxBridgeSupport {
    private JadxBridgeSupport() {
    }

    static String decompileApk(
            String inputApkPath,
            String outputDir,
            Integer jobs,
            boolean deobf,
            boolean showInconsistentCode
    ) throws Exception {
        File inputApk = SearchSupport.requireExistingFile(inputApkPath, "input_apk_path");
        File outputDirectory = new File(outputDir);
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new IllegalStateException("Failed to create output directory: " + outputDirectory.getAbsolutePath());
        }

        Object args = ReflectionSupport.newInstance("jadx.api.JadxArgs");
        try {
            ReflectionSupport.invoke(args, "setInputFile", inputApk);
            ReflectionSupport.invoke(args, "setOutDir", outputDirectory);
            ReflectionSupport.invoke(args, "setThreadsCount", jobs != null ? jobs : 1);
            if (deobf) {
                ReflectionSupport.invoke(args, "setDeobfuscationOn", true);
            }
            if (showInconsistentCode) {
                ReflectionSupport.invoke(args, "setShowInconsistentCode", true);
            }
            configureAndroidCompatibleSecurity(args);

            Object decompiler = ReflectionSupport.newInstance("jadx.api.JadxDecompiler", args);
            try {
                ReflectionSupport.invoke(decompiler, "load");
                ReflectionSupport.invoke(decompiler, "save");

                JSONObject payload = new JSONObject();
                payload.put("inputApkPath", inputApk.getAbsolutePath());
                payload.put("outputDir", outputDirectory.getAbsolutePath());
                payload.put("classCount", sizeOf(ReflectionSupport.invoke(decompiler, "getClasses")));
                payload.put("resourceCount", sizeOf(ReflectionSupport.invoke(decompiler, "getResources")));
                payload.put("errorsCount", numberOf(ReflectionSupport.invoke(decompiler, "getErrorsCount")));
                payload.put("warnsCount", numberOf(ReflectionSupport.invoke(decompiler, "getWarnsCount")));
                return payload.toString();
            } finally {
                ReflectionSupport.invoke(decompiler, "close");
            }
        } finally {
            ReflectionSupport.invoke(args, "close");
        }
    }

    @SuppressWarnings("unchecked")
    private static void configureAndroidCompatibleSecurity(Object args) throws Exception {
        Object rawFlags = ReflectionSupport.invokeStatic("jadx.api.security.JadxSecurityFlag", "all");
        if (!(rawFlags instanceof Set<?>)) {
            throw new IllegalStateException("jadx.api.security.JadxSecurityFlag.all() did not return a Set");
        }
        Set<Object> flags = (Set<Object>) rawFlags;
        Object secureXmlParserFlag =
                ReflectionSupport.getStaticField("jadx.api.security.JadxSecurityFlag", "SECURE_XML_PARSER");
        flags.remove(secureXmlParserFlag);
        Object security = ReflectionSupport.newInstance("jadx.api.security.impl.JadxSecurity", flags);
        ReflectionSupport.invoke(args, "setSecurity", security);
    }

    private static int sizeOf(Object value) throws Exception {
        if (value instanceof java.util.Collection<?>) {
            return ((java.util.Collection<?>) value).size();
        }
        Object result = ReflectionSupport.invoke(value, "size");
        return numberOf(result);
    }

    private static int numberOf(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
    }
}
