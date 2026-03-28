/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.androidforclaw.util;

import static android.content.pm.PackageInstaller.EXTRA_PACKAGE_NAME;
import static android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.xiaomo.androidforclaw.core.MyApplication;
import com.xiaomo.androidforclaw.data.model.ApkInfo;
import com.xiaomo.androidforclaw.util.WakeLockManager;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class InstallManager {

    private static final String TAG = "InstallManager";
    public static final String PACKAGE_NAME = "packageName";
    public static final Map<String, IPackageInstallObserver> sObservers = Collections.synchronizedMap(new HashMap<>());
    private static final InstallManager instance = new InstallManager();

    private InstallManager() {
    }

    public static InstallManager getInstance() {
        return instance;
    }

    public static void apkInstall(String apkAbsolutePath, PackageInstallObserver observer) {
        Log.d(TAG, "开始APK安装流程，路径: " + apkAbsolutePath);

        if (TextUtils.isEmpty(apkAbsolutePath)) {
            Log.e(TAG, "APK路径为空！");
            observer.onInstallFailure("", "APKABSOLUTEPATH_IS_NULL");
            return;
        }

        File apkFile = new File(apkAbsolutePath);
        if (!apkFile.exists()) {
            Log.e(TAG, "APK文件不存在: " + apkAbsolutePath);
            observer.onInstallFailure(apkAbsolutePath, "APK_FILE_NOT_EXISTS");
            return;
        }

        Log.d(TAG, "APK文件存在，大小: " + apkFile.length() + " bytes");
        observer.onInstallStart(apkAbsolutePath);

        // 检查安装权限
        Context context = MyApplication.application.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean hasInstallPermission = context.getPackageManager().canRequestPackageInstalls();
            Log.d(TAG, "安装权限检查: " + hasInstallPermission);
            if (!hasInstallPermission) {
                Log.w(TAG, "没有安装权限，静默安装可能失败");
                observer.onInstallFailure(apkAbsolutePath, "NO_INSTALL_PERMISSION");
                return;
            }
        }

        // 检查存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean hasStoragePermission = android.os.Environment.isExternalStorageManager();
            Log.d(TAG, "存储管理权限检查: " + hasStoragePermission);
            if (!hasStoragePermission) {
                Log.w(TAG, "没有存储管理权限，可能无法访问外部存储");
            }
        }

        Log.d(TAG, "开始解析APK文件: " + apkAbsolutePath);
        Log.d(TAG, "APK文件可读性: " + apkFile.canRead());
        Log.d(TAG, "APK文件权限: " + apkFile.getAbsolutePath());

        ApkInfo apkInfo = new ApkInfo(Uri.fromFile(apkFile));
        Log.d(TAG, "创建ApkInfo对象成功");

        boolean prepareResult = apkInfo.prepare();
        Log.d(TAG, "APK解析结果: " + prepareResult);

        if (!prepareResult) {
            Log.e(TAG, "APK文件解析失败，可能不是有效的APK文件");
            Log.e(TAG, "APK文件路径: " + apkAbsolutePath);
            Log.e(TAG, "APK文件大小: " + apkFile.length());
            Log.e(TAG, "APK文件存在: " + apkFile.exists());
            Log.e(TAG, "APK文件可读: " + apkFile.canRead());
            observer.onInstallFailure(apkAbsolutePath, "APK_UNAVAILABLE");
            return;
        }

        Log.d(TAG, "APK信息解析成功，包名: " + apkInfo.getApkPackageName());

        // 直接调用安装方法
        boolean installResult = installApkDirectly(apkInfo, observer);
        if (!installResult) {
            Log.e(TAG, "APK安装失败");
            observer.onInstallFailure(apkAbsolutePath, "INSTALL_FAILED");
        }
    }

    private static boolean installApkDirectly(ApkInfo apkInfo, PackageInstallObserver observer) {
        Log.d(TAG, "开始直接安装APK: " + apkInfo.getApkPackageName());

        InputStream in = null;
        OutputStream out = null;
        PackageInstaller.Session session = null;
        String packageName = apkInfo.getApkPackageName();

        // 设置超时处理
        Handler handler = new Handler(android.os.Looper.getMainLooper());
        Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (sObservers.containsKey(packageName)) {
                    Log.e(TAG, "APK安装超时: " + packageName);
                    observer.onInstallFailure(apkInfo.getApkUri().getPath(), "INSTALL_TIMEOUT");
                    sObservers.remove(packageName);
                }
            }
        };

        // 创建安装结果监听器
        InstallResultListener resultListener = new InstallResultListener(packageName, observer);
        resultListener.setHandler(handler, timeoutRunnable);
        sObservers.put(packageName, resultListener);

        handler.postDelayed(timeoutRunnable, 60000); // 60秒超时

        try {
            Context context = MyApplication.application.getApplicationContext();
            PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();

            Log.d(TAG, "创建安装会话...");
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(packageName);

            int sessionId = packageInstaller.createSession(params);
            Log.d(TAG, "安装会话创建成功，ID: " + sessionId);

            session = packageInstaller.openSession(sessionId);
            Uri uri = apkInfo.getApkUri();
            File apkFile = new File(uri.getPath());

            Log.d(TAG, "开始写入APK数据...");
            in = new FileInputStream(apkFile);
            String sessionName = String.valueOf(Math.abs(uri.getPath().hashCode()));
            out = session.openWrite(sessionName, 0, apkFile.length());

            int read;
            byte[] buffer = new byte[65536];
            long totalBytes = 0;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                totalBytes += read;
                if (totalBytes % (1024 * 1024) == 0) { // 每1MB打印一次进度
                    Log.d(TAG, "已写入: " + (totalBytes / 1024 / 1024) + "MB");
                }
            }

            Log.d(TAG, "APK数据写入完成，总计: " + totalBytes + " bytes");
            session.fsync(out);
            in.close();
            out.close();

            Log.d(TAG, "提交安装会话...");
            IntentSender intentSender = getDefaultIntentSender(context, packageName);
            Log.d(TAG, "IntentSender创建成功: " + intentSender);
            session.commit(intentSender);
            Log.d(TAG, "APK安装提交成功，开始检查安装状态...");

            // 使用延迟检查的方式，不依赖广播接收器
            checkInstallStatusDelayed(packageName, observer, handler, timeoutRunnable);

            // 同时启动系统安装Intent作为备选方案
