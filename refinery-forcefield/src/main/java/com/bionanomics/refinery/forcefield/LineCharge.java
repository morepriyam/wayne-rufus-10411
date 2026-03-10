package com.bionanomics.refinery.forcefield;

import edu.wpi.first.math.geometry.Translation2d;

/**
 * A line charge that creates force perpendicular to a wall or barrier segment.
 *
 * <p>The force is computed by projecting the query point onto the line segment and applying
 * a force perpendicular to the segment. The force falls off linearly from full strength at
 * the segment surface to zero at {@code falloffDistance} meters away.
 *
 * <p>Positive strength = attractive (pulls toward the line), negative = repulsive (pushes away).
 * Walls should use negative strength to repel.
 */
public class LineCharge implements Charge {
    private static final double kMinDistance = 0.02;

    private final String id;
    private final Translation2d start;
    private final Translation2d end;
    private final double strength;
    private final double falloffDistance;

    /**
     * @param id              Unique identifier
     * @param start           Start point of the line segment in field meters
     * @param end             End point of the line segment in field meters
     * @param strength        Force magnitude; positive = attractive, negative = repulsive
     * @param falloffDistance Distance in meters at which force reaches zero
     */
    public LineCharge(String id, Translation2d start, Translation2d end,
                      double strength, double falloffDistance) {
        this.id = id;
        this.start = start;
        this.end = end;
        this.strength = strength;
        this.falloffDistance = Math.max(falloffDistance, kMinDistance);
    }

    @Override
    public Translation2d evaluate(Translation2d point) {
        Translation2d segmentVec = end.minus(start);
        double segmentLengthSquared = segmentVec.getNorm() * segmentVec.getNorm();

        // Degenerate segment (zero length) — treat as a point
        if (segmentLengthSquared < 1e-9) {
            Translation2d delta = start.minus(point);
            double r = delta.getNorm();
            if (r < kMinDistance) r = kMinDistance;
            double magnitude = strength * Math.max(0, 1.0 - r / falloffDistance);
            if (r < 1e-9) return Translation2d.kZero;
            return delta.div(delta.getNorm()).times(magnitude);
        }

        // Parameter t along the segment: t=0 at start, t=1 at end
        Translation2d startToPoint = point.minus(start);
        double t = (startToPoint.getX() * segmentVec.getX()
                  + startToPoint.getY() * segmentVec.getY()) / segmentLengthSquared;
        t = Math.max(0, Math.min(1, t));

        // Closest point on segment
        Translation2d closest = start.plus(segmentVec.times(t));

        // Vector from point toward the closest point on the segment
        Translation2d delta = closest.minus(point);
        double r = delta.getNorm();

        if (r > falloffDistance) {
            return Translation2d.kZero;
        }
        if (r < kMinDistance) {
            r = kMinDistance;
        }

        double magnitude = strength * (1.0 - r / falloffDistance);

        if (delta.getNorm() < 1e-9) {
            return Translation2d.kZero;
        }

        Translation2d direction = delta.div(delta.getNorm());
        return direction.times(magnitude);
    }

    @Override public String id() { return id; }
    @Override public double strength() { return strength; }
    public Translation2d start() { return start; }
    public Translation2d end() { return end; }
    public double falloffDistance() { return falloffDistance; }
}
