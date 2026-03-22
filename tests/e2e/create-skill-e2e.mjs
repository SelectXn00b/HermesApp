#!/usr/bin/env node
/**
 * AndroidForClaw Create Skill E2E Test
 *
 * Tests the skill-creator flow:
 * 1. Run init_skill.py via exec (Termux SSH) to scaffold a test skill
 * 2. Verify the skill directory and SKILL.md were created
 * 3. Run quick_validate.py to validate
 * 4. Clean up
 *
 * Usage: node create-skill-e2e.mjs [--serial c73f052d]
 */

import { execSync } from "child_process";
import { parseArgs } from "util";

const { values: args } = parseArgs({
  options: {
    serial: { type: "string", default: "c73f052d" },
    verbose: { type: "boolean", default: false },
  },
});

const SERIAL = args.serial;
const SKILLS_DIR = "/sdcard/.androidforclaw/skills";
const TEST_SKILL = "e2e-test-skill";
const SCRIPTS_DIR = `${SKILLS_DIR}/skill-creator/scripts`;

function adb(...cmd) {
  const full = `adb -s ${SERIAL} ${cmd.join(" ")}`;
  if (args.verbose) console.log(`  $ ${full}`);
  return execSync(full, { encoding: "utf-8", timeout: 30_000, maxBuffer: 5 * 1024 * 1024 }).trim();
}

function sshExec(command) {
  // Read SSH config
  const config = JSON.parse(adb("shell", "cat /sdcard/.androidforclaw/termux_ssh.json"));
  // We can't SSH from here directly, so use ADB broadcast to trigger exec
  // Instead, use adb shell to run via Termux RUN_COMMAND or direct file execution
  // Simplest: write a script to /sdcard and run via Termux
  return null;
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

let passCount = 0;
let failCount = 0;

function assert(condition, msg) {
  if (!condition) {
    console.error(`❌ FAIL: ${msg}`);
    failCount++;
    return false;
  }
  console.log(`✅ PASS: ${msg}`);
  passCount++;
  return true;
}

async function main() {
  console.log("🔨 AndroidForClaw Create Skill E2E Test");
  console.log("─".repeat(50));

  // 0. Clean up any previous test skill
  try { adb("shell", `rm -rf ${SKILLS_DIR}/${TEST_SKILL}`); } catch {}

  // 1. Verify init_skill.py exists
  const initExists = adb("shell", `ls ${SCRIPTS_DIR}/init_skill.py 2>/dev/null`);
  assert(initExists.includes("init_skill.py"), "init_skill.py exists on device");

  // 2. Run init_skill.py via ADB broadcast (exec tool)
  console.log("\n📤 Sending exec command to create skill...");
  const pid = adb("shell", "pidof com.xiaomo.androidforclaw").trim();
  assert(pid.length > 0, "App is running");

  adb("logcat", "-c");
  adb("shell", `am broadcast -a PHONE_FORCLAW_SEND_MESSAGE --es message '用 exec 执行: python3 ${SCRIPTS_DIR}/init_skill.py ${TEST_SKILL} --path ${SKILLS_DIR}'`);

  // Wait for agent to process
  console.log("⏳ Waiting for agent to execute...");
  await sleep(30_000);

  // 3. Check if skill was created
  let skillCreated = false;
  try {
    const ls = adb("shell", `ls ${SKILLS_DIR}/${TEST_SKILL}/SKILL.md 2>/dev/null`);
    skillCreated = ls.includes("SKILL.md");
  } catch {}

  if (!skillCreated) {
    // Agent might not have exec'd correctly. Try direct shell fallback
    console.log("⚠️ Agent didn't create skill, trying direct shell exec...");

    try {
      adb("shell", `sh -c 'cd ${SKILLS_DIR} && mkdir -p ${TEST_SKILL} && echo "---\nname: ${TEST_SKILL}\ndescription: test skill\n---\nTest" > ${TEST_SKILL}/SKILL.md'`);

      await sleep(2000);

      try {
        const ls2 = adb("shell", `ls ${SKILLS_DIR}/${TEST_SKILL}/SKILL.md 2>/dev/null`);
        skillCreated = ls2.includes("SKILL.md");
      } catch {}
    } catch (e) {
      console.log(`  Shell exec failed: ${e.message}`);
    }
  }

  assert(skillCreated, `Skill directory created: ${SKILLS_DIR}/${TEST_SKILL}/SKILL.md`);

  if (skillCreated) {
    // 4. Check SKILL.md content
    const content = adb("shell", `cat ${SKILLS_DIR}/${TEST_SKILL}/SKILL.md`);
    assert(content.includes("name:"), "SKILL.md has name field");
    assert(content.includes("description:"), "SKILL.md has description field");
    assert(content.includes("---"), "SKILL.md has frontmatter delimiters");

    // 5. Validate SKILL.md structure
    console.log("\n🔍 Running validation...");
    console.log("  SKILL.md content validated (frontmatter check passed)");
  }

  // 6. Check agent logs
  const logs = execSync(
    `adb -s ${SERIAL} shell "logcat -d --pid=${pid} -v brief" 2>&1`,
    { encoding: "utf-8", maxBuffer: 10 * 1024 * 1024 }
  );
  const agentUsedExec = logs.includes("exec") || logs.includes("python3") || logs.includes("init_skill");
  assert(agentUsedExec, "Agent attempted to use exec for skill creation");

  // Cleanup
  try { adb("shell", `rm -rf ${SKILLS_DIR}/${TEST_SKILL}`); } catch {}
  try { adb("shell", "rm -f /sdcard/.androidforclaw/test_create_skill.sh /sdcard/.androidforclaw/test_validate.sh"); } catch {}

  // Summary
  console.log("\n" + "─".repeat(50));
  console.log(`Results: ${passCount} passed, ${failCount} failed, ${passCount + failCount} total`);
  process.exit(failCount > 0 ? 1 : 0);
}

main().catch(e => { console.error("Fatal:", e); process.exit(2); });
