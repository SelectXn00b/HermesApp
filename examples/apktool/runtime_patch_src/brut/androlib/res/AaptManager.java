package brut.androlib.res;

import brut.androlib.exceptions.AndrolibException;
import brut.common.BrutException;
import brut.util.Jar;
import brut.util.OS;
import java.io.File;

public final class AaptManager {
    private static final String BINARY_NAME = "aapt2";
    private static final String ANDROID_BINARY_RESOURCE = "/prebuilt/android/aapt2";

    private AaptManager() {
    }

    public static String getBinaryName() {
        return BINARY_NAME;
    }

    public static File getBinaryFile() throws AndrolibException {
        File binaryFile;
        try {
            binaryFile = Jar.getResourceAsFile(AaptManager.class, ANDROID_BINARY_RESOURCE, BINARY_NAME + "_");
        } catch (BrutException e) {
            throw new AndrolibException(e);
        }
        setBinaryExecutable(binaryFile);
        return binaryFile;
    }

    private static void setBinaryExecutable(File binaryFile) throws AndrolibException {
        if (!binaryFile.isFile() || !binaryFile.canRead()) {
            throw new AndrolibException("Could not read aapt binary: " + binaryFile.getPath());
        }
        if (!binaryFile.setExecutable(true)) {
            throw new AndrolibException("Could not set aapt binary as executable: " + binaryFile.getPath());
        }
    }

    public static int getBinaryVersion(File binaryFile) throws AndrolibException {
        setBinaryExecutable(binaryFile);
        String versionOutput = OS.execAndReturn(new String[]{binaryFile.getPath(), "version"});
        if (versionOutput == null) {
            throw new AndrolibException("Could not execute aapt binary at location: " + binaryFile.getPath());
        }
        return getVersionFromString(versionOutput);
    }

    public static int getVersionFromString(String versionOutput) throws AndrolibException {
        if (versionOutput.startsWith("Android Asset Packaging Tool (aapt) 2:")
                || versionOutput.startsWith("Android Asset Packaging Tool (aapt) 2.")) {
            return 2;
        }
        if (versionOutput.startsWith("Android Asset Packaging Tool, v0.")) {
            return 1;
        }
        throw new AndrolibException("Could not identify aapt binary version: " + versionOutput);
    }
}
