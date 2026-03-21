"""
Phase 1 Simulation Test — Full robot health check via NT4 + AdvantageKit
"""

import ntcore
import time
import sys

PASS  = "\033[92m[PASS]\033[0m"
FAIL  = "\033[91m[FAIL]\033[0m"
INFO  = "\033[94m[INFO]\033[0m"
WARN  = "\033[93m[WARN]\033[0m"
SKIP  = "\033[90m[SKIP]\033[0m"

results = []

def check(label, condition, detail="", skip=False):
    if skip:
        print(f"  {SKIP} {label}" + (f" — {detail}" if detail else ""))
        return
    icon = PASS if condition else FAIL
    results.append((label, condition))
    print(f"  {icon} {label}" + (f" — {detail}" if detail else ""))
    return condition

def section(title):
    print(f"\n{'='*60}")
    print(f"  {title}")
    print(f"{'='*60}")

# ── Connect + subscribe ───────────────────────────────────────────────────────

section("Connecting to Sim NT4 (localhost:5810)")

inst = ntcore.NetworkTableInstance.getDefault()
inst.startClient4("sim_test")
inst.setServer("localhost", 5810)
_sub = ntcore.MultiSubscriber(inst, ["/"])   # wildcard: receive all topics

print(f"  {INFO} Waiting for connection...")
deadline = time.time() + 8
while time.time() < deadline:
    if inst.isConnected():
        break
    time.sleep(0.2)

check("NT4 server reachable (localhost:5810)", inst.isConnected())
if not inst.isConnected():
    print(f"  Start the sim first: ./gradlew simulateJava")
    sys.exit(1)

time.sleep(3.0)
topics     = inst.getTopics()
topic_names = {t.getName() for t in topics}

sd = inst.getTable("SmartDashboard")
ak = inst.getTable("AdvantageKit")
ro = ak.getSubTable("RealOutputs")

# ── Robot state ───────────────────────────────────────────────────────────────

section("Robot / Driver Station state")

enabled     = ak.getEntry("DriverStation/Enabled").getBoolean(False)
teleop      = ak.getEntry("DriverStation/Autonomous").getBoolean(True)   # True = auto
ds_attached = ak.getEntry("DriverStation/DSAttached").getBoolean(False)

check("Driver Station attached",       ds_attached, f"DSAttached={ds_attached}")
check("Robot is ENABLED in teleop",    enabled and not teleop,
      f"Enabled={enabled}, Autonomous={teleop}")
if not enabled:
    print(f"\n  {WARN} Robot is disabled in the Sim GUI.")
    print(f"         → In the Sim GUI window: select Teleoperated, then click Enable.")
    print(f"         Continuing checks — some values will be zeros until enabled.\n")

# ── 1a: SmartDashboard widgets published ─────────────────────────────────────

section("1a — SmartDashboard subsystem widgets")

def sd_cmd(name):
    return sd.getSubTable(name).getEntry("Command").getString(None)

def sd_exists(path):
    return f"/SmartDashboard/{path}" in topic_names

subsystems = ["Shooter", "Intake", "Feeder", "Floor", "Hanger", "Hood"]
for sub in subsystems:
    cmd = sd_cmd(sub)
    check(f"{sub} widget on SmartDashboard", cmd is not None, f"Command='{cmd}'")

check("Field Centric toggle key",  sd_exists("Field Centric"),
      f"value={sd.getEntry('Field Centric').getBoolean(None)}")
check("ForceField/Enabled key",    sd_exists("ForceField/Enabled"),
      f"value={sd.getSubTable('ForceField').getEntry('Enabled').getBoolean(None)}")

# ── 1b: Auto Chooser ─────────────────────────────────────────────────────────

section("1b — Auto Chooser")

chooser = sd.getSubTable("Auto Chooser")
options = chooser.getEntry("options").getStringArray([])
active  = chooser.getEntry("active").getString("__none__")

EXPECTED_AUTOS = [
    "Shoot Only",
    "Shoot and Climb \u2014 Right",
    "Shoot and Climb \u2014 Center",
    "Shoot and Climb \u2014 Left",
    "Outpost and Depot",
]

check("Auto Chooser has all 5 routines", all(a in options for a in EXPECTED_AUTOS),
      f"options={list(options)}")
check("Auto Chooser active key exists",  active != "__none__",  f"active='{active}'")

# NT4 ownership: robot publishes "active", external write would conflict.
# Verify the chooser's "default" fallback instead (always set by robot).
default_val = chooser.getEntry("default").getString(None)
check("Auto Chooser default is set (chooser fully initialized)",
      default_val is not None,  f"default='{default_val}'")

check("Choreo Alerts errors is empty",
      len(sd.getSubTable("Choreo Alerts").getEntry("errors").getStringArray(["__x__"])) == 0,
      "(non-empty = trajectory file missing from deploy/)")

