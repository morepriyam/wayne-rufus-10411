#pragma once

#include <array>

#include <frc/geometry/Pose2d.h>
#include <frc/geometry/Translation2d.h>

#include "refinery/forcefield/ForceFieldMap.h"
#include "refinery/forcefield/ForceResult.h"

namespace refinery::forcefield {

/**
 * Evaluates the force field at the four swerve module corners.
 *
 * The engine produces both translational and rotational effects by computing
 * forces independently at each corner. Does not perform logging — teams should
 * log the returned ForceResult using their own framework.
 */
class ForceFieldEngine {
 public:
  /**
   * @param map           The active force field map (moved in)
   * @param cornerOffsets Module corners relative to robot center [FL, FR, BL, BR]
   */
  ForceFieldEngine(ForceFieldMap map,
                   std::array<frc::Translation2d, 4> cornerOffsets);

  /**
   * Computes the force field effect on the robot at its current pose.
   */
  ForceResult Compute(const frc::Pose2d& robotPose) const;

  /** Replaces the active force field map. */
  void SetMap(ForceFieldMap map);

  const ForceFieldMap& GetMap() const { return m_map; }

 private:
  ForceFieldMap m_map;
  std::array<frc::Translation2d, 4> m_cornerOffsets;
};

}  // namespace refinery::forcefield
