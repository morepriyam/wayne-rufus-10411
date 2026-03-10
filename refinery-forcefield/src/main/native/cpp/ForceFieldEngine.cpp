#include "refinery/forcefield/ForceFieldEngine.h"

#include <frc/MathUtil.h>

namespace refinery::forcefield {

ForceFieldEngine::ForceFieldEngine(ForceFieldMap map,
                                   std::array<frc::Translation2d, 4> cornerOffsets)
    : m_map{std::move(map)}, m_cornerOffsets{cornerOffsets} {}

ForceResult ForceFieldEngine::Compute(const frc::Pose2d& robotPose) const {
  auto heading = robotPose.Rotation();
  auto robotCenter = robotPose.Translation();

  ForceResult result;
  double netFx = 0.0, netFy = 0.0, netTorque = 0.0;

  for (int i = 0; i < 4; ++i) {
    // Transform corner from robot-local to field coordinates
    auto cornerField = robotCenter + m_cornerOffsets[i].RotateBy(heading);

    // Evaluate net force at this corner
    auto force = m_map.GetForceAt(cornerField);
    result.cornerForces[i] = force;

    netFx += force.X().value();
    netFy += force.Y().value();

    // Torque: τ = r × F (2D cross product in robot frame)
    auto forceRobotFrame = force.RotateBy(-heading);
    auto r = m_cornerOffsets[i];
    double torque = r.X().value() * forceRobotFrame.Y().value() -
                    r.Y().value() * forceRobotFrame.X().value();
    netTorque += torque;
  }

  // Average across 4 corners
  netFx /= 4.0;
  netFy /= 4.0;
  netTorque /= 4.0;

  // Apply gains and clamp
  double vx = std::clamp(netFx * m_map.ForceGain(),
                          -m_map.MaxForceVelocity(), m_map.MaxForceVelocity());
  double vy = std::clamp(netFy * m_map.ForceGain(),
                          -m_map.MaxForceVelocity(), m_map.MaxForceVelocity());

  frc::Translation2d velocityOffset{units::meter_t{vx}, units::meter_t{vy}};
  if (velocityOffset.Norm().value() > m_map.MaxForceVelocity()) {
    velocityOffset = velocityOffset / velocityOffset.Norm().value() *
                     m_map.MaxForceVelocity();
  }

  double angularOffset =
      std::clamp(netTorque * m_map.TorqueGain(),
                 -m_map.MaxForceTorque(), m_map.MaxForceTorque());

  result.velocityOffset = velocityOffset;
  result.angularVelocityOffset = angularOffset;
  return result;
}

void ForceFieldEngine::SetMap(ForceFieldMap map) {
  m_map = std::move(map);
}

}  // namespace refinery::forcefield
