package com.bionanomics.refinery.forcefield;

import edu.wpi.first.math.geometry.Translation2d;

/**
 * A radial zone that creates a constant inward pull inside an inner radius (snap zone)
 * and a linearly ramping force between the inner and outer radii.
 *
 * <p>Use cases:
 * <ul>
 *   <li>Attractive (positive strength): snap-to positions for climbing, shooting, etc.</li>
 *   <li>Repulsive (negative strength): keep-out zones that push the robot away.</li>
 * </ul>
 */
public class RadialZone implements Charge {
    private final String id;
    private final Translation2d center;
    private final double innerRadius;
    private final double outerRadius;
    private final double strength;

    /**
     * @param id          Unique identifier
     * @param center      Center of the zone in field meters
     * @param innerRadius Radius of the constant-force snap zone (meters)
     * @param outerRadius Radius at which force drops to zero (meters)
     * @param strength    Force magnitude; positive = attractive, negative = repulsive
     */
    public RadialZone(String id, Translation2d center, double innerRadius,
                      double outerRadius, double strength) {
        this.id = id;
        this.center = center;
        this.innerRadius = innerRadius;
        this.outerRadius = Math.max(outerRadius, innerRadius + 0.01);
        this.strength = strength;
    }

    @Override
    public Translation2d evaluate(Translation2d point) {
        Translation2d delta = center.minus(point);
        double r = delta.getNorm();

        if (r > outerRadius) {
            return Translation2d.kZero;
        }

        double magnitude;
        if (r <= innerRadius) {
            magnitude = strength;
        } else {
            double rampFraction = 1.0 - (r - innerRadius) / (outerRadius - innerRadius);
            magnitude = strength * rampFraction;
        }

        if (r < 1e-9) {
            return Translation2d.kZero;
        }

        Translation2d direction = delta.div(r);
        return direction.times(magnitude);
    }

    @Override public String id() { return id; }
    @Override public double strength() { return strength; }
    public Translation2d center() { return center; }
    public double innerRadius() { return innerRadius; }
    public double outerRadius() { return outerRadius; }
}
