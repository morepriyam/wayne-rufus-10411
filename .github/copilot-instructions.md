# Copilot Instructions — rufus10411

## Project Overview
FRC Team 10411 robot ("Rufus") — 2026 season.
Java, WPILib 2026.1.1, CTRE Phoenix 6, ChoreoLib, AdvantageKit.

## Robot Connection
- **Deploy IP:** `10.104.11.2` (non-standard, hardcoded in `build.gradle`)
- **Deploy command:** `./gradlew deploy`
- **CANivore bus name:** `main` (swerve drive motors)
- **RoboRIO bus name:** `rio` (all other motors)

## Shooter Notes
- Feed threshold: **3500 RPM** — floor/feeder only start once shooter crosses this
- Overshoot factor: **1.15×** — motors are commanded 15% above target to overcome bus voltage sag
- `PeakReverseVoltage` is set to `-12V` to allow reverse
- KI = 2 with velocity voltage control — watch for windup at high targets
- Supply voltage per motor is published to SmartDashboard for diagnosing sag

## Test Mode
- Homing (intake pivot, hanger) is suppressed in test mode
- Swerve `seedFieldCentric()` and operator perspective setup are suppressed in test mode

## Logging (AdvantageKit)
- Logs saved to `/home/lvuser/logs/` on roboRIO as `.wpilog`
- Also published live via NT4 (visible in AdvantageScope when connected)
- Retrieve after session: `sftp lvuser@10.104.11.2:/home/lvuser/logs/*.wpilog .`
- Open in AdvantageScope for timeline analysis

## Key Conventions
- All subsystems use `SmartDashboard.putData(this)` for Shuffleboard visibility
- Hanger homing uses `kCancelSelf` — any position command will interrupt it
- `Robot` extends `LoggedRobot` (AdvantageKit), not `TimedRobot`
- Shooter default command calls `stop()` so explicit stop calls in `end()` are not needed


## Code Quality Principles
<!-- https://github.com/mieweb/template-mieweb-opensource/blob/main/.github/copilot-instructions.md -->

### 🎯 DRY (Don't Repeat Yourself)
- **Never duplicate code**: If you find yourself copying code, extract it into a reusable function
- **Single source of truth**: Each piece of knowledge should have one authoritative representation
- **Refactor mercilessly**: When you see duplication, eliminate it immediately
- **Shared utilities**: Common patterns should be abstracted into utility functions

### 💋 KISS (Keep It Simple, Stupid)
- **Simple solutions**: Prefer the simplest solution that works
- **Avoid over-engineering**: Don't add complexity for hypothetical future needs
- **Clear naming**: Functions and variables should be self-documenting
- **Small functions**: Break down complex functions into smaller, focused ones
- **Readable code**: Code should be obvious to understand at first glance

### 🧹 Folder Philosophy
- **Clear purpose**: Every folder should have a main thing that anchors its contents.
- **No junk drawers**: Don’t leave loose files without context or explanation.
- **Explain relationships**: If it’s not elegantly obvious how files fit together, add a README or note.
- **Immediate clarity**: Opening a folder should make its organizing principle clear at a glance.

### 🔄 Refactoring Guidelines
- **Continuous improvement**: Refactor as you work, not as a separate task
- **Safe refactoring**: Always run tests before and after refactoring
- **Incremental changes**: Make small, safe changes rather than large rewrites
- **Preserve behavior**: Refactoring should not change external behavior
- **Code reviews**: All refactoring should be reviewed for correctness

### ⚰️ Dead Code Management
- **Immediate removal**: Delete unused code immediately when identified
- **Historical preservation**: Move significant dead code to `.attic/` directory with context
- **Documentation**: Include comments explaining why code was moved to attic
- **Regular cleanup**: Review and clean attic directory periodically
- **No accumulation**: Don't let dead code accumulate in active codebase

### 🌐 Testing with MCP Browser
- Use MCP browser in Playwright if available to test functionality
- **Never close the browser** after running MCP browser commands unless explicitly asked
- Let the user interact with the browser after navigation or testing
- Only use `browser_close` when the user specifically requests it

## Documentation Preferences

### Diagrams and Visual Documentation
- **Always use Mermaid diagrams** instead of ASCII art for workflow diagrams, architecture diagrams, and flowcharts
- **Use memorable names** instead of single letters in diagrams (e.g., `Engine`, `Auth`, `Server` instead of `A`, `B`, `C`)
- Use appropriate Mermaid diagram types:
  - `graph TB` or `graph LR` for workflow architectures 
  - `flowchart TD` for process flows
  - `sequenceDiagram` for API interactions
  - `gitgraph` for branch/release strategies
- Include styling with `classDef` for better visual hierarchy
- Add descriptive comments and emojis sparingly for clarity

### Documentation Standards
- Keep documentation DRY (Don't Repeat Yourself) - reference other docs instead of duplicating
- Use clear cross-references between related documentation files
- Update the main architecture document when workflow structure changes

## Working with GitHub Actions Workflows

### Development Philosophy
- **Script-first approach**: All workflows should call scripts that can be run locally
- **Local development parity**: Developers should be able to run the exact same commands locally as CI runs
- **Simple workflows**: GitHub Actions should be thin wrappers around scripts, not contain complex logic
- **Easy debugging**: When CI fails, developers can reproduce the issue locally by running the same script

## Quick Reference

### 🪶 All Changes should be considered for Pull Request Philosophy

* **Smallest viable change**: Always make the smallest change that fully solves the problem.
* **Fewest files first**: Start with the minimal number of files required.
* **No sweeping edits**: Broad refactors or multi-module changes must be split or proposed as new components.
* **Isolated improvements**: If a change grows complex, extract it into a new function, module, or component instead of modifying multiple areas.
* **Direct requests only**: Large refactors or architectural shifts should only occur when explicitly requested.
 
### Code Quality Checklist
- [ ] **DRY**: No code duplication - extracted reusable functions?
- [ ] **KISS**: Simplest solution that works?
- [ ] **Minimal Changes**: Smallest viable change made for PR?
- [ ] **Naming**: Self-documenting function/variable names?
- [ ] **Size**: Functions small and focused?
- [ ] **Dead Code**: Removed or archived appropriately?
- [ ] **Test**: Run tests
