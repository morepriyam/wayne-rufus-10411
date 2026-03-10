#include "refinery/forcefield/LineCharge.h"

namespace refinery::forcefield {

frc::Translation2d LineCharge::Evaluate(const frc::Translation2d& point) const {
  frc::Translation2d segmentVec = m_end - m_start;
  double segLenSq = segmentVec.Norm().value() * segmentVec.Norm().value();

  // Degenerate segment — treat as a point
  if (segLenSq < 1e-9) {
    frc::Translation2d delta = m_start - point;
    double r = delta.Norm().value();
    if (r < kMinDistance) r = kMinDistance;
    double magnitude = m_strength * std::max(0.0, 1.0 - r / m_falloffDistance);
    if (r < 1e-9) return frc::Translation2d{};
    return (delta / delta.Norm().value()) * magnitude;
  }

  // Project point onto segment
  frc::Translation2d startToPoint = point - m_start;
  double t = (startToPoint.X().value() * segmentVec.X().value() +
              startToPoint.Y().value() * segmentVec.Y().value()) /
             segLenSq;
  t = std::clamp(t, 0.0, 1.0);

  frc::Translation2d closest = m_start + segmentVec * t;
  frc::Translation2d delta = closest - point;
  double r = delta.Norm().value();

  if (r > m_falloffDistance) {
    return frc::Translation2d{};
  }
  if (r < kMinDistance) {
    r = kMinDistance;
  }

  double magnitude = m_strength * (1.0 - r / m_falloffDistance);

  if (delta.Norm().value() < 1e-9) {
    return frc::Translation2d{};
  }

  return (delta / delta.Norm().value()) * magnitude;
}

}  // namespace refinery::forcefield
