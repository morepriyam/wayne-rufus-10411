#pragma once

#include <algorithm>
#include <cmath>
#include <string>

#include <frc/geometry/Translation2d.h>

#include "refinery/forcefield/Charge.h"

namespace refinery::forcefield {

/**
 * A line charge that creates force perpendicular to a wall or barrier segment.
 *
 * Force falls off linearly from full strength at the segment to zero at
 * falloffDistance meters away.
 */
class LineCharge : public Charge {
 public:
  /**
   * @param id              Unique identifier
   * @param start           Start point in field meters
   * @param end             End point in field meters
   * @param strength        Force magnitude; negative = repulsive (walls)
   * @param falloffDistance Distance at which force reaches zero
   */
  LineCharge(std::string id, frc::Translation2d start, frc::Translation2d end,
             double strength, double falloffDistance)
      : m_id{std::move(id)},
        m_start{start},
        m_end{end},
        m_strength{strength},
        m_falloffDistance{std::max(falloffDistance, kMinDistance)} {}

  frc::Translation2d Evaluate(const frc::Translation2d& point) const override;

  std::string Id() const override { return m_id; }
  double Strength() const override { return m_strength; }
  frc::Translation2d Start() const { return m_start; }
  frc::Translation2d End() const { return m_end; }
  double FalloffDistance() const { return m_falloffDistance; }

 private:
  static constexpr double kMinDistance = 0.02;

  std::string m_id;
  frc::Translation2d m_start;
  frc::Translation2d m_end;
  double m_strength;
  double m_falloffDistance;
};

}  // namespace refinery::forcefield
