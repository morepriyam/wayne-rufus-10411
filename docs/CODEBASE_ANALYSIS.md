# Codebase Analysis — Wayne HS 10411 Rufus

This document summarizes the full robot codebase so any change or debug session has context.

---

## 1. Project overview

- **Stack:** FRC 2026, WPILib, Java 17. Gradle with WPILib GradleRIO 2026.1.1.
- **Robot:** Team 10411, deploy to `10.104.11.2`. Single RoboRIO CAN bus `"rio"`; CANivore `"main"` is defined in `Ports` but only the rio bus is used for Talon FX devices.
- **Pattern:** Command-based. Subsystems own hardware; commands are bound to triggers in `RobotContainer`. AdvantageKit for logging (`.wpilog` to `/home/lvuser/logs` + NT4 for live AdvantageScope).
- **External deps:** CTRE Phoenix 6 (Talon FX, CANcoder, Pigeon 2), ChoreoLib (trajectories), LimelightHelpers (vision), Refinery ForceField (composite build in `refinery-forcefield/`).

---

## 2. Entry point and robot lifecycle

- **Main.java** — `RobotBase.startRobot(Robot::new)`.
- **Robot.java** (extends `LoggedRobot`):
  - Constructor: builds `RobotContainer`, puts CommandScheduler on SmartDashboard, sets brownout 6.1 V, starts AdvantageKit (WPILOGWriter + NT4Publisher).
  - `robotPeriodic()`: runs CommandScheduler, logs battery voltage and match time, calls `m_robotContainer.updateForceFieldConfig()`.

---

## 3. RobotContainer — wiring and bindings

**Subsystems (all created here):**

- **Swerve** — CTRE Tuner swerve (Pigeon 2 id 13, 4 modules on CAN "rio"). Default command: `ManualDriveCommand`.
- **Intake** — Pivot (Talon 20) + rollers (Talon 22). Positions: HOMED 110°, STOWED 100°, INTAKE -4°, AGITATE 20°. Homing by current spike >6 A at hard stop.
- **Floor** — Single Talon 15. Speeds: STOP, FEED 0.83, REVERSE -0.83.
- **Feeder** — Single Talon 19. Feed at 5000 RPM (velocity control).
- **Shooter** — Three Talons (21, 16, 18). Velocity control; feed threshold 3500 RPM. Default command: `run(shooter::stop)`. Dashboard “Target RPM” for manual shoot.
- **Hood** — Two PWM servos (3, 4). Position 0.01–0.77; simulated motion at 20 mm/s for “within tolerance” logic.
- **Hanger** — Talon 14. Positions: HOMED 0", EXTEND_HOPPER 2", HANGING 6", HUNG 0.2". Homing by supply current >0.4 A at retract stop.
- **Limelight** — Name `"limelight"`. Pipeline 0 = AprilTag, 1 = Fuel (Neural Detector). Default command: `updateVisionCommand()` (runs every cycle, even when disabled).

**Other objects:**

- **ForceFieldEngine** — Loads `deploy/forcefield/default.json`, uses `ForceField.kCornerOffsets` (from TunerConstants module positions). **ForceFieldConfig** updated in `robotPeriodic` for preset/tuning.
- **CommandXboxController** port 0 = driver.
- **AutoRoutines** — Registers routines and puts Auto Chooser on SmartDashboard; autonomous trigger runs chooser’s selected command.
- **SubsystemCommands** — Builds `aimAndShoot()`, `shootManually()`, and feed sequence; takes driver stick suppliers for aim-and-shoot translation.
- **SwerveTelemetry** — Registered so swerve state is pushed to NT (DriveState, Pose, Field2d, Mechanism2d modules) and AdvantageKit.

**Bindings (configureBindings):**

- **Drive:** Default = `ManualDriveCommand` (left Y/X = forward/strafe, right X = rotate). Field-centric from SmartDashboard “Field Centric”. A/B/X/Y = lock heading 180°/90°R/90°L/0°. Back = re-zero field-centric. Right stick button = toggle force field.
- **First enable (teleop or auto, not test):** Intake homing + Hanger homing in parallel.
- **Right Trigger:** `aimAndShoot()` (aim at hub, prepare shot, feed when aimed and ready).
- **Right Bumper:** `shootManually()` (dashboard RPM, feed above 3500 RPM).
- **Left Trigger:** Intake deploy + rollers (hold).
- **Left Bumper:** Intake stow.
- **D-Pad Up/Down:** Hanger HANGING / HUNG.
- **D-Pad Left:** Floor + Shooter reverse (unjam).
- **Start:** `FuelChaseCommand` (Limelight fuel detector + intake).

