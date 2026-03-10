#include "refinery/forcefield/ForceFieldMap.h"

#include <filesystem>
#include <fstream>
#include <iostream>
#include <sstream>

#include <frc/Filesystem.h>

#include "refinery/forcefield/FalloffType.h"
#include "refinery/forcefield/LineCharge.h"
#include "refinery/forcefield/PointCharge.h"
#include "refinery/forcefield/RadialZone.h"

namespace refinery::forcefield {

ForceFieldMap::ForceFieldMap(std::string name, double maxForceVelocity,
                             double maxForceTorque, double forceGain,
                             double torqueGain,
                             std::vector<std::unique_ptr<Charge>> charges)
    : m_name{std::move(name)},
      m_maxForceVelocity{maxForceVelocity},
      m_maxForceTorque{maxForceTorque},
      m_forceGain{forceGain},
      m_torqueGain{torqueGain},
      m_charges{std::move(charges)} {}

frc::Translation2d ForceFieldMap::GetForceAt(
    const frc::Translation2d& point) const {
  double fx = 0.0, fy = 0.0;
  for (const auto& charge : m_charges) {
    auto f = charge->Evaluate(point);
    fx += f.X().value();
    fy += f.Y().value();
  }
  return frc::Translation2d{units::meter_t{fx}, units::meter_t{fy}};
}

ForceFieldMap ForceFieldMap::LoadFromDeploy(const std::string& presetName) {
  auto path = std::filesystem::path{frc::filesystem::GetDeployDirectory()} /
              "forcefield" / (presetName + ".json");
  try {
    std::ifstream file{path};
    if (!file.good()) {
      std::cerr << "[ForceField] Failed to open: " << path << "\n";
      return Empty();
    }
    std::stringstream ss;
    ss << file.rdbuf();
    return FromJson(ss.str());
  } catch (const std::exception& e) {
    std::cerr << "[ForceField] Load error: " << e.what() << "\n";
    return Empty();
  }
}

ForceFieldMap ForceFieldMap::FromJson(const std::string& json) {
  try {
    auto root = wpi::json::parse(json);
    std::string name = root.value("name", "Unnamed");
    double maxVel = root.value("maxForceVelocity", 2.0);
    double maxTorque = root.value("maxForceTorque", 1.5);
    double fGain = root.value("forceGain", 1.0);
    double tGain = root.value("torqueGain", 0.5);

    std::vector<std::unique_ptr<Charge>> charges;
    if (root.contains("charges")) {
      for (const auto& node : root["charges"]) {
        auto charge = ParseCharge(node);
        if (charge) {
          charges.push_back(std::move(charge));
        }
      }
    }
    return ForceFieldMap{name, maxVel, maxTorque, fGain, tGain,
                         std::move(charges)};
  } catch (const std::exception& e) {
    std::cerr << "[ForceField] JSON parse error: " << e.what() << "\n";
    return Empty();
  }
}

std::unique_ptr<Charge> ForceFieldMap::ParseCharge(const wpi::json& node) {
  std::string type = node.at("type").get<std::string>();
  std::string id = node.value("id", type + "_auto");

  if (type == "point") {
    return std::make_unique<PointCharge>(
        id,
        frc::Translation2d{units::meter_t{node.at("x").get<double>()},
                           units::meter_t{node.at("y").get<double>()}},
        node.at("strength").get<double>(),
        node.contains("falloff")
            ? FalloffTypeFromString(node.at("falloff").get<std::string>())
            : FalloffType::kInverseSquare,
        node.value("sigma", 0.5));
  } else if (type == "line") {
    return std::make_unique<LineCharge>(
        id,
        frc::Translation2d{units::meter_t{node.at("x1").get<double>()},
                           units::meter_t{node.at("y1").get<double>()}},
        frc::Translation2d{units::meter_t{node.at("x2").get<double>()},
                           units::meter_t{node.at("y2").get<double>()}},
        node.at("strength").get<double>(),
        node.value("falloffDistance", 1.0));
  } else if (type == "radial") {
    return std::make_unique<RadialZone>(
        id,
        frc::Translation2d{units::meter_t{node.at("x").get<double>()},
                           units::meter_t{node.at("y").get<double>()}},
        node.value("innerRadius", 0.3), node.value("outerRadius", 1.5),
        node.at("strength").get<double>());
  } else {
    std::cerr << "[ForceField] Unknown charge type: " << type << "\n";
    return nullptr;
  }
}

ForceFieldMap ForceFieldMap::Empty() {
  return ForceFieldMap{"Empty", 2.0, 1.5, 1.0, 0.5, {}};
}

}  // namespace refinery::forcefield