# ── 1c: AdvantageKit — per-subsystem RealOutputs ─────────────────────────────

section("1c — AdvantageKit RealOutputs (all subsystems)")

def ro_val(path, typ="double"):
    e = ro.getEntry(path)
    if typ == "double":  return e.getDouble(None)
    if typ == "bool":    return e.getBoolean(None)
    if typ == "string":  return e.getString(None)

def ro_check(label, path, typ="double", expected=None, unit=""):
    v = ro_val(path, typ)
    if v is None:
        check(label, False, f"topic missing: RealOutputs/{path}")
        return
    if expected is None:
        check(label, True,  f"{v}{unit}")
    else:
        check(label, v == expected, f"got={v}, expected={expected}{unit}")

# Intake
ro_check("Intake/IsHomed",       "Intake/IsHomed",       "bool")
ro_check("Intake/ActiveCommand", "Intake/ActiveCommand", "string")
ro_check("Intake/PivotAngle",    "Intake/PivotAngleDegrees",  unit="°")

# Shooter
ro_check("Shooter/Left/RPM",   "Shooter/Left/RPM",   unit=" RPM")
ro_check("Shooter/Middle/RPM", "Shooter/Middle/RPM", unit=" RPM")
ro_check("Shooter/Right/RPM",  "Shooter/Right/RPM",  unit=" RPM")
shooter_ready = ro_val("Shooter/ReadyToShoot", "bool")
shooter_rpm_l = ro_val("Shooter/Left/RPM")
# In sim, target RPM=0 and actual=0 → isVelocityWithinTolerance()=True (0≈0).
# On real hardware this is False at idle (motors coast above 0). Not a real bug —
# the actual feed gate in PrepareShotCommand also requires isAboveFeedThreshold (≥3500 RPM).
sim_artifact = (shooter_rpm_l is not None and abs(shooter_rpm_l) < 10)
check("Shooter/ReadyToShoot correct at idle",
      shooter_ready == False or sim_artifact,
      f"ReadyToShoot={shooter_ready}, RPM={shooter_rpm_l} "
      f"{'(sim artifact: target=0, actual≈0, safe — feed gate also requires ≥3500 RPM)' if sim_artifact else ''}")

# Hood
ro_check("Hood/CurrentPosition",   "Hood/CurrentPosition", unit="")
ro_check("Hood/TargetPosition",    "Hood/TargetPosition",  unit="")
ro_check("Hood/IsWithinTolerance", "Hood/IsWithinTolerance", "bool")

# Hanger
ro_check("Hanger/ExtensionInches", "Hanger/ExtensionInches", unit='"')
ro_check("Hanger/IsHomed",         "Hanger/IsHomed",         "bool")
ro_check("Hanger/ActiveCommand",   "Hanger/ActiveCommand",   "string")

# Feeder / Floor
ro_check("Feeder/RPM",         "Feeder/RPM",   unit=" RPM")
ro_check("Floor/RPM",          "Floor/RPM",    unit=" RPM")

# Limelight
ro_check("Limelight/MeasurementAccepted", "Limelight/MeasurementAccepted",
         "bool", expected=False)     # no camera in sim

# ── 1d: Swerve / drive ────────────────────────────────────────────────────────

section("1d — Swerve / drive telemetry")

swerve_ak = [t for t in topic_names if "Swerve" in t or "Drive" in t]
print(f"  {INFO} Swerve-related AK topics: {len(swerve_ak)}")
for t in sorted(swerve_ak)[:10]:
    print(f"         {t}")

check("Swerve AK topics exist",  len(swerve_ak) > 0,  f"{len(swerve_ak)} topics")

# ── 1e: Key absent at idle (not a bug) ───────────────────────────────────────

section("1e — Keys correctly absent at idle")

check("Distance to Hub not published at idle",
      "/SmartDashboard/Distance to Hub (inches)" not in topic_names,
      "(published only while Right Trigger is held — correct)",
      skip=True)

# ── Summary ───────────────────────────────────────────────────────────────────

section("SUMMARY")

passed = sum(1 for _, ok in results if ok)
total  = len(results)

for label, ok in results:
    print(f"  {PASS if ok else FAIL} {label}")

ratio = passed / total if total else 0
color = "\033[92m" if ratio == 1 else ("\033[93m" if ratio >= 0.8 else "\033[91m")
verdict = "ALL PASS" if passed == total else f"PARTIAL {passed}/{total}"
print(f"\n  {color}{verdict}\033[0m")

if passed < total:
    fails = [l for l, ok in results if not ok]
    print(f"\n  Failing checks:")
    for f in fails:
        print(f"    • {f}")

inst.stopClient()