**Vision:** `updateVisionCommand()` gets current pose from swerve, calls `limelight.getMeasurement(pose)` (MegaTag1 + MegaTag2, robot orientation set for MegaTag2). If present, `swerve.addVisionMeasurement(pose, timestamp, stdDevs [0.1, 0.1, 10])`.

---

## 4. Constants and config

- **Constants.Driving** — Max speed from TunerConstants (5.85 m/s at 12 V), limited speed 4 m/s, max rotation 1 rot/s, rotation deadband 0.005.
- **Constants.KrakenX60** — Free speed 6000 RPM (for KV and limits).
- **Constants.Vision** — PIPELINE_APRILTAG 0, PIPELINE_FUEL_DETECTOR 1.
- **Constants.ForceField** — Preset `"default"`, corner offsets from TunerConstants (FL/FR/BL/BR positions).
- **Ports** — CAN buses rio/main; Talon IDs (Intake 20/22, Floor 15, Feeder 19, Shooter 21/16/18, Hanger 14); Hood servos 3/4.
- **Landmarks** — `hubPosition()`: Blue (182.105", 158.845"), Red (469.115", 158.845") in inches, returned as Translation2d.

---

## 5. Subsystems (detail)

- **Swerve** — Extends `TunerConstants.TunerSwerveDrivetrain`. Path following via `followPath(SwerveSample)` with PID on x, y, theta; vision via overridden `addVisionMeasurement` (FPGA time conversion). `periodic()` applies operator perspective (alliance) and seeds field-centric on first apply.
- **Intake** — Pivot Motion Magic to positions; rollers VoltageOut. Homing: 10% out until supply current >6 A, then set position HOMED and move to STOWED; `kCancelIncoming`. Agitate: INTAKE → AGITATE → INTAKE repeatedly.
- **Floor / Feeder / Shooter** — Voltage or velocity control; Shooter spin-up overshoots by 15% and uses `isAboveFeedThreshold()` (all motors ≥3500 RPM).
- **Hood** — Servo setpoints; `currentPosition` simulated toward `targetPosition` at max speed for “within tolerance.”
- **Hanger** — Motion Magic to extension angles (6" per 142 rotations). Homing: -5% until supply >0.4 A, zero, then EXTEND_HOPPER; `kCancelSelf`.
- **Limelight** — `getMeasurement(pose)` sends robot yaw to camera, reads MegaTag1 and MegaTag2; requires both and tagCount>0; merges MegaTag2 position with MegaTag1 rotation; returns Optional with std devs [0.1, 0.1, 10]. Publishes to SmartDashboard and Logger.

---

## 6. Commands

- **ManualDriveCommand** — Field-centric or robot-centric (from dashboard). Heading lock after 0.25 s of no rotation input. Force field adds velocity/angular offsets when enabled. Uses DriveInputSmoother (deadband 0.15, curve exponent 1.5).
- **AimAndDriveCommand** — Field-centric facing angle: target = direction to hub (from pose + Landmarks) + 180° (shooter at back). Aim tolerance 5°. Uses same smoothed forward/left for translation.
- **SubsystemCommands.aimAndShoot()** — Parallel: (1) AimAndDriveCommand, (2) after 0.25 s PrepareShotCommand, (3) waitUntil(aimed && readyToShoot) then feed(). Feed = 0.25 s delay, then feeder.feedCommand() and (0.125 s later) floor.feedCommand() + intake.agitateCommand().
- **SubsystemCommands.shootManually()** — Deferred: shooter spin-up at dashboard RPM, waitUntil(aboveFeedThreshold), then same feed sequence.
- **PrepareShotCommand** — Distance to hub from pose supplier → InterpolatingTreeMap (52", 114.4", 165.5" → 5000 RPM and hood 0.19/0.40/0.48). Sets shooter RPM and hood position each cycle. Ready = shooter above 3500 RPM and hood within tolerance.
- **FuelChaseCommand** — On init: Limelight pipeline 1 (Fuel). Each cycle: intake INTAKE + rollers INTAKE; read raw detections; weighted-average txnc by area; robot-centric drive forward at 0.3 max speed when target, rotate with kP 0.7 to center. On end: rollers stop, pipeline 0.
- **AutoRoutines** — Shoot Only (aimAndShoot 5 s). Shoot and Climb L/C/R: reset odometry to trajectory start, aimAndShoot 5 s, run trajectory to tower, hanger HANGING during drive and HUNG on arrival. Outpost and Depot: four segments (start→outpost→depot→shooting pose→tower); intake deploy after hanger homed; spin shooter/hood during depot→shooting; aimAndShoot then final segment; limelight.idle() during path segments that need AprilTag.

---

## 7. Util

- **DriveInputSmoother** — Deadband 0.15, curve 1.5, outputs ManualDriveInput (forward, left, rotation).
- **ManualDriveInput** — hasTranslation(), hasRotation() for state logic.
- **GeometryUtil** — isNear(Rotation2d, Rotation2d, Angle) with angle modulus.
- **Stopwatch** — elapsed time for heading-lock delay.
- **SwerveTelemetry** — Pushes pose, speeds, module states/targets/positions to NT and Logger (DriveState, Pose/robotPose, Module 0–3 mechanisms).

---

## 8. Generated

- **TunerConstants** — CTRE Tuner swerve: Pigeon 13, four modules (drive/steer/encoder IDs, offsets, positions ±10.875", ±10.125"), kSpeedAt12Volts 5.85 m/s, gear ratios, gains. Defines `TunerSwerveDrivetrain` with optional odometry/vision std devs.
- **ChoreoTraj** — Record (name, segment, totalTimeSecs, initialPoseBlue, endPoseBlue). OutpostAndDepotTrajectory and segments $0–$3. Method `asAutoTraj(AutoRoutine)` for ChoreoLib.
- **ShootAndClimbTraj** — Manually maintained ChoreoTraj instances for Right/Center/Left (different start Y, same end at tower). Loads .traj from deploy/choreo/.
- **ChoreoVars** — Empty Poses class; placeholder for Choreo GUI vars.

---

## 9. Deploy and assets

- **src/main/deploy** — Copied to roboRIO `/home/lvuser/deploy` (deleteOldFiles false). Contains:
  - **forcefield/default.json** — Wall lines (field 0–16.54 m x 0–8.21 m), hub radials (blue/red), shoot point charges (gaussian).
  - **choreo/** — ChoreoProject.chor, ShootAndClimbTrajectory_Left/Center/Right.traj, OutpostAndDepotTrajectory.traj (and segments).
- **limelight-config/** (project root) — 2026-rebuilt-welded.json, 2026-rebuilt-andymark.json; uploaded to Limelight UI, not deployed by Gradle.

---

## 10. Data flow summary

- **Pose:** Swerve odometry (TunerSwerveDrivetrain) + Limelight vision (MegaTag1/2) with std devs [0.1, 0.1, 10]. Pose used by AimAndDrive, PrepareShot, auto path following, and force field.
- **Shooting:** Right Trigger = pose → direction to hub + distance → aim (FieldCentricFacingAngle) + PrepareShot (RPM/hood table) → feed when aimed and ready (shooter ≥3500 RPM, hood in tolerance). Right Bumper = dashboard RPM, feed above 3500 RPM.
- **Driver inputs:** Xbox 0; ManualDriveCommand and AimAndDriveCommand use negative left Y/X for forward/left; rotation negative right X. Field-centric and force field toggles from dashboard/button.
- **Autonomous:** Chooser on SmartDashboard; ChoreoLib mirrors for Red; trajectories reset odometry to start pose then run path + triggers (hanger, intake, shooter, aimAndShoot).

---

## 11. File map (quick reference)

| Area | Files |
|------|--------|
| Entry | Main.java, Robot.java, RobotContainer.java |
| Config | Constants.java, Ports.java, Landmarks.java |
| Subsystems | Swerve, Intake, Floor, Feeder, Shooter, Hood, Hanger, Limelight |
| Commands | ManualDriveCommand, AimAndDriveCommand, SubsystemCommands, PrepareShotCommand, FuelChaseCommand, AutoRoutines |
| Util | DriveInputSmoother, ManualDriveInput, GeometryUtil, Stopwatch, SwerveTelemetry |
| Vision | LimelightHelpers.java (in robot package) |
| Generated | TunerConstants, ChoreoTraj, ShootAndClimbTraj, ChoreoVars |
| Deploy | forcefield/default.json, choreo/*.traj, choreo/ChoreoProject.chor |

This gives a single reference for how the robot is wired, how data flows, and where to look when changing or debugging behavior.
