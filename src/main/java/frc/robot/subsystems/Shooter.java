package frc.robot.subsystems;

import static edu.wpi.first.units.Units.Amps;
import static edu.wpi.first.units.Units.RPM;
import static edu.wpi.first.units.Units.RotationsPerSecond;
import static edu.wpi.first.units.Units.Volts;

import java.util.List;

import com.ctre.phoenix6.configs.CurrentLimitsConfigs;
import com.ctre.phoenix6.configs.MotorOutputConfigs;
import com.ctre.phoenix6.configs.Slot0Configs;
import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.configs.VoltageConfigs;
import com.ctre.phoenix6.controls.VelocityVoltage;
import com.ctre.phoenix6.controls.VoltageOut;
import com.ctre.phoenix6.hardware.TalonFX;
import com.ctre.phoenix6.signals.InvertedValue;
import com.ctre.phoenix6.signals.NeutralModeValue;

import org.littletonrobotics.junction.Logger;

import edu.wpi.first.units.measure.AngularVelocity;
import edu.wpi.first.util.sendable.SendableBuilder;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants.KrakenX60;
import frc.robot.Ports;

public class Shooter extends SubsystemBase {
    private static final AngularVelocity kVelocityTolerance = RPM.of(100);
    // Minimum RPM before the floor/feeder feed sequence starts
    private static final double kFeedThresholdRPM = 3500;

    private final TalonFX leftMotor, middleMotor, rightMotor;
    private final List<TalonFX> motors;
    private final VelocityVoltage velocityRequest = new VelocityVoltage(0).withSlot(0);
    private final VoltageOut voltageRequest = new VoltageOut(0);

    private double dashboardTargetRPM = 3750.0;

    public Shooter() {
        leftMotor = new TalonFX(Ports.kShooterLeft, Ports.kRoboRioCANBus);
        middleMotor = new TalonFX(Ports.kShooterMiddle, Ports.kRoboRioCANBus);
        rightMotor = new TalonFX(Ports.kShooterRight, Ports.kRoboRioCANBus);
        motors = List.of(leftMotor, middleMotor, rightMotor);

        configureMotor(leftMotor, InvertedValue.CounterClockwise_Positive);
        configureMotor(middleMotor, InvertedValue.CounterClockwise_Positive);
        configureMotor(rightMotor, InvertedValue.Clockwise_Positive);

        SmartDashboard.putData(this);
    }

    private void configureMotor(TalonFX motor, InvertedValue invertDirection) {
        final TalonFXConfiguration config = new TalonFXConfiguration()
            .withMotorOutput(
                new MotorOutputConfigs()
                    .withInverted(invertDirection)
                    .withNeutralMode(NeutralModeValue.Coast)
            )
            .withVoltage(
                new VoltageConfigs()
                    .withPeakReverseVoltage(Volts.of(-12))
            )
            .withCurrentLimits(
                new CurrentLimitsConfigs()
                    .withStatorCurrentLimit(Amps.of(120))
                    .withStatorCurrentLimitEnable(true)
                    .withSupplyCurrentLimit(Amps.of(70))
                    .withSupplyCurrentLimitEnable(true)
            )
            .withSlot0(
                new Slot0Configs()
                    .withKP(0.5)
                    .withKI(0)
                    .withKD(0)
                    .withKV(12.0 / KrakenX60.kFreeSpeed.in(RotationsPerSecond)) // 12 volts when requesting max RPS
            );
        
        motor.getConfigurator().apply(config);
    }

    public void setRPM(double rpm) {
        for (final TalonFX motor : motors) {
            motor.setControl(
                velocityRequest
                    .withVelocity(RPM.of(rpm))
            );
        }
    }

    public void setPercentOutput(double percentOutput) {
        for (final TalonFX motor : motors) {
            motor.setControl(
                voltageRequest
                    .withOutput(Volts.of(percentOutput * 12.0))
            );
        }
    }

    public void stop() {
        setPercentOutput(0.0);
    }

    public Command reverseCommand() {
        return startEnd(() -> setPercentOutput(-0.2), this::stop);
    }

    private static final double kSpinUpOvershootFactor = 1.15;

    public double getDashboardTargetRPM() {
        return dashboardTargetRPM;
    }

    public Command spinUpCommand(double rpm) {
        // Overshoot by 15% to ensure we reach target against bus voltage sag
        // Hold shooter subsystem throughout so the default stop command doesn't interrupt
        return run(() -> setRPM(rpm * kSpinUpOvershootFactor))
            .until(() -> isNearRPM(rpm));
    }

