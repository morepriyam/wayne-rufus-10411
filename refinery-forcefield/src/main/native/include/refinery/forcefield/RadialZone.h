#pragma once

#include <algorithm>
#include <cmath>
#include <string>

#include <frc/geometry/Translation2d.h>

#include "refinery/forcefield/Charge.h"

namespace refinery::forcefield {

/**
 * A radial zone with constant force inside innerRadius and linear ramp to outerRadius.
 *
 * Positive strength = snap-to (climbing/shooting), negative = keep-out.
 */
class RadialZone : public Charge {
 public:
  /**
   * @param id          Unique identifier
   * @param center      Center of the zone in field meters
   * @param innerRadius Constant-force snap zone (meters)
   * @param outerRadius Radius at which force drops to zero
   * @param strength    Force magnitude; positive = attractive
   */
  RadialZone(std::string id, frc::Translation2d center, double innerRadius,
             double outerRadius, double strength)
      : m_id{std::move(id)},
        m_center{center},
        m_innerRadius{innerRadius},
        m_outerRadius{std::max(outerRadius, innerRadius + 0.01)},
        m_strength{strength} {}

  frc::Translation2d Evaluate(const frc::Translation2d& point) const override;

  std::string Id() const override { return m_id; }
  double Strength() const override { return m_strength; }
  frc::Translation2d Center() const { return m_center; }
  double InnerRadius() const { return m_innerRadius; }
  double OuterRadius() const { return m_outerRadius; }

 private:
  std::string m_id;
  frc::Translation2d m_center;
  double m_innerRadius;
  double m_outerRadius;
  double m_strength;
};

}  // namespace refinery::forcefield
