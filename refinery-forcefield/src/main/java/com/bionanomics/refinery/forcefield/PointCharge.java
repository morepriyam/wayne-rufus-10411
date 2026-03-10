package com.bionanomics.refinery.forcefield;

import edu.wpi.first.math.geometry.Translation2d;

/**
 * A point charge that exerts force radially toward (attractive) or away from (repulsive)
 * its position.
 *
 * <p>Positive strength attracts, negative repels. The force magnitude depends on the
 * chosen {@link FalloffType}:
 * <ul>
 *   <li>{@code INVERSE_SQUARE}: |F| = strength / r²</li>
 *   <li>{@code INVERSE_LINEAR}: |F| = strength / r</li>
 *   <li>{@code GAUSSIAN}: |F| = strength × exp(−r² / 2σ²)</li>
 * </ul>
 *
 * <p>A minimum distance clamp prevents singularities at r→0 for inverse falloff types.
 */
public class PointCharge implements Charge {
    /** Minimum distance (meters) to prevent divide-by-zero singularities. */
    private static final double kMinDistance = 0.05;

    private final String id;
    private final Translation2d position;
    private final double strength;
    private final FalloffType falloff;
    private final double sigma;

    /**
     * @param id       Unique identifier
     * @param position Field position in meters (blue-alliance origin)
     * @param strength Force magnitude; positive = attractive, negative = repulsive
     * @param falloff  How force decays with distance
     * @param sigma    Gaussian width in meters (ignored for non-Gaussian falloff)
     */
    public PointCharge(String id, Translation2d position, double strength,
                       FalloffType falloff, double sigma) {
        this.id = id;
        this.position = position;
        this.strength = strength;
        this.falloff = falloff;
        this.sigma = sigma;
    }

    public PointCharge(String id, Translation2d position, double strength, FalloffType falloff) {
        this(id, position, strength, falloff, 0.5);
    }

    @Override
    public Translation2d evaluate(Translation2d point) {
        Translation2d delta = position.minus(point);
        double r = delta.getNorm();

        if (r < kMinDistance && falloff != FalloffType.GAUSSIAN) {
            r = kMinDistance;
        }

        double magnitude = switch (falloff) {
            case INVERSE_SQUARE -> strength / (r * r);
            case INVERSE_LINEAR -> strength / r;
            case GAUSSIAN -> strength * Math.exp(-(r * r) / (2.0 * sigma * sigma));
        };

        if (r < 1e-9) {
            return Translation2d.kZero;
        }

        Translation2d direction = delta.div(delta.getNorm());
        return direction.times(magnitude);
    }

    @Override public String id() { return id; }
    @Override public double strength() { return strength; }
    public Translation2d position() { return position; }
    public FalloffType falloff() { return falloff; }
    public double sigma() { return sigma; }
}
