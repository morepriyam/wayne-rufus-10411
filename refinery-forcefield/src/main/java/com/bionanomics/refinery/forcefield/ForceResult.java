package com.bionanomics.refinery.forcefield;

import edu.wpi.first.math.geometry.Translation2d;

/**
 * Result of evaluating the force field at the robot's current pose.
 *
 * @param velocityOffset        Net translational velocity offset (m/s, field-frame)
 * @param angularVelocityOffset Net angular velocity offset (rad/s, CCW-positive)
 * @param cornerForces          Force vectors at each swerve module corner [FL, FR, BL, BR]
 */
public record ForceResult(
    Translation2d velocityOffset,
    double angularVelocityOffset,
    Translation2d[] cornerForces
) {
    /** A zero result with no velocity or angular offsets. */
    public static final ForceResult ZERO = new ForceResult(
        Translation2d.kZero,
        0.0,
        new Translation2d[] {
            Translation2d.kZero, Translation2d.kZero,
            Translation2d.kZero, Translation2d.kZero
        }
    );
}
