// spotless:off
package frc.robot.generated;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import java.util.OptionalInt;

/**
 * Choreo trajectory definitions for "Back Up Left and Shoot" and "Back Up Right and Shoot".
 *
 * Start from the left or right zone (same poses as Shoot and Climb Left/Right). Paths back up
 * at an angle and end with the robot rotated toward the hub. Create these trajectories in Choreo
 * with the same start/end positions and end headings (tilt toward speaker).
 *
 * Choreo: New Trajectory, name "BackUpLeftTrajectory" or "BackUpRightTrajectory". Use the start
 * poses below (left zone / right zone). Set end waypoint with heading that points the shooter
 * at the hub. Generate, Save. Export to src/main/deploy/choreo/.
 */
@SuppressWarnings("unused")
public final class BackUpAndShootTraj {

    /** Blue hub position (m) for computing end heading. */
    private static final double kHubX = 182.105 / 39.37;
    private static final double kHubY = 158.845 / 39.37;

    /** Start from left zone (same as Shoot and Climb — Left). */
    private static final Pose2d kLeftStart = new Pose2d(3.598, 7.432, Rotation2d.fromRadians(Math.PI));
    /** End: back from left; heading so shooter (back) faces hub. */
    private static final Pose2d kLeftEnd = new Pose2d(
        2.1, 6.8,
        Rotation2d.fromRadians(
            Math.atan2(kHubY - 6.8, kHubX - 2.1) + Math.PI
        )
    );

    /** Start from right zone (same as Shoot and Climb — Right). */
    private static final Pose2d kRightStart = new Pose2d(3.598, 0.640, Rotation2d.fromRadians(Math.PI));
    /** End: back from right; heading so shooter (back) faces hub. */
    private static final Pose2d kRightEnd = new Pose2d(
        2.1, 1.2,
        Rotation2d.fromRadians(
            Math.atan2(kHubY - 1.2, kHubX - 2.1) + Math.PI
        )
    );

    private BackUpAndShootTraj() {}

    /** Back up from left zone; end pose tilted so shooter faces the hub. */
    public static final ChoreoTraj Left = new ChoreoTraj(
        "BackUpLeftTrajectory",
        OptionalInt.empty(),
        1.5,
        kLeftStart,
        kLeftEnd
    );

    /** Back up from right zone; end pose tilted so shooter faces the hub. */
    public static final ChoreoTraj Right = new ChoreoTraj(
        "BackUpRightTrajectory",
        OptionalInt.empty(),
        1.5,
        kRightStart,
        kRightEnd
    );
}
// spotless:on
