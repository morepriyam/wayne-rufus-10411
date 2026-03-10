#pragma once

#include <memory>
#include <string>
#include <vector>

#include <frc/geometry/Translation2d.h>
#include <wpi/json.h>

#include "refinery/forcefield/Charge.h"

namespace refinery::forcefield {

/**
 * A collection of Charge elements defining the force field layout.
 *
 * Loaded from JSON (deployed to the roboRIO or available in simulation).
 */
class ForceFieldMap {
 public:
  ForceFieldMap(std::string name, double maxForceVelocity, double maxForceTorque,
                double forceGain, double torqueGain,
                std::vector<std::unique_ptr<Charge>> charges);

  /**
   * Computes the net force at a point on the field by summing all charges.
   */
  frc::Translation2d GetForceAt(const frc::Translation2d& point) const;

  /** Loads a map from the deploy directory by preset name. */
  static ForceFieldMap LoadFromDeploy(const std::string& presetName);

  /** Parses a map from a JSON string. */
  static ForceFieldMap FromJson(const std::string& json);

  /** Returns an empty map with no charges. */
  static ForceFieldMap Empty();

  const std::string& Name() const { return m_name; }
  double MaxForceVelocity() const { return m_maxForceVelocity; }
  double MaxForceTorque() const { return m_maxForceTorque; }
  double ForceGain() const { return m_forceGain; }
  double TorqueGain() const { return m_torqueGain; }
  size_t ChargeCount() const { return m_charges.size(); }

 private:
  static std::unique_ptr<Charge> ParseCharge(const wpi::json& node);

  std::string m_name;
  double m_maxForceVelocity;
  double m_maxForceTorque;
  double m_forceGain;
  double m_torqueGain;
  std::vector<std::unique_ptr<Charge>> m_charges;
};

}  // namespace refinery::forcefield
