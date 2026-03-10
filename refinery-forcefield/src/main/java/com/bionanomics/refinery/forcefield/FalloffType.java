package com.bionanomics.refinery.forcefield;

/**
 * Determines how a charge's force magnitude decreases with distance.
 */
public enum FalloffType {
    /** Force ∝ 1/r² — strong near the charge, drops off quickly. */
    INVERSE_SQUARE,

    /** Force ∝ 1/r — moderate falloff. */
    INVERSE_LINEAR,

    /**
     * Force ∝ exp(−r²/2σ²) — Gaussian bell curve centered on the charge.
     * Requires a sigma parameter on the charge. Smooth, bounded, no singularity at r=0.
     */
    GAUSSIAN;

    /** Parses a falloff type from a JSON string, case-insensitive. */
    public static FalloffType fromString(String s) {
        return switch (s.toLowerCase()) {
            case "inverse_square" -> INVERSE_SQUARE;
            case "inverse_linear" -> INVERSE_LINEAR;
            case "gaussian" -> GAUSSIAN;
            default -> throw new IllegalArgumentException("Unknown falloff type: " + s);
        };
    }
}
