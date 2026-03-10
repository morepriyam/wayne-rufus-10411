package com.bionanomics.refinery.forcefield;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;

/**
 * Evaluates the force field at the four swerve module corners of the robot.
 *
 * <p>By computing forces at each corner independently, the engine naturally produces
 * both translational and rotational effects:
 * <ul>
 *   <li>A wall parallel to the robot creates uniform repulsion (pure translation).</li>
 *   <li>A wall at an angle creates stronger force on the nearer corner, producing a
 *       torque that pivots the robot away.</li>
 *   <li>An attractor off to one side pulls that corner harder, rotating the robot
 *       to face the attractor.</li>
 * </ul>
 *
 * <p>The engine converts raw force vectors into velocity offsets (m/s) and angular velocity
 * offsets (rad/s) that are added to the driver's joystick input. It does <b>not</b> perform
 * any logging — teams should log the returned {@link ForceResult} using their own framework
 * (AdvantageKit, DataLog, SmartDashboard, etc.).
 */
public class ForceFieldEngine {
    /** Module corner offsets from robot center [FL, FR, BL, BR] in robot-local meters. */
    private final Translation2d[] cornerOffsets;

    private ForceFieldMap map;

    /**
     * @param map           The active force field map
     * @param cornerOffsets Module corner positions relative to robot center [FL, FR, BL, BR]
     */
    public ForceFieldEngine(ForceFieldMap map, Translation2d[] cornerOffsets) {
        if (cornerOffsets.length != 4) {
            throw new IllegalArgumentException(
                "Expected 4 corner offsets, got " + cornerOffsets.length);
        }
        this.map = map;
        this.cornerOffsets = cornerOffsets;
    }

    /** Replaces the active force field map (e.g., when switching presets). */
    public void setMap(ForceFieldMap map) {
        this.map = map;
    }

    public ForceFieldMap getMap() {
        return map;
    }

    /**
     * Computes the force field effect on the robot at its current pose.
     *
     * @param robotPose Current robot pose in field coordinates (blue-alliance origin)
     * @return The velocity and angular velocity offsets to apply, plus per-corner forces
     */
    public ForceResult compute(Pose2d robotPose) {
        Rotation2d heading = robotPose.getRotation();
        Translation2d robotCenter = robotPose.getTranslation();

        Translation2d[] cornerForces = new Translation2d[4];
        double netFx = 0, netFy = 0;
        double netTorque = 0;

        for (int i = 0; i < 4; i++) {
            // Transform corner offset from robot-local to field coordinates
            Translation2d cornerField =
                robotCenter.plus(cornerOffsets[i].rotateBy(heading));

            // Evaluate the net force at this corner (in field frame)
            Translation2d force = map.getForceAt(cornerField);
            cornerForces[i] = force;

            // Accumulate net translational force
            netFx += force.getX();
            netFy += force.getY();

            // Compute torque: τ = r × F (2D cross product, robot frame)
            Translation2d forceRobotFrame = force.rotateBy(heading.unaryMinus());
            Translation2d r = cornerOffsets[i];
            double torque = r.getX() * forceRobotFrame.getY()
                          - r.getY() * forceRobotFrame.getX();
            netTorque += torque;
        }

        // Average across 4 corners
        netFx /= 4.0;
        netFy /= 4.0;
        netTorque /= 4.0;

        // Apply gains and clamp
        double vx = MathUtil.clamp(
            netFx * map.forceGain(),
            -map.maxForceVelocity(), map.maxForceVelocity());
        double vy = MathUtil.clamp(
            netFy * map.forceGain(),
            -map.maxForceVelocity(), map.maxForceVelocity());

        Translation2d velocityOffset = new Translation2d(vx, vy);
        if (velocityOffset.getNorm() > map.maxForceVelocity()) {
            velocityOffset = velocityOffset.div(velocityOffset.getNorm())
                .times(map.maxForceVelocity());
        }

        double angularOffset = MathUtil.clamp(
            netTorque * map.torqueGain(),
            -map.maxForceTorque(), map.maxForceTorque());

        return new ForceResult(velocityOffset, angularOffset, cornerForces);
    }
}
