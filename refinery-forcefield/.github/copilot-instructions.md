# Copilot Instructions — refinery-forcefield

## Project Overview
Potential-field driving assist library for FRC swerve robots.
Part of **The REFINERY** by [BioNanomics](https://github.com/BioNanomics).

Invisible charges (point, line, radial) are placed on the field and converted
into real-time velocity / angular-velocity offsets that blend with driver
joystick input. The driver always has authority — they can push through forces.

## Tech Stack
- **Java 17**, Gradle `java-library` plugin, `maven-publish`
- **WPILib 2026.1.1** — wpimath, wpiunits, wpiutil, wpilibj
- **Jackson 2.19.2** — JSON preset parsing
- **C++ mirror** — WPILib C++ types (`frc::Translation2d`, `frc::Pose2d`, `wpi::json`)
- **No AdvantageKit dependency** — the library is framework-agnostic; consuming teams log `ForceResult` with their own system

## Repository Layout
```
refinery-forcefield/
├── editor/                           ← web-based visual preset editor (HTML/JS/CSS)
│   ├── index.html
│   ├── style.css
│   ├── editor.js
│   └── fields/                       ← WPILib field images + calibration JSONs
│       ├── manifest.json             ← ordered list of field JSON filenames
│       ├── 2026-rebuilt.json         ← WPILib calibration (field-corners, field-size)
│       ├── 2026-field.png
│       └── ...                       ← one JSON + one PNG per season
├── src/main/java/com/bionanomics/refinery/forcefield/
│   ├── Charge.java                   ← interface: evaluate(point) → force vector
│   ├── PointCharge.java              ← gaussian / inverse falloff from a point
│   ├── LineCharge.java               ← perpendicular force along a segment (walls)
│   ├── RadialZone.java              ← constant inner + linear ramp to outer radius
│   ├── FalloffType.java             ← enum: GAUSSIAN, INVERSE_SQUARE, INVERSE_LINEAR
│   ├── ForceResult.java             ← record: velocityOffset, angularVelocityOffset, cornerForces[4]
│   ├── ForceFieldMap.java           ← loads JSON presets, evaluates net force at a point
│   ├── ForceFieldEngine.java        ← evaluates at 4 swerve corners, computes torque, clamps
│   └── ForceFieldConfig.java        ← SmartDashboard preset chooser + live tuning
├── src/main/native/
│   ├── include/refinery/forcefield/  ← C++ headers (mirror of Java API)
│   │   ├── ForceField.h              ← convenience header (includes all)
│   │   └── *.h
│   └── cpp/                          ← C++ implementations
│       └── *.cpp
├── build.gradle
├── settings.gradle
├── README.md
└── .gitignore
```

## Architecture

### Core Data Flow
```
Preset JSON → ForceFieldMap → ForceFieldEngine.compute(Pose2d)
                                      ↓
                                ForceResult {
                                  velocityOffset (m/s, field-frame),
                                  angularVelocityOffset (rad/s),
                                  cornerForces[4] (FL, FR, BL, BR)
                                }
```

### How the Engine Works
1. Four swerve module corners are transformed from robot-local to field coordinates
2. Each charge's `evaluate()` is called at each corner position
3. Forces accumulate per-corner, then are averaged for net translation
4. Torque is computed via 2D cross product (r × F) in robot frame
5. Gains are applied, outputs are clamped to `maxForceVelocity` / `maxForceTorque`
6. Result is returned — **no logging, no side effects**

### Strength Convention
- **Positive** = attractive (pulls toward the charge)
- **Negative** = repulsive (pushes away from the charge)

## Key Implementation Details

### PointCharge
- Minimum distance clamp: `0.05m` (prevents divide-by-zero singularities)
- Gaussian falloff: `strength × exp(-d² / (2σ²))` — natural bell-curve shape
- Inverse falloffs: `strength / d²` or `strength / d` — for hard boundaries

### LineCharge
- Projects query point onto the line segment (clamped to endpoints)
- Force direction is perpendicular to the segment, pointing away from it
- Linear falloff from 0 to `falloffDistance` — zero force beyond

### RadialZone
- Full strength inside `innerRadius`
- Linear ramp from `innerRadius` to `outerRadius`
- Zero force beyond `outerRadius`

### ForceFieldMap
- Loads JSON via Jackson from `Filesystem.getDeployDirectory() + "/forcefield/"` + name + `.json`
- `listPresets()` scans the deploy folder for available `.json` files
- `getForceAt(Translation2d)` sums all charges — called 4× per cycle (once per corner)

### ForceFieldConfig
- Publishes `SendableChooser` to SmartDashboard for preset switching
- Live-tunable gains: `ForceField/ForceGain`, `ForceField/TorqueGain`, `ForceField/MaxVelocity`, `ForceField/MaxTorque`
- `update()` must be called periodically (e.g., from `robotPeriodic()`)

## Preset JSON Schema
```json
{
  "name": "string",
  "maxForceVelocity": 2.0,
  "maxForceTorque": 1.5,
  "forceGain": 1.0,
  "torqueGain": 0.5,
  "charges": [
    { "type": "point|line|radial", "id": "string", ... }
  ]
}
```
Presets live in the consuming robot project at `src/main/deploy/forcefield/*.json`.

## Web Editor (`editor/`)
- Standalone HTML/JS/CSS — no build step, no dependencies
- Catppuccin Mocha dark theme
- FRC field canvas with dynamic dimensions per season (calibrated from WPILib field-size metadata)
- Color scheme: **green** = attractor, **yellow** = repulsor (avoids FRC red/blue alliance confusion)
- Supports drag-to-move for all charge types
- Drag handles: purple = center/endpoints, yellow = outer radius / sigma, green = inner radius
- Exports/imports the same JSON format consumed by `ForceFieldMap`
- **Field images**: loaded dynamically from `editor/fields/manifest.json` → WPILib calibration JSONs
  - To add a new season: drop a `YYYY-game.json` + `YYYY-field.png` into `editor/fields/`, add the JSON filename to `manifest.json`
- Serve locally with `python3 -m http.server 8765` from the `editor/` directory
- Planned: GitHub Pages deployment under BioNanomics

## C++ Parity
- C++ headers and sources mirror the Java API 1:1
- Namespace: `refinery::forcefield`
- Uses WPILib C++ types: `frc::Translation2d`, `frc::Pose2d`, `frc::Rotation2d`, `wpi::json`
- No native Gradle build configured yet — teams copy sources into their own C++ project
- When changing Java API, update the corresponding C++ header and source to stay in sync

## Development

### Build
```sh
./gradlew build          # compile + test
./gradlew publishToMavenLocal  # install to ~/.m2 for local testing
```

### Using as a Composite Build (development)
In the consuming robot project:
```gradle
// settings.gradle
includeBuild '../refinery-forcefield'

// build.gradle
dependencies {
    implementation 'com.bionanomics.refinery:refinery-forcefield'
}
```

### Version
- Managed in `build.gradle` → `version = '0.1.0'`
- Bump before tagging a release
- Maven coordinates: `com.bionanomics.refinery:refinery-forcefield:VERSION`

## Code Quality Principles

### 🎯 DRY (Don't Repeat Yourself)
- Extract reusable functions — never duplicate logic
- Single source of truth for each piece of knowledge
- Java ↔ C++ share the same algorithms; keep implementations aligned

### 💋 KISS (Keep It Simple, Stupid)
- Prefer the simplest solution that works
- Self-documenting names over comments
- Small, focused functions

### 🧹 Folder Philosophy
- Every folder has a clear purpose anchored by its main content
- No loose files without context

### ⚰️ Dead Code
- Remove immediately when identified
- Move significant dead code to `.attic/` with context if historically important

### 🔬 Testing
- JUnit 5 via `./gradlew test`
- Test each charge type independently: known input → expected force vector
- Test engine with a simple preset: verify velocity and torque offsets
- Test JSON round-trip: export → import → verify identical map

### 🪶 Pull Request Philosophy
- Smallest viable change that fully solves the problem
- Java and C++ changes for the same feature go in one PR
- Editor changes can be a separate PR unless coupled to engine changes

## Quick Reference

### Coordinate System
- **Standard**: WPILib "Always Blue Origin" — shared with Choreo and PathPlanner
- **Origin**: Blue alliance corner (bottom-left when viewed from above)
- **X**: Toward red alliance wall (0 → ~16.54m, varies by year)
- **Y**: Toward far side wall (0 → ~8.07m, varies by year)
- **Rotation**: CCW-positive, 0° = toward red alliance wall
- **Units**: Meters, radians
- **References**: [WPILib docs](https://docs.wpilib.org/en/stable/docs/software/basic-programming/coordinate-system.html), [Choreo](https://choreo.autos), [PathPlanner](https://pathplanner.dev)
- Field dimensions are dynamic per season — derived from WPILib's field-size calibration JSON

### Typical Tuning Ranges
| Parameter | Range | Notes |
|-----------|-------|-------|
| `forceGain` | 0.5 – 2.0 | Translational responsiveness |
| `torqueGain` | 0.2 – 1.0 | Rotational responsiveness |
| `maxForceVelocity` | 1.0 – 3.0 m/s | Caps velocity offset |
| `maxForceTorque` | 0.5 – 2.0 rad/s | Caps angular velocity offset |
| `strength` | ±1.0 – ±5.0 | Per-charge; negative = repel |

### Code Quality Checklist
- [ ] **DRY**: No duplicated logic across Java or C++
- [ ] **KISS**: Simplest correct solution
- [ ] **Parity**: Java and C++ implementations match
- [ ] **Naming**: Self-documenting function/variable names
- [ ] **Tests**: `./gradlew test` passes
- [ ] **Editor**: If JSON schema changed, editor handles it
- [ ] **Docs**: README updated for new features