//            startSystemInstallIntent(apkInfo, observer);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "APK安装过程中发生异常: " + e.getMessage(), e);
            observer.onInstallFailure(apkInfo.getApkUri().getPath(), "INSTALL_EXCEPTION: " + e.getMessage());
            sObservers.remove(packageName);
            handler.removeCallbacks(timeoutRunnable);
            return false;
        } finally {
            closeQuietly(session);
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    private static void checkInstallStatusDelayed(String packageName, PackageInstallObserver observer,
                                                  Handler handler, Runnable timeoutRunnable) {
        Log.d(TAG, "开始延迟检查安装状态: " + packageName);

        // 每2秒检查一次安装状态，最多检查15次（30秒）
        final int[] checkCount = {0};
        final int maxChecks = 15;

        Runnable checkRunnable = new Runnable() {
            @Override
            public void run() {
                checkCount[0]++;
                Log.d(TAG, "第" + checkCount[0] + "次检查安装状态: " + packageName);

                try {
                    Context context = MyApplication.application.getApplicationContext();
                    PackageManager pm = context.getPackageManager();

                    try {
                        PackageInfo packageInfo = pm.getPackageInfo(packageName, 0);
                        if (packageInfo.applicationInfo.enabled) {
                            long installTime = packageInfo.firstInstallTime;
                            long lastUpdateTime = packageInfo.lastUpdateTime;
                            long currentTime = System.currentTimeMillis();
                            // 如果最近1分钟内有安装或更新，认为安装成功
                            if (currentTime - installTime < 60000 || currentTime - lastUpdateTime < 60000) {
                                Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
                                if (launchIntent != null) {
                                    Log.d(TAG, "APK安装成功: " + packageName);
                                    handler.removeCallbacks(timeoutRunnable);
                                    observer.onInstallSuccess("安装成功");
                                    sObservers.remove(packageName);
                                } else {
                                    Log.d(TAG, "应用已安装但无启动Activity，继续等待...");
                                }
                            } else {
                                Log.d(TAG, "应用已存在但不是新安装的，继续等待...");
                            }
                        } else {
                            Log.d(TAG, "应用已安装但被禁用，继续等待...");
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        Log.d(TAG, "应用尚未安装，继续等待...");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "检查安装状态时发生异常: " + e.getMessage(), e);
                    handler.removeCallbacks(timeoutRunnable);
                    observer.onInstallFailure("", "INSTALL_CHECK_EXCEPTION: " + e.getMessage());
                    sObservers.remove(packageName);
                }
            }
        };

        // 延迟2秒开始第一次检查
        handler.postDelayed(checkRunnable, 2000);
    }

    private static void startSystemInstallIntent(ApkInfo apkInfo, PackageInstallObserver observer) {
        try {
            Log.d(TAG, "启动系统安装Intent作为备选方案");
            Context context = MyApplication.application.getApplicationContext();

            // 检查安装权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                boolean hasInstallPermission = context.getPackageManager().canRequestPackageInstalls();
                if (!hasInstallPermission) {
                    Log.w(TAG, "没有安装权限，无法使用系统安装Intent");
                    return;
                }
            }

            // 使用FileProvider创建URI
            Uri apkUri = apkInfo.getApkUri();
            Log.d(TAG, "原始APK URI: " + apkUri);

            // 如果是file:// URI，需要转换为FileProvider URI
            if (apkUri.getScheme().equals("file")) {
                String filePath = apkUri.getPath();
                Log.d(TAG, "APK文件路径: " + filePath);

                // 使用FileProvider创建URI
                String authority = context.getPackageName() + ".provider";
                apkUri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        authority,
                        new java.io.File(filePath)
                );
                Log.d(TAG, "FileProvider URI: " + apkUri);
            }

            // 创建系统安装Intent
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Log.d(TAG, "启动系统安装Intent: " + installIntent);
            context.startActivity(installIntent);
            Log.d(TAG, "系统安装Intent已启动，请用户手动确认安装");

        } catch (Exception e) {
            Log.e(TAG, "启动系统安装Intent失败: " + e.getMessage(), e);
        }
    }

    private static IntentSender getDefaultIntentSender(Context context, String pkgName) {
        Intent intent = new Intent(InstallResultBroadcastReceiver.ACTION_INSTALL_RESULT);
        intent.setPackage(MyApplication.application.getPackageName());
        int index = 0;
        if (!TextUtils.isEmpty(pkgName)) {
            intent.putExtra(PACKAGE_NAME, pkgName);
            try {
                index = pkgName.hashCode();
            } catch (NumberFormatException e) {
                // Ignore exception
            }
        }
        int flags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent
                .getBroadcast(context, index, intent, flags);
        return pendingIntent.getIntentSender();
    }

    public static void notifyResult(final String pkgName, final int returnCode, Bundle extrac) {
        Log.d(TAG, "notifyResult被调用，包名: " + pkgName + ", 返回码: " + returnCode);
        Log.d(TAG, "当前观察者数量: " + sObservers.size());
        Log.d(TAG, "观察者列表: " + sObservers.keySet());

        IPackageInstallObserver observer = sObservers.remove(pkgName);
        if (observer == null) {
            Log.w(TAG, "没有找到对应的观察者: " + pkgName);
            return;
        }

        Log.d(TAG, "找到观察者，开始通知结果");
        try {
            observer.packageInstalledResult(pkgName, returnCode, extrac);
            Log.d(TAG, "观察者通知成功");
        } catch (Throwable e) {
            Log.e(TAG, "观察者通知失败: " + e.getMessage(), e);
            try {
                observer.reNotifyResultOnError(pkgName, returnCode, extrac);
                Log.d(TAG, "重试通知成功");
            } catch (Throwable ex) {
                Log.e(TAG, "重试通知也失败: " + ex.getMessage(), ex);
            }
        }
    }

    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // Ignore exception
            }
        }
    }

    public interface IPackageInstallObserver {

        void packageInstalledResult(java.lang.String packageName, int returnCode, android.os.Bundle extras);

        void reNotifyResultOnError(String packageName, int returnCode, Bundle extras);

    }

    public static class MarketInstallObserverDelegate implements IPackageInstallObserver {

        private final ApkInfo mApkInfo;
        private final PackageInstallObserver mDelegate;

        MarketInstallObserverDelegate(ApkInfo apkInfo, PackageInstallObserver listener) {
            mApkInfo = apkInfo;
            mDelegate = listener;
        }

        public void packageInstalledResult(String packageName, int returnCode, Bundle extras) {
            Log.i(TAG, "Installed " + packageName + ":" + returnCode);
            String apkUrl = mApkInfo.getApkUri().getPath();
            if (!apkUrl.isEmpty()) {
                if (extras != null) {
                    int status = extras.getInt(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                    if (status != PackageInstaller.STATUS_SUCCESS) {
                        mDelegate.onInstallFailure(apkUrl, "onReceiveResult:" + status + "," + returnCode + ",android:" + Build.VERSION.SDK_INT);
                    } else {
                        mDelegate.onInstallSuccess(apkUrl);
                    }
                } else {
                    mDelegate.onInstallFailure(apkUrl, "onReceiveResultReturnCode" + returnCode + ",android:" + Build.VERSION.SDK_INT);
                }
            } else {
                mDelegate.onInstallFailure("", "onReceiveResult:APKABSOLUTEPATH_IS_NULL");
            }
        }

        /**
         * 某些手机上会出现java.lang.NoSuchMethodError,packageInstalledResult(Ljava/lang/String;ILandroid/os/Bundle;)调用失败,这时手动重新调用一下
         */
        public void reNotifyResultOnError(String packageName, int returnCode, Bundle extras) {
            packageInstalledResult(packageName, returnCode, extras);
        }

    }

    class InstallManagerInfo {
        public static final int INSTALL_SUCCEEDED = 1;
        public static final int ERROR_NO_ENOUGH_SPACE_AFTER_INSTALL = 11;  //安装过程中发生的空间不足
        public static final int ERROR_INSTALL_COMMIT_FAIL = 17;
        /**
         * Installation return code: this is passed in the {PackageInstaller#EXTRA_LEGACY_STATUS}
         * if the package manager service found that the device didn't have enough storage space to
         * install the app.
         *
         * @hide
         */
        public static final int INSTALL_FAILED_INSUFFICIENT_STORAGE = -4;
        /**
         * Installation return code: this is passed in the {PackageInstaller#EXTRA_LEGACY_STATUS}
         * if a previously installed package of the same name has a different signature than the new
         * package (and the old package's data was not removed).
         *
         * @hide
         */
        public static final int INSTALL_FAILED_UPDATE_INCOMPATIBLE = -7;
        /**
         * Installation return code: this is passed in the {PackageInstaller#EXTRA_LEGACY_STATUS}
         * if the new package couldn't be installed because the verification did not succeed.
         *
         * @hide
         */
        public static final int INSTALL_FAILED_VERIFICATION_FAILURE = -22;
        /**
         * Installation parse return code: this is passed in the
         * {PackageInstaller#EXTRA_LEGACY_STATUS} if the parser encountered some structural
         * problem in the manifest.
         *
         * @hide
         */
        public static final int INSTALL_PARSE_FAILED_MANIFEST_MALFORMED = -108;
    }


    class InstallResultBroadcastReceiver extends BroadcastReceiver {
        private static final String TAG = "SessionInstallReceiver";
        private static final String EXTRA_LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS";
        public static final String ACTION_INSTALL_RESULT = "com.xiaomo.androidforclaw.action.INSTALL_RESULT";
        //legacyStatus没有通用错误码可用，此处使用未定义的错误码，以便在接下来的PackageInstallObserver中被转换为商店的错误码ERROR_INSTALL_DEFAULT_FAIL
        private static final int STATUS_UNKNOWN = -10000;

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "广播接收器收到Intent: " + intent);
            Log.d(TAG, "Intent Action: " + intent.getAction());
            Log.d(TAG, "Intent Data: " + intent.getDataString());
            Log.d(TAG, "Intent Extras: " + intent.getExtras());

            String action = intent.getAction();
            Log.d(TAG, "检查Action是否匹配: " + action + " == " + ACTION_INSTALL_RESULT);

            if (ACTION_INSTALL_RESULT.equals(action)) {
                Log.d(TAG, "Action匹配成功，开始处理安装结果");

                String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
                Log.d(TAG, "从EXTRA_PACKAGE_NAME获取包名: " + packageName);

                if (TextUtils.isEmpty(packageName)) {
                    packageName = intent.getStringExtra("packageName"); //被CustomOS拦截时存在一种不标准的参数写法，做兼容
                    Log.d(TAG, "从packageName获取包名: " + packageName);
                }

                int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                String message = intent.getStringExtra(EXTRA_STATUS_MESSAGE);

                Log.d(TAG, "安装状态: " + status);
                Log.d(TAG, "状态消息: " + message);
                Log.d(TAG, "包名: " + packageName);

                if (status != PackageInstaller.STATUS_SUCCESS) {
                    Log.e(TAG, String.format(Locale.getDefault(), "install %s failed with [status=%d,message=%s]", packageName, status, message));
                } else {
                    Log.d(TAG, "安装成功: " + packageName);
                }

                handleSessionInstallByNormal(context, intent, packageName, status);
            } else {
                Log.w(TAG, "Action不匹配，忽略此广播: " + action);
            }
        }

        private void handleSessionInstallByNormal(Context context, final Intent intent, final String packageName, final int status) {
            Log.d(TAG, "开始处理安装结果，包名: " + packageName + ", 状态: " + status);

            int legacyStatus;
            if (intent.hasExtra(EXTRA_LEGACY_STATUS)) {
                legacyStatus = intent.getIntExtra(EXTRA_LEGACY_STATUS, STATUS_UNKNOWN);
                Log.d(TAG, "从EXTRA_LEGACY_STATUS获取legacy状态: " + legacyStatus);
            } else {
                Log.d(TAG, "没有EXTRA_LEGACY_STATUS，根据status转换legacy状态");
                switch (status) {
                    case PackageInstaller.STATUS_SUCCESS:
                        legacyStatus = InstallManagerInfo.INSTALL_SUCCEEDED;
                        Log.d(TAG, "STATUS_SUCCESS -> INSTALL_SUCCEEDED");
                        break;
                    case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                    case PackageInstaller.STATUS_FAILURE_CONFLICT:
                        legacyStatus = InstallManagerInfo.INSTALL_FAILED_UPDATE_INCOMPATIBLE;
                        Log.d(TAG, "STATUS_FAILURE_INCOMPATIBLE/CONFLICT -> INSTALL_FAILED_UPDATE_INCOMPATIBLE");
                        break;
                    case PackageInstaller.STATUS_FAILURE_BLOCKED:
                        legacyStatus = InstallManagerInfo.INSTALL_FAILED_VERIFICATION_FAILURE;
                        Log.d(TAG, "STATUS_FAILURE_BLOCKED -> INSTALL_FAILED_VERIFICATION_FAILURE");
                        break;
                    case PackageInstaller.STATUS_FAILURE_INVALID:
                        legacyStatus = InstallManagerInfo.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
                        Log.d(TAG, "STATUS_FAILURE_INVALID -> INSTALL_PARSE_FAILED_MANIFEST_MALFORMED");
                        break;
                    case PackageInstaller.STATUS_FAILURE_STORAGE:
                        legacyStatus = InstallManagerInfo.INSTALL_FAILED_INSUFFICIENT_STORAGE;
                        Log.d(TAG, "STATUS_FAILURE_STORAGE -> INSTALL_FAILED_INSUFFICIENT_STORAGE");
                        break;
                    case PackageInstaller.STATUS_FAILURE:
                    case PackageInstaller.STATUS_FAILURE_ABORTED:
                    case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    default:
                        legacyStatus = STATUS_UNKNOWN;
                        Log.d(TAG, "其他状态 -> STATUS_UNKNOWN");
                        break;
                }
            }

            Log.d(TAG, "最终legacy状态: " + legacyStatus);

            if (TextUtils.isEmpty(packageName)) {
                Log.e(TAG, "包名为空，无法处理安装结果");
                return;
            }

            final int resultCode = legacyStatus;
            Log.d(TAG, "通知安装结果，包名: " + packageName + ", 结果码: " + resultCode);
            Log.d(TAG, "当前观察者数量: " + sObservers.size());
            Log.d(TAG, "观察者列表: " + sObservers.keySet());

            InstallManager.notifyResult(packageName, resultCode, intent.getExtras());
            Log.d(TAG, "安装结果通知完成");
        }

    }

    public interface PackageInstallObserver {

        void onInstallStart(String fileUrl);

        void onInstallSuccess(String fileUrl);

        void onInstallFailure(String fileUrl, String errorMsg);

    }

    // 安装结果监听器
    private static class InstallResultListener implements IPackageInstallObserver {
        private final String packageName;
        private final PackageInstallObserver observer;
        private Handler handler;
        private Runnable timeoutRunnable;

        public InstallResultListener(String packageName, PackageInstallObserver observer) {
            this.packageName = packageName;
            this.observer = observer;
        }

        public void setHandler(Handler handler, Runnable timeoutRunnable) {
            this.handler = handler;
            this.timeoutRunnable = timeoutRunnable;
        }

        @Override
        public void packageInstalledResult(String packageName, int returnCode, Bundle extras) {
            Log.d(TAG, "收到安装结果: " + packageName + ", 返回码: " + returnCode);

            // 取消超时处理
            if (handler != null && timeoutRunnable != null) {
                handler.removeCallbacks(timeoutRunnable);
            }

            if (returnCode == InstallManagerInfo.INSTALL_SUCCEEDED) {
                Log.d(TAG, "APK安装成功: " + packageName);
                observer.onInstallSuccess("安装成功");
            } else {
                Log.e(TAG, "APK安装失败: " + packageName + ", 返回码: " + returnCode);
                String errorMsg = "安装失败，返回码: " + returnCode;
                observer.onInstallFailure("", errorMsg);
            }

            // 清理观察者
            sObservers.remove(packageName);
        }

        @Override
        public void reNotifyResultOnError(String packageName, int returnCode, Bundle extras) {
            packageInstalledResult(packageName, returnCode, extras);
        }
    }
}