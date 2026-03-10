package com.bionanomics.refinery.forcefield;

import edu.wpi.first.math.geometry.Translation2d;

/**
 * A charge element in a force field map.
 *
 * <p>Each charge can evaluate the force it exerts on a given field point.
 * Positive strength = attractive (pulls toward), negative = repulsive (pushes away).
 *
 * @see PointCharge
 * @see LineCharge
 * @see RadialZone
 */
public interface Charge {
    /**
     * Computes the force vector this charge exerts at the given field position.
     *
     * @param point A point on the field in meters (blue-alliance origin)
     * @return Force vector in arbitrary force units (direction + magnitude)
     */
    Translation2d evaluate(Translation2d point);

    /** Unique identifier for this charge (used in JSON keys and dashboards). */
    String id();

    /** The raw strength value (positive = attractive, negative = repulsive). */
    double strength();
}