    public Command dashboardSpinUpCommand() {
        return defer(() -> spinUpCommand(dashboardTargetRPM)); 
    }

    public boolean isVelocityWithinTolerance() {
        final AngularVelocity targetVelocity = velocityRequest.getVelocityMeasure();
        return motors.stream().allMatch(motor -> 
            motor.getVelocity().getValue().isNear(targetVelocity, kVelocityTolerance)
        );
    }

    public boolean isAboveFeedThreshold() {
        return motors.stream().allMatch(motor ->
            motor.getVelocity().getValue().gte(RPM.of(kFeedThresholdRPM))
        );
    }

    public boolean isNearRPM(double rpm) {
        final AngularVelocity target = RPM.of(rpm);
        return motors.stream().allMatch(motor ->
            motor.getVelocity().getValue().isNear(target, kVelocityTolerance)
        );
    }

    private void initSendable(SendableBuilder builder, TalonFX motor, String name) {
        builder.addDoubleProperty(name + " RPM", () -> motor.getVelocity().getValue().in(RPM), null);
        builder.addDoubleProperty(name + " Stator Current", () -> motor.getStatorCurrent().getValue().in(Amps), null);
        builder.addDoubleProperty(name + " Supply Current", () -> motor.getSupplyCurrent().getValue().in(Amps), null);
        builder.addDoubleProperty(name + " Supply Voltage", () -> motor.getSupplyVoltage().getValue().in(Volts), null);
    }

    @Override
    public void periodic() {
        Logger.recordOutput("Shooter/ActiveCommand", getCurrentCommand() != null ? getCurrentCommand().getName() : "none");
        Logger.recordOutput("Shooter/Left/RPM", leftMotor.getVelocity().getValue().in(RPM));
        Logger.recordOutput("Shooter/Middle/RPM", middleMotor.getVelocity().getValue().in(RPM));
        Logger.recordOutput("Shooter/Right/RPM", rightMotor.getVelocity().getValue().in(RPM));
        Logger.recordOutput("Shooter/Left/MotorVoltage", leftMotor.getMotorVoltage().getValue().in(Volts));
        Logger.recordOutput("Shooter/Middle/MotorVoltage", middleMotor.getMotorVoltage().getValue().in(Volts));
        Logger.recordOutput("Shooter/Right/MotorVoltage", rightMotor.getMotorVoltage().getValue().in(Volts));
        Logger.recordOutput("Shooter/Left/SupplyVoltage", leftMotor.getSupplyVoltage().getValue().in(Volts));
        Logger.recordOutput("Shooter/Middle/SupplyVoltage", middleMotor.getSupplyVoltage().getValue().in(Volts));
        Logger.recordOutput("Shooter/Right/SupplyVoltage", rightMotor.getSupplyVoltage().getValue().in(Volts));
        Logger.recordOutput("Shooter/Left/StatorCurrent", leftMotor.getStatorCurrent().getValue().in(Amps));
        Logger.recordOutput("Shooter/Middle/StatorCurrent", middleMotor.getStatorCurrent().getValue().in(Amps));
        Logger.recordOutput("Shooter/Right/StatorCurrent", rightMotor.getStatorCurrent().getValue().in(Amps));
        Logger.recordOutput("Shooter/Left/SupplyCurrent", leftMotor.getSupplyCurrent().getValue().in(Amps));
        Logger.recordOutput("Shooter/Middle/SupplyCurrent", middleMotor.getSupplyCurrent().getValue().in(Amps));
        Logger.recordOutput("Shooter/Right/SupplyCurrent", rightMotor.getSupplyCurrent().getValue().in(Amps));
        Logger.recordOutput("Shooter/TargetRPM", velocityRequest.getVelocityMeasure().in(RPM));
        Logger.recordOutput("Shooter/ReadyToShoot", isVelocityWithinTolerance());
        Logger.recordOutput("Shooter/AboveFeedThreshold", isAboveFeedThreshold());
    }

    @Override
    public void initSendable(SendableBuilder builder) {
        initSendable(builder, leftMotor, "Left");
        initSendable(builder, middleMotor, "Middle");
        initSendable(builder, rightMotor, "Right");
        builder.addStringProperty("Command", () -> getCurrentCommand() != null ? getCurrentCommand().getName() : "null", null);
        builder.addDoubleProperty("Dashboard RPM", () -> dashboardTargetRPM, value -> dashboardTargetRPM = value);
        builder.addDoubleProperty("Target RPM", () -> velocityRequest.getVelocityMeasure().in(RPM), null);
        builder.addBooleanProperty("Ready to Shoot", this::isVelocityWithinTolerance, null);
    }
}
