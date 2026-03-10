#pragma once

#include <array>

#include <frc/geometry/Translation2d.h>

namespace refinery::forcefield {

/**
 * Result of evaluating the force field at the robot's current pose.
 */
struct ForceResult {
  /** Net translational velocity offset (m/s, field-frame). */
  frc::Translation2d velocityOffset;

  /** Net angular velocity offset (rad/s, CCW-positive). */
  double angularVelocityOffset = 0.0;

  /** Force vectors at each swerve module corner [FL, FR, BL, BR]. */
  std::array<frc::Translation2d, 4> cornerForces;

  /** A zero result with no offsets. */
  static ForceResult Zero() {
    return ForceResult{frc::Translation2d{}, 0.0, {}};
  }
};

}  // namespace refinery::forcefield
