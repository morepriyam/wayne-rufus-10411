// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import static edu.wpi.first.units.Units.MetersPerSecond;

import java.util.Optional;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.button.RobotModeTriggers;
import edu.wpi.first.wpilibj2.command.button.Trigger;
import frc.robot.Constants.Driving;
import frc.robot.Constants.ForceField;
import frc.robot.commands.AutoRoutines;
import frc.robot.commands.ManualDriveCommand;
import frc.robot.commands.PrepareShotCommand.Shot;
import frc.robot.commands.SubsystemCommands;
import com.bionanomics.refinery.forcefield.ForceFieldConfig;
import com.bionanomics.refinery.forcefield.ForceFieldEngine;
import com.bionanomics.refinery.forcefield.ForceFieldMap;
import frc.robot.subsystems.Feeder;
import frc.robot.subsystems.Floor;
import frc.robot.subsystems.Hood;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.Limelight;
import frc.robot.subsystems.Shooter;
import frc.robot.subsystems.Swerve;
import frc.util.SwerveTelemetry;

/**
 * This class is where the bulk of the robot should be declared. Since
 * Command-based is a
 * "declarative" paradigm, very little robot logic should actually be handled in
 * the {@link Robot}
 * periodic methods (other than the scheduler calls). Instead, the structure of
 * the robot (including
 * subsystems, commands, and trigger mappings) should be declared here.
 */
public class RobotContainer {
    /** Named shot presets — distance label, RPM, hood position. Tune RPMs per distance. */
    private static final String[] PRESET_NAMES = { "CLOSE (~52\")", "MID (~114\")", "FAR (~166\")" };
    private static final Shot[] SHOT_PRESETS = {
        new Shot(3750, 0.19), // close  ~52"
        new Shot(3700, 0.40), // mid   ~114"
        new Shot(3950, 0.48), // far   ~166"
    };
    /** Mutable index wrapped in array so lambdas can mutate it. */
    private final int[] presetIndex = { 0 };

    private final Swerve swerve = new Swerve();
    private final Intake intake = new Intake();
    private final Floor floor = new Floor();
    private final Feeder feeder = new Feeder();
    private final Shooter shooter = new Shooter();
    private final Hood hood = new Hood();
    private final Limelight limelight = new Limelight("limelight");

    private final SwerveTelemetry swerveTelemetry = new SwerveTelemetry(Driving.kMaxSpeed.in(MetersPerSecond));

    // Force field system
    private final ForceFieldEngine forceFieldEngine = new ForceFieldEngine(
            ForceFieldMap.loadFromDeploy(ForceField.kDefaultPreset),
            ForceField.kCornerOffsets);
    private final ForceFieldConfig forceFieldConfig = new ForceFieldConfig(forceFieldEngine, ForceField.kDefaultPreset);

    private final CommandXboxController driver = new CommandXboxController(0);

    private final AutoRoutines autoRoutines = new AutoRoutines(
            swerve,
            intake,
            floor,
            feeder,
            shooter,
            hood,
            limelight);
    private final SubsystemCommands subsystemCommands = new SubsystemCommands(
            swerve,
            intake,
            floor,
            feeder,
            shooter,
            hood,
            () -> -driver.getLeftY(),
            () -> -driver.getLeftX());
    private final ManualDriveCommand manualDriveCommand = new ManualDriveCommand(
            swerve,
            // Left stick translation scaled for gentler control.
            () -> -driver.getLeftY() * Driving.kManualTranslationScale,
            () -> -driver.getLeftX() * Driving.kManualTranslationScale,
            // Right stick X -> rotation, scaled down for gentler control.
            () -> -driver.getRightX() * Driving.kManualRotationScale,
            forceFieldEngine,
            () -> false // toggle is handled via button binding below
    );

    /**
     * The container for the robot. Contains subsystems, OI devices, and commands.
     */
    public RobotContainer() {
        configureBindings();
        autoRoutines.configure();
        swerve.registerTelemetry(swerveTelemetry::telemeterize);
        // Publish initial preset so SmartDashboard shows state before first button press
        SmartDashboard.putString("Shot Preset", PRESET_NAMES[presetIndex[0]]);
        SmartDashboard.putNumber("Preset RPM", SHOT_PRESETS[presetIndex[0]].shooterRPM);
        SmartDashboard.putNumber("Preset Hood", SHOT_PRESETS[presetIndex[0]].hoodPosition);
    }

    /**
     * Called from Robot.robotPeriodic() to update force field config (preset
     * changes, live tuning).
     */
    public void updateForceFieldConfig() {
        forceFieldConfig.update();
    }

    /**
     * Returns the swerve drivetrain for simulation (e.g. updateSimState in
     * simulationPeriodic).
     */
    public Swerve getSwerve() {
        return swerve;
    }

