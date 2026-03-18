# Teleop Xbox Controller Checklist

> Source: `src/main/java/frc/robot/RobotContainer.java`

## Joysticks / Axes (sanity check)
- [x] Left stick translation works (`LeftX/LeftY` drive).
- [x] Right stick rotation works (`RightX` rotates).
- [x] Right stick rotation feels “gentle” enough to maneuver.

## Buttons (what each one does)
- [ ] `A` (press): lock heading to `180deg`
- [ ] `B` (press): lock heading to `CW 90deg`
- [ ] `X` (press): lock heading to `CCW 90deg`
- [ ] `Y` (press): lock heading to `0deg`
- [ ] `Back` (press): re-zero field-centric heading (`seedFieldCentric`)
- [ ] `Start` (hold): run `FuelChaseCommand` (aim/drive + intake)

- [ ] `Left Trigger` (hold): `intake.intakeCommand()` (pivot + rollers)
- [ ] `Right Trigger` (hold): `aimAndShoot()` (aim + prep + feed)
- [ ] `Left Bumper` (press): stow intake pivot (`Position.STOWED`)
- [ ] `Right Bumper` (hold): `shootManually()` (spin shooter + feed)

- [ ] D-pad `Up` (hold): climber disabled (confirm no action)
- [ ] D-pad `Down` (hold): climber disabled (confirm no action)
- [ ] D-pad `Left` (hold): reverse floor + reverse shooter (parallel)
- [ ] D-pad `Right` (hold): confirm no action

- [ ] `Right Stick Button` (press): toggle force field overlay on/off
- [ ] `Left Stick Button` (press): confirm no action
- [ ] `Guide` (press): confirm no action

## Notes / Observations
- Record any issues you see (axis direction reversed, stuck command, unexpected behavior, etc.).
