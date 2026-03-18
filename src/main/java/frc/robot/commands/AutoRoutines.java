// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.commands;

import static frc.robot.generated.ChoreoTraj.OutpostAndDepotTrajectory$0;
import static frc.robot.generated.ChoreoTraj.OutpostAndDepotTrajectory$1;
import static frc.robot.generated.ChoreoTraj.OutpostAndDepotTrajectory$2;
import static frc.robot.generated.ChoreoTraj.OutpostAndDepotTrajectory$3;
import frc.robot.generated.BackUpAndShootTraj;
import frc.robot.generated.ChoreoTraj;

import choreo.auto.AutoChooser;
import choreo.auto.AutoFactory;
import choreo.auto.AutoRoutine;
import choreo.auto.AutoTrajectory;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.button.RobotModeTriggers;
import frc.robot.subsystems.Feeder;
import frc.robot.subsystems.Floor;
import frc.robot.subsystems.Hood;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.Limelight;
import frc.robot.subsystems.Shooter;
import frc.robot.subsystems.Swerve;

public final class AutoRoutines {
    private final Intake intake;
    private final Shooter shooter;
    private final Hood hood;
    private final Limelight limelight;

    private final SubsystemCommands subsystemCommands;

    private final AutoFactory autoFactory;
    private final AutoChooser autoChooser;

    public AutoRoutines(
        Swerve swerve,
        Intake intake,
        Floor floor,
        Feeder feeder,
        Shooter shooter,
        Hood hood,
        Limelight limelight
    ) {
        this.intake = intake;
        this.shooter = shooter;
        this.hood = hood;
        this.limelight = limelight;

        this.subsystemCommands = new SubsystemCommands(swerve, intake, floor, feeder, shooter, hood);

        this.autoFactory = swerve.createAutoFactory();
        this.autoChooser = new AutoChooser();
    }

    public void configure() {
        autoChooser.addRoutine("Shoot Only", this::shootOnlyRoutine);
        autoChooser.addRoutine("Back Up Left and Shoot", this::backUpLeftAndShoot);
        autoChooser.addRoutine("Back Up Right and Shoot", this::backUpRightAndShoot);
        autoChooser.addRoutine("Outpost and Depot", this::outpostAndDepotRoutine);
        SmartDashboard.putData("Auto Chooser", autoChooser);
        RobotModeTriggers.autonomous().whileTrue(autoChooser.selectedCommandScheduler());
    }

    private AutoRoutine shootOnlyRoutine() {
        final AutoRoutine routine = autoFactory.newRoutine("Shoot Only");
        routine.active().onTrue(
            subsystemCommands.aimAndShoot().withTimeout(5)
        );
        return routine;
    }

    private AutoRoutine backUpLeftAndShoot()  { return backUpAndShootFromTrajectory(BackUpAndShootTraj.Left,  "Back Up Left and Shoot"); }
    private AutoRoutine backUpRightAndShoot() { return backUpAndShootFromTrajectory(BackUpAndShootTraj.Right, "Back Up Right and Shoot"); }

    /**
     * Backs up along a Choreo path from the starting zone, then runs aim-and-shoot.
     * Requires BackUpLeftTrajectory.traj / BackUpRightTrajectory.traj in deploy/choreo/ —
     * see BackUpAndShootTraj.java for how to create them in Choreo.
     */
    private AutoRoutine backUpAndShootFromTrajectory(ChoreoTraj trajDef, String name) {
        final AutoRoutine routine = autoFactory.newRoutine(name);
        final AutoTrajectory backUpPath = trajDef.asAutoTraj(routine);
        routine.active().onTrue(
            Commands.sequence(
                backUpPath.resetOdometry(),
                backUpPath.cmd(),
                subsystemCommands.aimAndShoot().withTimeout(5)
            )
        );
        return routine;
    }

    private AutoRoutine outpostAndDepotRoutine() {
        final AutoRoutine routine = autoFactory.newRoutine("Outpost and Depot");
        final AutoTrajectory startToOutpost = OutpostAndDepotTrajectory$0.asAutoTraj(routine);
        final AutoTrajectory outpostToDepot = OutpostAndDepotTrajectory$1.asAutoTraj(routine);
        final AutoTrajectory depotToShootingPose = OutpostAndDepotTrajectory$2.asAutoTraj(routine);
        final AutoTrajectory shootingPoseToTower = OutpostAndDepotTrajectory$3.asAutoTraj(routine);

        routine.active().onTrue(
            Commands.sequence(
                startToOutpost.resetOdometry(),
                startToOutpost.cmd()
            )
        );

        // (climber disabled)

        startToOutpost.doneDelayed(1).onTrue(outpostToDepot.cmd());

        outpostToDepot.atTimeBeforeEnd(1).onTrue(intake.intakeCommand());
        outpostToDepot.doneDelayed(0.1).onTrue(depotToShootingPose.cmd());

        depotToShootingPose.active().whileTrue(limelight.idle());
        depotToShootingPose.atTime(0.5).onTrue(
            Commands.parallel(
                shooter.spinUpCommand(2600),
                hood.positionCommand(0.32)
            )
        );
        depotToShootingPose.done().onTrue(
            Commands.sequence(
                subsystemCommands.aimAndShoot()
                    .withTimeout(5),
                shootingPoseToTower.cmd()
            )
        );

        shootingPoseToTower.active().whileTrue(limelight.idle());

        return routine;
    }
}
