# APK Reverse Toolkit Runtime Resources

This directory keeps the generated runtime layout and docs, but the `.jar` artifacts are local build outputs and are no longer tracked by Git.

- Runtime jars:
  - `apktool-runtime-android.jar`
  - `android-framework.jar`
  - `jadx-runtime-android.jar`
  - `apk-reverse-helper-runtime-android.jar`
- Load mode: `ToolPkg.readResource(...)` + `Java.loadJar(..., { childFirstPrefixes: [...] })`
- Runtime policy: no CLI entrypoints, no terminal subprocesses, no `runJar`; JS only coordinates parameters, resource extraction, and result shaping
- Packaging rule: every runtime jar must be an Android-loadable dex-jar containing `classes.dex`
- Expected layers:
  - `apktool-runtime-android.jar` carries `brut.androlib.*` plus the Android-only `prebuilt/android/aapt2`
  - `jadx-runtime-android.jar` carries `jadx.api.*`, strips desktop GUI payload, and removes `jadx-script`, `java-convert`, `aab-input`, plus their bundled compiler/build baggage during packaging
  - `apk-reverse-helper-runtime-android.jar` carries the stable helper facade and native-analysis helpers
- Resource generation is handled by `examples/apktool/build_runtime_android_resources.ps1`
- `android-framework.jar` remains a standalone resource and is no longer duplicated inside `apktool-runtime-android.jar`
- Packaging trims the old desktop runtime payload instead of keeping Windows / Linux / macOS `aapt2` binaries around
- If `jadx-runtime-android.jar` or `apk-reverse-helper-runtime-android.jar` are missing, related tools should fail clearly instead of falling back to terminal execution
