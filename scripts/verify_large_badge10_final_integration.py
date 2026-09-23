from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
workflow = (ROOT / ".github/workflows/android-build.yml").read_text(encoding="utf-8")
checks = []
def check(name, ok): checks.append((name, bool(ok)))
required_steps = ["Verify Large Badge #2","Verify Large Badge #3","Verify Large Badge #4","Verify Large Badge #5","Verify Large Badge #6","Verify Large Badge #7","Verify Large Badge #8","Verify Large Badge #9","Verify final FYNX production certification gate","Build, test and lint","Run Android instrumentation tests and capture the real FYNX UI","Upload FYNX runtime screenshots and UI hierarchies","Upload exact-commit debug APK"]
check("all major certification gates remain in the release workflow", all(x in workflow for x in required_steps))
check("Android release build runs lint, unit tests and APK assembly", "./gradlew lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest" in workflow)
check("real-device instrumentation remains part of release certification", "reactivecircus/android-emulator-runner@v2" in workflow and "connectedDebugAndroidTest" in workflow)
 runtime_navigation = (ROOT / "scripts/verify_runtime_navigation.py").read_text(encoding="utf-8")
check("runtime screenshots and UI hierarchy are captured", "screencap" in runtime_navigation and "uiautomator" in runtime_navigation and "verify_runtime_navigation.py" in workflow)
check("exact-commit APK artifact is produced", "FYNX-debug-apk-${{ github.sha }}" in workflow and "FYNX-debug-${GITHUB_SHA}.apk" in workflow)
check("production certification runs before Android build", workflow.index("Verify final FYNX production certification gate") < workflow.index("Build, test and lint"))
journey = (ROOT / "scripts/verify_fynx_journey.py").read_text(encoding="utf-8")
production = (ROOT / "scripts/verify_fynx_production.py").read_text(encoding="utf-8")
check("consolidated user journey remains wired", "Verify consolidated FYNX user journey integration" in workflow and "authenticated app gate exists" in journey)
check("admin controls remain server-role gated", "admin center is server-role gated" in journey)
check("marketplace protection remains in the final audit", "protected marketplace transaction backend remains present" in journey)
check("trust and safety remains in the release workflow", "Verify Large Badge #9" in workflow)
check("common API secret patterns remain blocked", "secret_pattern" in production and "no common API secret pattern" in production)
failed = [n for n, ok in checks if not ok]
for n, ok in checks: print(("PASS: " if ok else "FAIL: ") + n)
if failed: raise SystemExit("Large Badge #10 final integration certification failed: " + "; ".join(failed))
print(f"Large Badge #10 final integration certification passed ({len(checks)} checks)")
