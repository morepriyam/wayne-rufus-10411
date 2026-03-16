package frc.robot.commands;

import static edu.wpi.first.units.Units.MetersPerSecond;

import com.ctre.phoenix6.swerve.SwerveModule.DriveRequestType;
import com.ctre.phoenix6.swerve.SwerveModule.SteerRequestType;
import com.ctre.phoenix6.swerve.SwerveRequest;
import com.ctre.phoenix6.swerve.SwerveRequest.ForwardPerspectiveValue;

import edu.wpi.first.units.measure.LinearVelocity;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.robot.Constants.Driving;
import frc.robot.subsystems.Swerve;

/**
 * Builds commands that drive the robot backward and left (or backward and right) in field-centric
 * for a fixed duration, then run aim-and-shoot.
 *
 * Used by the "Back Up Left and Shoot" and "Back Up Right and Shoot" auto routines.
 */
public final class BackUpAndShootCommand {

    /** Field-centric speed for the backup (m/s). Back is negative X; left is positive Y. */
    private static final double kBackUpSpeedMps = 1.5;
    /** How long to drive before stopping and aiming. */
    private static final double kBackUpDurationSeconds = 1.5;
    /** Timeout for the aim-and-shoot phase. */
    private static final double kAimAndShootTimeoutSeconds = 5.0;

    public enum Direction {
        /** Back and to the left (field +Y). */
        LEFT(1.0),
        /** Back and to the right (field -Y). */
        RIGHT(-1.0);

        final double ySign;

        Direction(double ySign) {
            this.ySign = ySign;
        }
    }

    private BackUpAndShootCommand() {}

    private static final SwerveRequest.FieldCentric fieldCentricRequest = new SwerveRequest.FieldCentric()
        .withDriveRequestType(DriveRequestType.OpenLoopVoltage)
        .withSteerRequestType(SteerRequestType.MotionMagicExpo)
        .withForwardPerspective(ForwardPerspectiveValue.OperatorPerspective);

    /**
     * Returns a command that drives back-left or back-right for a fixed time, then runs the given
     * aim-and-shoot command.
     */
    public static Command sequence(Swerve swerve, Command aimAndShootCommand, Direction direction) {
        final LinearVelocity vx = MetersPerSecond.of(-kBackUpSpeedMps);
        final LinearVelocity vy = MetersPerSecond.of(direction.ySign * kBackUpSpeedMps);
        final Command backUp = swerve.applyRequest(() ->
            fieldCentricRequest
                .withVelocityX(vx)
                .withVelocityY(vy)
                .withRotationalRate(Driving.kMaxRotationalRate.times(0))
        ).withTimeout(kBackUpDurationSeconds);

        return Commands.sequence(
            backUp,
            aimAndShootCommand.withTimeout(kAimAndShootTimeoutSeconds)
        );
    }
}
