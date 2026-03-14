#!/usr/bin/env node
/**
 * AndroidForClaw Skill Hub E2E Test
 *
 * Tests: "看看有什么 skill hub 里边" should:
 * 1. Agent receives the message
 * 2. Agent searches ClawHub (via web_fetch or skills_search tool)
 * 3. Returns a list of available skills from ClawHub
 *
 * Usage:
 *   node skill-hub-e2e.mjs [--serial c73f052d]
 */

import { execSync } from "child_process";
import { parseArgs } from "util";

const { values: args } = parseArgs({
  options: {
    serial: { type: "string", default: "c73f052d" },
    timeout: { type: "string", default: "60" },
    verbose: { type: "boolean", default: false },
  },
});

const SERIAL = args.serial;
const TIMEOUT_S = parseInt(args.timeout);

function adb(...cmd) {
  const full = `adb -s ${SERIAL} ${cmd.join(" ")}`;
  if (args.verbose) console.log(`  $ ${full}`);
  return execSync(full, { encoding: "utf-8", timeout: 15_000, maxBuffer: 10 * 1024 * 1024 }).trim();
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

let passCount = 0;
let failCount = 0;
const results = [];

function assert(condition, msg) {
  if (!condition) {
    console.error(`❌ FAIL: ${msg}`);
    failCount++;
    results.push({ name: msg, status: "FAIL" });
    return false;
  }
  console.log(`✅ PASS: ${msg}`);
  passCount++;
  results.push({ name: msg, status: "PASS" });
  return true;
}

async function main() {
  console.log("🔍 AndroidForClaw Skill Hub E2E Test");
  console.log(`   Device: ${SERIAL}`);
  console.log("─".repeat(50));

  // Ensure app is running
  const pid = adb("shell", "pidof", "com.xiaomo.androidforclaw").trim();
  assert(pid.length > 0, "App is running");

  // Clear logcat
  adb("logcat", "-c");

  // Send the test message
  console.log("\n📤 Sending: 看看有什么 skill hub 里边");
  adb("shell", `am broadcast -a PHONE_FORCLAW_SEND_MESSAGE --es message '看看clawhub上有什么skill'`);

  // Wait for agent to process
  console.log("⏳ Waiting for agent response...");
  await sleep(TIMEOUT_S * 1000);

  // Collect logs
  const allLogs = execSync(
    `adb -s ${SERIAL} shell "logcat -d --pid=${pid} -v brief" 2>&1`,
    { encoding: "utf-8", maxBuffer: 10 * 1024 * 1024 }
  );

  // Parse relevant log lines
  const agentLines = allLogs.split("\n").filter(l =>
    l.includes(pid) && (
      /AgentLoop|ToolCall|Function|web_fetch|web_search|ClawHub|clawhub|skill|Final content|Content:|Raw response/.test(l)
    )
  );

  if (args.verbose) {
    console.log("\n📋 Relevant logs:");
    agentLines.slice(0, 30).forEach(l => console.log(`    ${l}`));
  }

  // Check: agent received the message
  const receivedMsg = allLogs.includes("clawhub") || allLogs.includes("skill");
  assert(receivedMsg, "Agent received skill hub query");

  // Check: agent tried to search (via web_fetch to clawhub.com or skills.search or list_dir)
  const triedSearch = agentLines.some(l =>
    l.includes("web_fetch") ||
    l.includes("web_search") ||
    l.includes("clawhub") ||
    l.includes("list_dir") ||
    l.includes("skills")
  );
  assert(triedSearch, "Agent attempted to search for skills");

  // Check: agent produced a final response
  const hasFinalContent = agentLines.some(l =>
    l.includes("Final content") || l.includes("Agent Loop 结束")
  );
  assert(hasFinalContent, "Agent produced a final response");

  // Check: response mentions skills (any skill names)
  const responseLines = agentLines.filter(l => l.includes("Content:"));
  const responseText = responseLines.join("\n");
  const mentionsSkills = responseText.includes("Skill") ||
    responseText.includes("skill") ||
    responseText.includes("browser") ||
    responseText.includes("weather") ||
    responseText.includes("feishu") ||
    responseText.includes("termux");
  assert(mentionsSkills, "Response mentions available skills");

  // Check: no crash
  const appStillRunning = adb("shell", "pidof", "com.xiaomo.androidforclaw").trim().length > 0;
  assert(appStillRunning, "App still running after query");

  // Summary
  console.log("\n" + "─".repeat(50));
  console.log(`Results: ${passCount} passed, ${failCount} failed, ${passCount + failCount} total`);

  if (failCount > 0) {
    console.log("\nFailed tests:");
    results.filter(r => r.status === "FAIL").forEach(r => console.log(`  ❌ ${r.name}`));
  }

  process.exit(failCount > 0 ? 1 : 0);
}

main().catch(e => {
  console.error("Fatal:", e);
  process.exit(2);
});
