# Teleop Xbox Controller Checklist

> Source: `src/main/java/frc/robot/RobotContainer.java`

## Joysticks / Axes (sanity check)
- [x] Left stick translation works (`LeftX/LeftY` drive).
- [x] Right stick rotation works (`RightX` rotates).
- [x] Right stick rotation feels “gentle” enough to maneuver.

## Buttons (what each one does)
- [x] `A` (press): lock heading to `180deg`
- [x] `B` (press): lock heading to `CW 90deg`
- [x] `X` (press): lock heading to `CCW 90deg`
- [x] `Y` (press): lock heading to `0deg`
- [ ] `Back` (press): re-zero field-centric heading (`seedFieldCentric`)
- [ ] `Start` (hold): run `FuelChaseCommand` (aim/drive + intake)

- [x] `Left Trigger` (hold): run rollers only; stops on release (pivot unaffected)
- [ ] `Right Trigger` (hold): `aimAndShoot()` (aim + prep + feed)
- [x] `Left Bumper` (press): toggle pivot `STOWED` ↔ `INTAKE`; ignored while mid-move
- [ ] `Right Bumper` (hold): `shootManually()` (spin shooter + feed)

- [ ] D-pad `Left` (hold): reverse floor + reverse shooter (parallel)
- [ ] D-pad `Up` (press): nudge hood +0.02 (raise)
- [ ] D-pad `Down` (press): nudge hood -0.02 (lower)

- [ ] `Right Stick Button` (press): toggle force field overlay on/off

## Notes / Observations
- Record any issues you see (axis direction reversed, stuck command, unexpected behavior, etc.).
- Verify: `Field Centric` toggle affects driving translation only; ABXY heading-lock works the same with it ON or OFF.

## Control Details (quick reference)
- `A`: locks robot heading to `180deg` (operator/field perspective)
- `B`: locks robot heading to `CW 90deg`
- `X`: locks robot heading to `CCW 90deg`
- `Y`: locks robot heading to `0deg`
- `Back`: re-seeds field-centric heading to the robot’s current orientation (`seedFieldCentric`)
- `Start`: runs `FuelChaseCommand` while held (Limelight + drive + intake)
- `Left Trigger`: while held, runs rollers only (`Speed.INTAKE`); on release, rollers stop (`Speed.STOP`); pivot position unaffected
- `Right Trigger`: while held, runs `aimAndShoot()` (aiming + shot prep + feeder/floor feed)
- `Left Bumper`: toggles pivot between `STOWED` and `INTAKE`; press is ignored if pivot is still mid-move
- `Right Bumper`: while held, runs `shootManually()` (spins shooter to dashboard RPM and feeds once above threshold)
- `D-pad Left`: while held, runs `floor.reverseCommand()` and `shooter.reverseCommand()` in parallel (stops on release)
- `Right Stick Button`: press toggles the ForceField overlay/pushback behavior on/off
- Not bound / no action (confirm): D-pad `Right`, `Left Stick Button`, `Guide`
