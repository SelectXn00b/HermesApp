/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/agents/(all)
 *
 * AndroidForClaw adaptation: data models.
 */
package com.xiaomo.androidforclaw.data.model;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import com.xiaomo.androidforclaw.core.MyApplication;

import java.io.File;

/**
 * Created by zhangjiahao on 17-11-22.
 */

public class ApkInfo {

    private Uri mApkUri;
    private String mApkPackageName;
    private File mApk;
    private static final String TAG = "Installsupport";

    public ApkInfo(Uri apkUri) {
        mApkUri = apkUri;
    }

    public boolean prepare() {
        Log.d(TAG, "开始准备APK信息，URI: " + mApkUri);
        
        mApk = getApkFile(mApkUri);
        Log.d(TAG, "APK文件路径: " + mApk.getAbsolutePath());
        Log.d(TAG, "APK文件存在: " + mApk.exists());
        Log.d(TAG, "APK文件可读: " + mApk.canRead());
        Log.d(TAG, "APK文件大小: " + mApk.length());
        
        // 即使文件不可读，也尝试解析APK信息
        if (mApk.exists() && mApk.length() > 0) {
            Log.d(TAG, "开始解析APK包信息");
            try {
                PackageInfo packageInfo = MyApplication.application
                        .getApplicationContext()
                        .getPackageManager()
                        .getPackageArchiveInfo(
                                mApk.getAbsolutePath(),
                                PackageManager.GET_SIGNATURES);
 
                Log.d(TAG, "PackageInfo解析结果: " + (packageInfo != null));
 
                if (packageInfo != null) {
                    mApkPackageName = packageInfo.packageName;
                    Log.d(TAG, "APK包名: " + mApkPackageName);
                    Log.d(TAG, "APK版本: " + packageInfo.versionName);
                    Log.d(TAG, "APK版本码: " + packageInfo.versionCode);
                    return true;
                } else {
                    Log.e(TAG, "PackageInfo为null，APK文件可能损坏或不是有效的APK");
                }
            } catch (Exception e) {
                Log.e(TAG, "解析APK时发生异常: " + e.getMessage(), e);
            }
        } else {
            Log.e(TAG, "APK文件不存在或大小为0");
        }
        return false;
    }

    private File getApkFile(Uri uri) {
        return new File(uri.getPath());
    }

    public Uri getApkUri() {
        return Uri.fromFile(mApk);
    }

    public String getApkPackageName() {
        return mApkPackageName;
    }

    @Override
    public String toString() {
        return "ApkInfo{" +
                "mApkUri=" + getApkUri() +
                ", mApkPackageName='" + getApkPackageName() + '\'' +
                '}';
    }

}
