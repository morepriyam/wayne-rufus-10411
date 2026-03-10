#pragma once

#include <stdexcept>
#include <string>

namespace refinery::forcefield {

/** Determines how a charge's force magnitude decreases with distance. */
enum class FalloffType {
  /** Force ∝ 1/r² */
  kInverseSquare,
  /** Force ∝ 1/r */
  kInverseLinear,
  /** Force ∝ exp(−r²/2σ²) — Gaussian bell curve */
  kGaussian
};

/** Parses a FalloffType from a JSON string, case-insensitive. */
inline FalloffType FalloffTypeFromString(const std::string& s) {
  if (s == "inverse_square") return FalloffType::kInverseSquare;
  if (s == "inverse_linear") return FalloffType::kInverseLinear;
  if (s == "gaussian") return FalloffType::kGaussian;
  throw std::invalid_argument("Unknown falloff type: " + s);
}

}  // namespace refinery::forcefield
