/**
 * Operit Java Bridge Tester
 *
 * Focused test suite for the new Java/Kotlin bridge runtime:
 * - Java package-chain sugar / Java.use / Java.importClass / Kotlin
 * - Proxy-based class and instance calls
 * - Java.package and package-chain access
 * - Java.implement / Java.proxy
 * - NativeInterface.java* low-level bridge
 */

interface BridgeTestParams {
    caseName?: string;
    verbose?: boolean;
}

interface BridgeCaseResult {
    name: string;
    ok: boolean;
    durationMs: number;
    detail?: any;
    error?: string;
}

interface BridgeSuiteResult {
    startedAt: string;
    durationMs: number;
    passed: number;
    total: number;
    allPassed: boolean;
    results: BridgeCaseResult[];
}

type BridgeCaseHandler = (params: BridgeTestParams) => Promise<any> | any;

interface BridgeCaseDefinition {
    name: string;
    handler: BridgeCaseHandler;
}

function assertTrue(condition: any, message: string): void {
    if (!condition) {
        throw new Error(message);
    }
}

function assertEq(actual: any, expected: any, message: string): void {
    if (actual !== expected) {
        throw new Error(`${message} | expected=${String(expected)} actual=${String(actual)}`);
    }
}

function waitMs(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function waitUntil(predicate: () => boolean, timeoutMs: number, intervalMs: number): Promise<void> {
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
        if (predicate()) {
            return;
        }
        await waitMs(intervalMs);
    }
    throw new Error(`waitUntil timeout (${timeoutMs}ms)`);
}

function parseNativeResult(raw: string): any {
    const parsed = JSON.parse(raw);
    assertTrue(parsed && typeof parsed === "object", "native result must be object");
    return parsed;
}

async function runCase(definition: BridgeCaseDefinition, params: BridgeTestParams): Promise<BridgeCaseResult> {
    const started = Date.now();
    try {
        const detail = await definition.handler(params);
        return {
            name: definition.name,
            ok: true,
            durationMs: Date.now() - started,
            detail
        };
    } catch (error) {
        return {
            name: definition.name,
            ok: false,
            durationMs: Date.now() - started,
            error: String(error)
        };
    }
}

function caseBridgeExposed(): any {
    assertTrue(typeof Java === "object", "Java global must exist");
    assertTrue(typeof Kotlin === "object", "Kotlin global must exist");
    assertTrue(Java.classExists("java.lang.StringBuilder"), "StringBuilder should exist");
    assertTrue(Java.classExists("java.lang.Thread"), "Thread should exist");

    const pkgProbe = Java.java && Java.java.lang;
    assertTrue(!!pkgProbe, "Java package-chain proxy should be available");

    return {
        javaExposed: true,
        kotlinExposed: true
    };
}

function caseProxyStaticAndInstance(): any {
    const Integer = Java.java.lang.Integer;
    const StringBuilderA = Java.use("java.lang.StringBuilder");
    const StringBuilderB = Java.importClass("java.lang.StringBuilder");
    const StringBuilderK = Kotlin.type("java.lang.StringBuilder");

    const maxValue = Integer.MAX_VALUE;
    const parsed = Integer.parseInt("123");
    const parsedByApi = Java.callStatic("java.lang.Integer", "parseInt", "7");

    assertEq(maxValue, 2147483647, "Integer.MAX_VALUE mismatch");
    assertEq(parsed, 123, "Integer.parseInt mismatch");
    assertEq(parsedByApi, 7, "Java.callStatic parseInt mismatch");

    const sbA = new StringBuilderA();
    const sbB = StringBuilderB();
    const sbK = new StringBuilderK();

    sbA.append("A");
    sbA.append("B");
    sbB.append("C");
    sbK.append("K");

    assertEq(sbA.toString(), "AB", "sbA content mismatch");
    assertEq(sbB.toString(), "C", "sbB content mismatch");
    assertEq(sbK.toString(), "K", "sbK content mismatch");
    assertEq(sbA.length(), 2, "sbA length mismatch");

    return {
        integerMax: maxValue,
        parseInt: parsed
    };
}

function casePackageAccess(): any {
    const nowByChain = Number(Java.java.lang.System.currentTimeMillis());
    const nowByApi = Number(Java.callStatic("java.lang.System", "currentTimeMillis"));

    assertTrue(Number.isFinite(nowByChain), "nowByChain should be finite number");
    assertTrue(Number.isFinite(nowByApi), "nowByApi should be finite number");
    assertTrue(Math.abs(nowByChain - nowByApi) < 5000, "time delta should be small");

    const utilPkg = Java.package("java.util");
    const ArrayList = utilPkg.ArrayList;
    const list = new ArrayList();

    list.add("x");
    list.add("y");
    assertEq(list.size(), 2, "ArrayList size mismatch");

    return {
        nowByChain,
        nowByApi
    };
}

async function caseImplementRunnable(): Promise<any> {
    const Thread = Java.java.lang.Thread;
    const Runnable = Java.java.lang.Runnable;

    let runCount = 0;
    const runnable = Java.implement(Runnable, () => {
        runCount += 1;
    });

    const worker = new Thread(runnable);
    worker.start();
    await waitUntil(() => runCount > 0, 4000, 20);
    worker.join(2000);
    assertEq(runCount, 1, "Runnable should run exactly once");

    return {
        runCount
    };
}

async function caseImplementShorthand(): Promise<any> {
    const Thread = Java.java.lang.Thread;

    let runCount = 0;
    const runnable = Java.implement(() => {
        runCount += 1;
    });

    const worker = new Thread(runnable);
    worker.start();
    await waitUntil(() => runCount > 0, 4000, 20);
    worker.join(2000);
    assertEq(runCount, 1, "Shorthand implement should run once");

    return {
        runCount
    };
}

