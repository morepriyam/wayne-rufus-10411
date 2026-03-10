#include "refinery/forcefield/RadialZone.h"

namespace refinery::forcefield {

frc::Translation2d RadialZone::Evaluate(const frc::Translation2d& point) const {
  frc::Translation2d delta = m_center - point;
  double r = delta.Norm().value();

  if (r > m_outerRadius) {
    return frc::Translation2d{};
  }

  double magnitude;
  if (r <= m_innerRadius) {
    magnitude = m_strength;
  } else {
    double rampFraction = 1.0 - (r - m_innerRadius) / (m_outerRadius - m_innerRadius);
    magnitude = m_strength * rampFraction;
  }

  if (r < 1e-9) {
    return frc::Translation2d{};
  }

  return (delta / r) * magnitude;
}

}  // namespace refinery::forcefield
