package frc.robot.commands;

import com.ctre.phoenix6.swerve.SwerveModule.DriveRequestType;
import com.ctre.phoenix6.swerve.SwerveModule.SteerRequestType;
import com.ctre.phoenix6.swerve.SwerveRequest;

import org.littletonrobotics.junction.Logger;

import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.Constants.Driving;
import frc.robot.Constants.Vision;
import frc.robot.LimelightHelpers;
import frc.robot.LimelightHelpers.RawDetection;
import frc.robot.subsystems.Intake;
import frc.robot.subsystems.Limelight;
import frc.robot.subsystems.Swerve;

/**
 * Drives toward the visible fuel cluster and runs the intake.
 *
 * Switches the Limelight to the Neural Detector pipeline on start, reads all
 * fuel detections each cycle, steers toward the weighted-average horizontal
 * center (weighted by bounding-box area so closer/larger pieces dominate),
 * drives forward at a fixed fraction of max speed, and runs the intake pivot
 * and rollers. Switches the Limelight back to the AprilTag pipeline on end.
 *
 * Bound to: driver Start button (hold).
 */
public class FuelChaseCommand extends Command {
    /** Proportion of max rotational rate per unit of normalised horizontal offset (txnc ∈ [−1, 1]). */
    private static final double kRotationKP = 0.7;

    /** Fraction of max drive speed to use while chasing a target. */
    private static final double kForwardSpeedFraction = 0.3;

    private final Swerve swerve;
    private final Limelight limelight;
    private final Intake intake;

    private final SwerveRequest.RobotCentric robotCentricRequest = new SwerveRequest.RobotCentric()
        .withDriveRequestType(DriveRequestType.OpenLoopVoltage)
        .withSteerRequestType(SteerRequestType.MotionMagicExpo);

    public FuelChaseCommand(Swerve swerve, Limelight limelight, Intake intake) {
        this.swerve = swerve;
        this.limelight = limelight;
        this.intake = intake;
        addRequirements(swerve, intake);
    }

    @Override
    public void initialize() {
        limelight.setPipeline(Vision.PIPELINE_FUEL_DETECTOR);
    }

    @Override
    public void execute() {
        // Deploy intake and run rollers
        intake.set(Intake.Position.INTAKE);
        intake.set(Intake.Speed.INTAKE);

        // Compute weighted-average horizontal offset across all visible fuel detections.
        // txnc is normalised (-1 = far left, +1 = far right). Weight by area (ta) so
        // closer or larger pieces pull the robot toward them more strongly.
        final RawDetection[] detections = LimelightHelpers.getRawDetections(limelight.getName());
        final boolean hasTarget = detections.length > 0;

        double weightedTxncSum = 0.0;
        double weightSum = 0.0;
        for (RawDetection d : detections) {
            double weight = Math.max(d.ta, 0.001);
            weightedTxncSum += d.txnc * weight;
            weightSum += weight;
        }
        final double targetTxnc = hasTarget ? weightedTxncSum / weightSum : 0.0;

        // Drive forward when a target is visible; rotate to centre it (sign: positive txnc → rotate right = negative in robot-centric)
        swerve.setControl(
            robotCentricRequest
                .withVelocityX(Driving.kMaxSpeed.times(hasTarget ? kForwardSpeedFraction : 0.0))
                .withVelocityY(Driving.kMaxSpeed.times(0.0))
                .withRotationalRate(Driving.kMaxRotationalRate.times(-targetTxnc * kRotationKP))
        );

        Logger.recordOutput("FuelChase/DetectionCount", (double) detections.length);
        Logger.recordOutput("FuelChase/TargetTxnc", targetTxnc);
        Logger.recordOutput("FuelChase/HasTarget", hasTarget);
    }

    @Override
    public void end(boolean interrupted) {
        intake.set(Intake.Speed.STOP);
        limelight.setPipeline(Vision.PIPELINE_APRILTAG);
        Logger.recordOutput("FuelChase/HasTarget", false);
        Logger.recordOutput("FuelChase/DetectionCount", 0.0);
        Logger.recordOutput("FuelChase/TargetTxnc", 0.0);
    }

    @Override
    public boolean isFinished() {
        return false;
    }
}
