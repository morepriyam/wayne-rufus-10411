// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import static edu.wpi.first.units.Units.MetersPerSecond;
import static edu.wpi.first.units.Units.RPM;
import static edu.wpi.first.units.Units.RotationsPerSecond;

import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.units.measure.AngularVelocity;
import edu.wpi.first.units.measure.LinearVelocity;
import frc.robot.generated.TunerConstants;

/**
 * The Constants class provides a convenient place for teams to hold robot-wide
 * numerical or boolean
 * constants. This class should not be used for any other purpose. All constants
 * should be declared
 * globally (i.e. public static). Do not put anything functional in this class.
 *
 * <p>
 * It is advised to statically import this class (or one of its inner classes)
 * wherever the
 * constants are needed, to reduce verbosity.
 */
public final class Constants {
    public static class Driving {
        public static final LinearVelocity kMaxSpeed = TunerConstants.kSpeedAt12Volts;
        public static final LinearVelocity kLimitedSpeed = LinearVelocity.ofBaseUnits(4, MetersPerSecond);
        public static final AngularVelocity kMaxRotationalRate = RotationsPerSecond.of(1);
        /** Scales manual rotation stick input to make turning easier to control. */
        public static final double kManualRotationScale = 0.7;
        /** Scales manual translation stick input for gentler driving control. */
        public static final double kManualTranslationScale = 0.7;
        public static final AngularVelocity kPIDRotationDeadband = kMaxRotationalRate.times(0.005);
    }

    public static class KrakenX60 {
        public static final AngularVelocity kFreeSpeed = RPM.of(6000);
    }

    public static class Vision {
        /** AprilTag localization pipeline (default). */
        public static final int PIPELINE_APRILTAG = 0;
        /** Neural Detector pipeline for fuel game pieces. */
        public static final int PIPELINE_FUEL_DETECTOR = 1;
    }

    public static class ForceField {
        /** Default preset name (loaded from deploy/forcefield/<name>.json). */
        public static final String kDefaultPreset = "default";

        /**
         * Swerve module corner offsets from robot center in meters [FL, FR, BL, BR].
         * Derived from TunerConstants module positions (10.875", 10.125").
         */
        public static final Translation2d[] kCornerOffsets = new Translation2d[] {
                new Translation2d(TunerConstants.FrontLeft.LocationX, TunerConstants.FrontLeft.LocationY),
                new Translation2d(TunerConstants.FrontRight.LocationX, TunerConstants.FrontRight.LocationY),
                new Translation2d(TunerConstants.BackLeft.LocationX, TunerConstants.BackLeft.LocationY),
                new Translation2d(TunerConstants.BackRight.LocationX, TunerConstants.BackRight.LocationY),
        };
    }
}
