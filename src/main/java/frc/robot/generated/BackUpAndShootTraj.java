// spotless:off
package frc.robot.generated;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import java.util.OptionalInt;

/**
 * Choreo trajectory definitions for the "Back Up and Shoot" auto routines.
 *
 * Both paths start from the same positions as Shoot-and-Climb Left/Right, back up at an angle,
 * and end with the robot's shooter (rear) already roughly facing the hub so aim-and-shoot
 * converges quickly.
 *
 * *** THESE TRAJECTORIES DO NOT EXIST YET IN deploy/choreo/ ***
 * To create them:
 *   1. Open Choreo → New Trajectory
 *   2. Name it exactly "BackUpLeftTrajectory" or "BackUpRightTrajectory"
 *   3. Set start waypoint to kLeftStart / kRightStart below
 *   4. Set end waypoint to kLeftEnd / kRightEnd below (heading already computed here)
 *   5. Generate → Save → the .traj file will appear in src/main/deploy/choreo/
 *   6. Deploy — the routine will then work at runtime.
 *
 * Until the .traj files exist, selecting either routine will show a Choreo alert error
 * and the routine will not run.
 */
public final class BackUpAndShootTraj {

    /** Blue hub position (m) used to compute end-waypoint headings. */
    private static final double kHubX = 182.105 / 39.37;  // ~4.625 m
    private static final double kHubY = 158.845 / 39.37;  // ~4.036 m

    /** Left zone: same start as Shoot-and-Climb — Left. */
    private static final Pose2d kLeftStart = new Pose2d(3.598, 7.432, Rotation2d.fromRadians(Math.PI));
    /** Left end: backed up and tilted so the shooter (back) faces the hub. */
    private static final Pose2d kLeftEnd = new Pose2d(
        2.1, 6.8,
        Rotation2d.fromRadians(Math.atan2(kHubY - 6.8, kHubX - 2.1) + Math.PI)
    );

    /** Right zone: same start as Shoot-and-Climb — Right. */
    private static final Pose2d kRightStart = new Pose2d(3.598, 0.640, Rotation2d.fromRadians(Math.PI));
    /** Right end: backed up and tilted so the shooter (back) faces the hub. */
    private static final Pose2d kRightEnd = new Pose2d(
        2.1, 1.2,
        Rotation2d.fromRadians(Math.atan2(kHubY - 1.2, kHubX - 2.1) + Math.PI)
    );

    private BackUpAndShootTraj() {}

    public static final ChoreoTraj Left = new ChoreoTraj(
        "BackUpLeftTrajectory",
        OptionalInt.empty(),
        1.5,
        kLeftStart,
        kLeftEnd
    );

    public static final ChoreoTraj Right = new ChoreoTraj(
        "BackUpRightTrajectory",
        OptionalInt.empty(),
        1.5,
        kRightStart,
        kRightEnd
    );
}
// spotless:on