async function caseProxyAliasRunnable(): Promise<any> {
    const Thread = Java.java.lang.Thread;
    const Runnable = Java.java.lang.Runnable;

    let runCount = 0;
    const runnable = Java.proxy(Runnable, {
        run() {
            runCount += 1;
        }
    });

    const worker = new Thread(runnable);
    worker.start();
    await waitUntil(() => runCount > 0, 4000, 20);
    worker.join(2000);
    assertEq(runCount, 1, "Java.proxy runnable should run once");

    return {
        runCount
    };
}

async function caseImplementCallableReturn(): Promise<any> {
    const FutureTask = Java.java.util.concurrent.FutureTask;
    const Thread = Java.java.lang.Thread;
    const Callable = Java.java.util.concurrent.Callable;

    const callable = Java.implement(Callable, {
        call() {
            return "callable-ok";
        }
    });

    const future = new FutureTask(callable);
    const worker = new Thread(future);

    worker.start();
    await waitUntil(() => future.isDone(), 5000, 20);
    const value = future.get();
    assertEq(String(value), "callable-ok", "Callable return value mismatch");
    return {
        callableResult: String(value)
    };
}

function caseNativeLowLevel(): any {
    const raw = NativeInterface.javaCallStatic(
        "java.lang.Integer",
        "parseInt",
        JSON.stringify(["42"])
    );

    const parsed = parseNativeResult(raw);
    assertTrue(parsed.success === true, "native javaCallStatic should succeed");
    assertEq(parsed.data, 42, "native javaCallStatic data mismatch");

    return {
        raw,
        parsed
    };
}

function caseAndroidBridgeDirect(): any {
    const Build = Java.android.os.Build;
    const Version = Java.android.os.Build.VERSION;
    const Process = Java.android.os.Process;
    const SystemClock = Java.android.os.SystemClock;
    const ActivityLifecycleManager = Java.com.ai.assistance.operit.core.application.ActivityLifecycleManager;
    const AlertDialogBuilder = Java.android.app.AlertDialog.Builder;

    const manufacturer = String(Build.MANUFACTURER || "");
    const model = String(Build.MODEL || "");
    const brand = String(Build.BRAND || "");
    const sdkInt = Number(Version.SDK_INT);
    const release = String(Version.RELEASE || "");
    const pid = Number(Process.myPid());
    const uptimeMs = Number(SystemClock.uptimeMillis());
    const activity = ActivityLifecycleManager.INSTANCE.getCurrentActivity();

    let alertShown = false;
    let alertError = "";
    let builder: any = null;
    let dialog: any = null;

    try {
        assertTrue(!!activity, "current activity should not be null");
        builder = AlertDialogBuilder(activity);
        builder.setTitle("Bridge Alert");
        builder.setMessage(`bridge-alert-${Date.now()}`);
        builder.setCancelable(true);
        builder.setPositiveButton("OK", null);
        dialog = builder.show();
        alertShown = true;
    } catch (error) {
        alertError = String(error);
    }

    assertTrue(manufacturer.length > 0, "manufacturer should not be empty");
    assertTrue(model.length > 0, "model should not be empty");
    assertTrue(sdkInt > 0, "sdkInt should be positive");
    assertTrue(pid > 0, "pid should be positive");
    assertTrue(uptimeMs >= 0, "uptime should be non-negative");
    assertTrue(alertShown, `alert should be shown, error=${alertError}`);

    return {
        manufacturer,
        model,
        brand,
        sdkInt,
        release,
        pid,
        uptimeMs,
        alertShown,
        alertError
    };
}

const BRIDGE_CASES: BridgeCaseDefinition[] = [
    { name: "bridge_exposed", handler: caseBridgeExposed },
    { name: "proxy_static_and_instance", handler: caseProxyStaticAndInstance },
    { name: "package_access", handler: casePackageAccess },
    { name: "implement_runnable", handler: caseImplementRunnable },
    { name: "implement_shorthand", handler: caseImplementShorthand },
    { name: "proxy_alias_runnable", handler: caseProxyAliasRunnable },
    { name: "implement_callable_return", handler: caseImplementCallableReturn },
    { name: "native_low_level", handler: caseNativeLowLevel },
    { name: "android_bridge_direct", handler: caseAndroidBridgeDirect }
];

async function bridgeMain(params: BridgeTestParams = {}): Promise<void> {
    const startedAt = new Date().toISOString();
    const startedMs = Date.now();

    const requestedName = String(params.caseName || "").trim();
    const selectedCases = requestedName
        ? BRIDGE_CASES.filter(item => item.name === requestedName)
        : BRIDGE_CASES;

    if (selectedCases.length === 0) {
        complete({
            success: false,
            error: `unknown caseName: ${requestedName}`,
            availableCases: BRIDGE_CASES.map(item => item.name)
        });
        return;
    }

    const results: BridgeCaseResult[] = [];
    for (const definition of selectedCases) {
        if (params.verbose) {
            console.log(`[bridge-test] running ${definition.name}`);
        }
        const result = await runCase(definition, params);
        results.push(result);

        if (params.verbose) {
            console.log(
                `[bridge-test] ${definition.name} => ${result.ok ? "PASS" : "FAIL"} (${result.durationMs}ms)`
            );
            if (!result.ok) {
                console.error(`[bridge-test] ${definition.name} error: ${result.error}`);
            }
        }
    }

    const passed = results.filter(item => item.ok).length;
    const suiteResult: BridgeSuiteResult = {
        startedAt,
        durationMs: Date.now() - startedMs,
        passed,
        total: results.length,
        allPassed: passed === results.length,
        results
    };

    complete(suiteResult);
}

exports.main = bridgeMain;
exports.runCase = bridgeMain;
