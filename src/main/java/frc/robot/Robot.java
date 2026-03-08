// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import static edu.wpi.first.units.Units.Volts;

import org.littletonrobotics.junction.LoggedRobot;
import org.littletonrobotics.junction.Logger;
import org.littletonrobotics.junction.wpilog.WPILOGWriter;
import org.littletonrobotics.junction.networktables.NT4Publisher;

import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.CommandScheduler;

/**
 * The methods in this class are called automatically corresponding to each mode, as described in
 * the TimedRobot documentation. If you change the name of this class or the package after creating
 * this project, you must also update the Main.java file in the project.
 */
public class Robot extends LoggedRobot {
    private final RobotContainer m_robotContainer;
    
    /**
     * This function is run when the robot is first started up and should be used for any
     * initialization code.
     */
    public Robot() {
        // Instantiate our RobotContainer.  This will perform all our button bindings, and put our
        // autonomous chooser on the dashboard.
        m_robotContainer = new RobotContainer();
        SmartDashboard.putData(CommandScheduler.getInstance());
        RobotController.setBrownoutVoltage(Volts.of(6.1));
        // AdvantageKit logging: write to USB stick + publish to NetworkTables
        Logger.recordMetadata("ProjectName", "rufus10411");
        Logger.addDataReceiver(new WPILOGWriter("/home/lvuser/logs")); // Retrieve via SFTP: sftp lvuser@10.104.11.2:/home/lvuser/logs
        Logger.addDataReceiver(new NT4Publisher());  // Live data in AdvantageScope
        Logger.start();
    }
    
    /**
     * This function is called every 20 ms, no matter the mode. Use this for items like diagnostics
     * that you want ran during disabled, autonomous, teleoperated and test.
     *
     * <p>This runs after the mode specific periodic functions, but before LiveWindow and
     * SmartDashboard integrated updating.
     */
    @Override
    public void robotPeriodic() {
        // Runs the Scheduler.  This is responsible for polling buttons, adding newly-scheduled
        // commands, running already-scheduled commands, removing finished or interrupted commands,
        // and running subsystem periodic() methods.  This must be called from the robot's periodic
        // block in order for anything in the Command-based framework to work.
        CommandScheduler.getInstance().run();
        Logger.recordOutput("Robot/BatteryVoltage", RobotController.getBatteryVoltage());
        Logger.recordOutput("Robot/MatchTimeSec", DriverStation.getMatchTime());
    }
}