    /**
     * Use this method to define your trigger->command mappings. Triggers can be
     * created via the
     * {@link Trigger#Trigger(java.util.function.BooleanSupplier)} constructor with
     * an arbitrary
     * predicate, or via the named factories in {@link
     * edu.wpi.first.wpilibj2.command.button.CommandGenericHID}'s subclasses for
     * {@link
     * CommandXboxController
     * Xbox}/{@link edu.wpi.first.wpilibj2.command.button.CommandPS4Controller
     * PS4} controllers or
     * {@link edu.wpi.first.wpilibj2.command.button.CommandJoystick Flight
     * joysticks}.
     */
    private void configureBindings() {
        configureManualDriveBindings();
        limelight.setDefaultCommand(updateVisionCommand());
        shooter.setDefaultCommand(shooter.run(shooter::stop));

        // On teleop/auto enable, force intake down (do not auto-stow/homing).
        RobotModeTriggers.autonomous().or(RobotModeTriggers.teleop())
                .and(RobotModeTriggers.test().negate())
                .onTrue(Commands.runOnce(() -> intake.set(Intake.Position.INTAKE)));

        // Right Trigger: Re-seed field-centric heading to current robot orientation.
        // Use after manually aligning robot so ABXY lock headings align correctly.
        driver.rightTrigger().onTrue(Commands.runOnce(() -> manualDriveCommand.seedFieldCentric()));
        // Right Bumper: Spin up shooter to the selected preset RPM, then feed
        driver.rightBumper().whileTrue(subsystemCommands.shootManually(() -> SHOT_PRESETS[presetIndex[0]].shooterRPM));
        // Left Bumper: toggle pivot STOWED <-> INTAKE immediately, even mid-move
        final boolean[] pivotDeployed = { true };
        driver.leftBumper().onTrue(Commands.runOnce(() -> {
            pivotDeployed[0] = !pivotDeployed[0];
            intake.set(pivotDeployed[0] ? Intake.Position.INTAKE : Intake.Position.STOWED);
        }));
        // Left Trigger: run rollers only (pivot stays wherever it is)
        driver.leftTrigger().whileTrue(intake.startEnd(
            () -> intake.set(Intake.Speed.INTAKE),
            () -> intake.set(Intake.Speed.STOP)
        ));

        // Climbers disabled (no hanger controls).
        // D-Pad Left: Reverse floor and shooter to clear a jam
        driver.povLeft().whileTrue(Commands.parallel(floor.reverseCommand(), shooter.reverseCommand()));

        // D-Pad Up/Down: Cycle through shot presets (snaps hood + RPM to known-good combo)
        driver.povUp().onTrue(Commands.runOnce(() -> {
            presetIndex[0] = (presetIndex[0] + 1) % SHOT_PRESETS.length;
            hood.setPosition(SHOT_PRESETS[presetIndex[0]].hoodPosition);
            SmartDashboard.putString("Shot Preset", PRESET_NAMES[presetIndex[0]]);
            SmartDashboard.putNumber("Preset RPM", SHOT_PRESETS[presetIndex[0]].shooterRPM);
            SmartDashboard.putNumber("Preset Hood", SHOT_PRESETS[presetIndex[0]].hoodPosition);
        }));
        driver.povDown().onTrue(Commands.runOnce(() -> {
            presetIndex[0] = (presetIndex[0] - 1 + SHOT_PRESETS.length) % SHOT_PRESETS.length;
            hood.setPosition(SHOT_PRESETS[presetIndex[0]].hoodPosition);
            SmartDashboard.putString("Shot Preset", PRESET_NAMES[presetIndex[0]]);
            SmartDashboard.putNumber("Preset RPM", SHOT_PRESETS[presetIndex[0]].shooterRPM);
            SmartDashboard.putNumber("Preset Hood", SHOT_PRESETS[presetIndex[0]].hoodPosition);
        }));

    }

    private void configureManualDriveBindings() {
        swerve.setDefaultCommand(manualDriveCommand);
        // A: Lock heading toward opponent alliance wall (180°)
        driver.a().onTrue(Commands.runOnce(() -> manualDriveCommand.setLockedHeading(Rotation2d.k180deg)));
        // B: Lock heading to the right (90° clockwise)
        driver.b().onTrue(Commands.runOnce(() -> manualDriveCommand.setLockedHeading(Rotation2d.kCW_90deg)));
        // X: Lock heading to the left (90° counter-clockwise)
        driver.x().onTrue(Commands.runOnce(() -> manualDriveCommand.setLockedHeading(Rotation2d.kCCW_90deg)));
        // Y: Lock heading toward own alliance wall (0°)
        driver.y().onTrue(Commands.runOnce(() -> manualDriveCommand.setLockedHeading(Rotation2d.kZero)));
        // Back (Select): Re-zero field-centric heading to current robot orientation
        driver.back().onTrue(Commands.runOnce(() -> manualDriveCommand.seedFieldCentric()));
        // Right Stick Button: Toggle force field on/off
        driver.rightStick().onTrue(Commands.runOnce(() -> manualDriveCommand.toggleForceField()));
    }

    private Command updateVisionCommand() {
        return limelight.run(() -> {
            final Pose2d currentRobotPose = swerve.getState().Pose;
            final Optional<Limelight.Measurement> measurement = limelight.getMeasurement(currentRobotPose);
            measurement.ifPresent(m -> {
                swerve.addVisionMeasurement(
                        m.poseEstimate.pose,
                        m.poseEstimate.timestampSeconds,
                        m.standardDeviations);
            });
        })
                .ignoringDisable(true);
    }
}
