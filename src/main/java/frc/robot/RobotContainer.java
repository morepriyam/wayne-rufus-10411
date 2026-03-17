// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import static edu.wpi.first.units.Units.MetersPerSecond;

import java.util.Optional;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.button.RobotModeTriggers;
import edu.wpi.first.wpilibj2.command.button.Trigger;
import frc.robot.Constants.Driving;
import frc.robot.Constants.ForceField;
import frc.robot.commands.AutoRoutines;
import frc.robot.commands.FuelChaseCommand;
import frc.robot.commands.ManualDriveCommand;
import frc.robot.commands.SubsystemCommands;
import com.bionanomics.refinery.forcefield.ForceFieldConfig;
import com.bionanomics.refinery.forcefield.ForceFieldEngine;
import com.bionanomics.refinery.forcefield.ForceFieldMap;
import frc.robot.subsystems.Feeder;
import frc.robot.subsystems.Floor;
import frc.robot.subsystems.Hanger;
import frc.robot.subsystems.Hood;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.Limelight;
import frc.robot.subsystems.Shooter;
import frc.robot.subsystems.Swerve;
import frc.util.SwerveTelemetry;

/**
 * This class is where the bulk of the robot should be declared. Since Command-based is a
 * "declarative" paradigm, very little robot logic should actually be handled in the {@link Robot}
 * periodic methods (other than the scheduler calls). Instead, the structure of the robot (including
 * subsystems, commands, and trigger mappings) should be declared here.
 */
public class RobotContainer {
    private final Swerve swerve = new Swerve();
    private final Intake intake = new Intake();
    private final Floor floor = new Floor();
    private final Feeder feeder = new Feeder();
    private final Shooter shooter = new Shooter();
    private final Hood hood = new Hood();
    private final Hanger hanger = new Hanger();
    private final Limelight limelight = new Limelight("limelight");

    private final SwerveTelemetry swerveTelemetry = new SwerveTelemetry(Driving.kMaxSpeed.in(MetersPerSecond));

    // Force field system
    private final ForceFieldEngine forceFieldEngine = new ForceFieldEngine(
        ForceFieldMap.loadFromDeploy(ForceField.kDefaultPreset),
        ForceField.kCornerOffsets
    );
    private final ForceFieldConfig forceFieldConfig = new ForceFieldConfig(forceFieldEngine, ForceField.kDefaultPreset);
    
    private final CommandXboxController driver = new CommandXboxController(0);

    private final AutoRoutines autoRoutines = new AutoRoutines(
        swerve,
        intake,
        floor,
        feeder,
        shooter,
        hood,
        hanger,
        limelight
    );
    private final SubsystemCommands subsystemCommands = new SubsystemCommands(
        swerve,
        intake,
        floor,
        feeder,
        shooter,
        hood,
        hanger,
        () -> -driver.getLeftY(),
        () -> -driver.getLeftX()
    );
    
    /** The container for the robot. Contains subsystems, OI devices, and commands. */
    public RobotContainer() {
        configureBindings();
        autoRoutines.configure();
        swerve.registerTelemetry(swerveTelemetry::telemeterize);
    }

    /** Called from Robot.robotPeriodic() to update force field config (preset changes, live tuning). */
    public void updateForceFieldConfig() {
        forceFieldConfig.update();
    }
    
    /**
     * Use this method to define your trigger->command mappings. Triggers can be created via the
     * {@link Trigger#Trigger(java.util.function.BooleanSupplier)} constructor with an arbitrary
     * predicate, or via the named factories in {@link
     * edu.wpi.first.wpilibj2.command.button.CommandGenericHID}'s subclasses for {@link
     * CommandXboxController Xbox}/{@link edu.wpi.first.wpilibj2.command.button.CommandPS4Controller
     * PS4} controllers or {@link edu.wpi.first.wpilibj2.command.button.CommandJoystick Flight
     * joysticks}.
     */
    private void configureBindings() {
        configureManualDriveBindings();
        limelight.setDefaultCommand(updateVisionCommand());
        shooter.setDefaultCommand(shooter.run(shooter::stop));

        // Power-up homing: on first teleop/auto enable (suppressed in test mode), intake pivot and
        // hanger both run their homing sequences automatically and in parallel.
        // See README.md ## Power-Up Initialization for the full sequence description.
        RobotModeTriggers.autonomous().or(RobotModeTriggers.teleop())
            .and(RobotModeTriggers.test().negate())
            .onTrue(intake.homingCommand())
            .onTrue(hanger.homingCommand());

        // Right Trigger: Aim at hub using limelight + drive, then spin up shooter and feed when ready
        driver.rightTrigger().whileTrue(subsystemCommands.aimAndShoot());
        // Right Bumper: Spin up shooter to dashboard-configured RPM, then feed
        driver.rightBumper().whileTrue(subsystemCommands.shootManually());
        // Left Trigger: Deploy intake and run rollers to pick up a note
        driver.leftTrigger().whileTrue(intake.intakeCommand());
        // Left Bumper: Stow the intake pivot
        driver.leftBumper().onTrue(intake.runOnce(() -> intake.set(Intake.Position.STOWED)));

        // D-Pad Up: Extend climber while held; release to stop
        driver.povUp().whileTrue(hanger.extendCommand());
        // D-Pad Down: Retract climber while held; release to stop
        driver.povDown().whileTrue(hanger.retractCommand());
        // D-Pad Left: Reverse floor and shooter to clear a jam
        driver.povLeft().whileTrue(Commands.parallel(floor.reverseCommand(), shooter.reverseCommand()));

        // Start: Chase visible fuel using Limelight Neural Detector + run intake
        driver.start().whileTrue(new FuelChaseCommand(swerve, limelight, intake));
    }

    private void configureManualDriveBindings() {
        final ManualDriveCommand manualDriveCommand = new ManualDriveCommand(
            swerve, 
            () -> -driver.getLeftY(), 
            () -> -driver.getLeftX(), 
            () -> -driver.getRightX(),
            forceFieldEngine,
            () -> false // toggle is handled via button binding below
        );
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
                    m.standardDeviations
                );
            });
        })
        .ignoringDisable(true);
    }
}
