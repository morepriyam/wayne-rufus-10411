#pragma once

#include <frc/geometry/Translation2d.h>
#include <string>

namespace refinery::forcefield {

/**
 * A charge element in a force field map.
 *
 * Each charge can evaluate the force it exerts on a given field point.
 * Positive strength = attractive (pulls toward), negative = repulsive (pushes away).
 */
class Charge {
 public:
  virtual ~Charge() = default;

  /**
   * Computes the force vector this charge exerts at the given field position.
   *
   * @param point A point on the field in meters (blue-alliance origin)
   * @return Force vector in arbitrary force units (direction + magnitude)
   */
  virtual frc::Translation2d Evaluate(const frc::Translation2d& point) const = 0;

  /** Unique identifier for this charge. */
  virtual std::string Id() const = 0;

  /** The raw strength value (positive = attractive, negative = repulsive). */
  virtual double Strength() const = 0;
};

}  // namespace refinery::forcefield
