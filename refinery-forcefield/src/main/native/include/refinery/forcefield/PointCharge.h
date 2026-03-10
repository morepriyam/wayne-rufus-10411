#pragma once

#include <cmath>
#include <string>

#include <frc/geometry/Translation2d.h>

#include "refinery/forcefield/Charge.h"
#include "refinery/forcefield/FalloffType.h"

namespace refinery::forcefield {

/**
 * A point charge that exerts force radially toward or away from its position.
 *
 * Positive strength attracts, negative repels.
 */
class PointCharge : public Charge {
 public:
  /**
   * @param id       Unique identifier
   * @param position Field position in meters
   * @param strength Force magnitude; positive = attractive, negative = repulsive
   * @param falloff  How force decays with distance
   * @param sigma    Gaussian width in meters (ignored for non-Gaussian)
   */
  PointCharge(std::string id, frc::Translation2d position, double strength,
              FalloffType falloff, double sigma = 0.5)
      : m_id{std::move(id)},
        m_position{position},
        m_strength{strength},
        m_falloff{falloff},
        m_sigma{sigma} {}

  frc::Translation2d Evaluate(const frc::Translation2d& point) const override;

  std::string Id() const override { return m_id; }
  double Strength() const override { return m_strength; }
  frc::Translation2d Position() const { return m_position; }
  FalloffType Falloff() const { return m_falloff; }
  double Sigma() const { return m_sigma; }

 private:
  static constexpr double kMinDistance = 0.05;

  std::string m_id;
  frc::Translation2d m_position;
  double m_strength;
  FalloffType m_falloff;
  double m_sigma;
};

}  // namespace refinery::forcefield
