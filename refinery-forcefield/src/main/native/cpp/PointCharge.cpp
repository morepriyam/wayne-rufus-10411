#include "refinery/forcefield/PointCharge.h"

namespace refinery::forcefield {

frc::Translation2d PointCharge::Evaluate(const frc::Translation2d& point) const {
  frc::Translation2d delta = m_position - point;
  double r = delta.Norm().value();

  if (r < kMinDistance && m_falloff != FalloffType::kGaussian) {
    r = kMinDistance;
  }

  double magnitude;
  switch (m_falloff) {
    case FalloffType::kInverseSquare:
      magnitude = m_strength / (r * r);
      break;
    case FalloffType::kInverseLinear:
      magnitude = m_strength / r;
      break;
    case FalloffType::kGaussian:
      magnitude = m_strength * std::exp(-(r * r) / (2.0 * m_sigma * m_sigma));
      break;
  }

  if (r < 1e-9) {
    return frc::Translation2d{};
  }

  frc::Translation2d direction = delta / delta.Norm().value();
  return direction * magnitude;
}

}  // namespace refinery::forcefield
