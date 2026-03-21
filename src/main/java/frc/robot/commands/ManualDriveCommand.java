package frc.robot.commands;

import static edu.wpi.first.units.Units.MetersPerSecond;
import static edu.wpi.first.units.Units.RadiansPerSecond;
import static edu.wpi.first.units.Units.Seconds;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import com.ctre.phoenix6.swerve.SwerveModule.DriveRequestType;
import com.ctre.phoenix6.swerve.SwerveModule.SteerRequestType;
import com.ctre.phoenix6.swerve.SwerveRequest;
import com.ctre.phoenix6.swerve.SwerveRequest.ForwardPerspectiveValue;

import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.units.measure.LinearVelocity;
import edu.wpi.first.units.measure.Time;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import org.littletonrobotics.junction.Logger;
import frc.robot.Constants.Driving;
import com.bionanomics.refinery.forcefield.ForceFieldEngine;
import com.bionanomics.refinery.forcefield.ForceResult;
import frc.robot.subsystems.Swerve;
import frc.util.DriveInputSmoother;
import frc.util.ManualDriveInput;
import frc.util.Stopwatch;

/**
 * Teleop manual drive command for the swerve drivetrain.
 *
 * Handles field-centric driving with manual rotation input and
 * heading-hold behavior after a short delay once rotation input
 * returns to zero.
 *
 * Drive mode (field-centric vs robot-centric) is toggled live from Elastic
 * via the "Field Centric" SmartDashboard boolean entry.
 *
 * An optional force field overlay adds velocity offsets based on the robot's
 * position relative to defined charges (walls, snap-to zones, etc.).
 * The force field is toggled via a BooleanSupplier (controller button).
 */
public class ManualDriveCommand extends Command {
    private static final String kFieldCentricKey = "Field Centric";
    private static final String kForceFieldEnabledKey = "ForceField/Enabled";

    private enum State {
        IDLING,
        DRIVING_WITH_MANUAL_ROTATION,
        DRIVING_WITH_LOCKED_HEADING
    }

    private static final Time kHeadingLockDelay = Seconds.of(0.25); // time to wait before locking heading

    private final Swerve swerve;
    private final DriveInputSmoother inputSmoother;
    private final ForceFieldEngine forceFieldEngine;
    private final BooleanSupplier forceFieldToggle;
    private boolean lastForceFieldToggleState = false;
    private final NetworkTableEntry fieldCentricEntry;
    private final SwerveRequest.Idle idleRequest = new SwerveRequest.Idle();
    private boolean forceFieldEnabled = false;

    private final SwerveRequest.FieldCentric fieldCentricRequest = new SwerveRequest.FieldCentric()
            .withDriveRequestType(DriveRequestType.OpenLoopVoltage)
            .withSteerRequestType(SteerRequestType.MotionMagicExpo)
            .withForwardPerspective(ForwardPerspectiveValue.OperatorPerspective);

    private final SwerveRequest.FieldCentricFacingAngle fieldCentricFacingAngleRequest = new SwerveRequest.FieldCentricFacingAngle()
            .withRotationalDeadband(Driving.kPIDRotationDeadband)
            .withMaxAbsRotationalRate(Driving.kMaxRotationalRate)
            .withDriveRequestType(DriveRequestType.OpenLoopVoltage)
            .withSteerRequestType(SteerRequestType.MotionMagicExpo)
            .withForwardPerspective(ForwardPerspectiveValue.OperatorPerspective)
            .withHeadingPID(5, 0, 0);

    private State currentState = State.IDLING;
    private Optional<Rotation2d> lockedHeading = Optional.empty();
    private Stopwatch headingLockStopwatch = new Stopwatch();
    private ManualDriveInput previousInput = new ManualDriveInput();

    public ManualDriveCommand(
            Swerve swerve,
            DoubleSupplier forwardInput,
            DoubleSupplier leftInput,
            DoubleSupplier rotationInput,
            ForceFieldEngine forceFieldEngine,
            BooleanSupplier forceFieldToggle) {
        this.swerve = swerve;
        this.inputSmoother = new DriveInputSmoother(forwardInput, leftInput, rotationInput);
        this.forceFieldEngine = forceFieldEngine;
        this.forceFieldToggle = forceFieldToggle;
        // Publish the toggle so Elastic can display it as a Boolean Box / Toggle Button
        // widget.
        SmartDashboard.putBoolean(kFieldCentricKey, true);
        SmartDashboard.putBoolean(kForceFieldEnabledKey, false);
        this.fieldCentricEntry = SmartDashboard.getEntry(kFieldCentricKey);
        addRequirements(swerve);
    }

    public void seedFieldCentric() {
        initialize();
        swerve.seedFieldCentric();
    }

    public void setLockedHeading(Rotation2d heading) {
        lockedHeading = Optional.of(heading);
        currentState = State.DRIVING_WITH_LOCKED_HEADING;
    }

    private void setLockedHeadingToCurrent() {
        final Rotation2d headingInBlueAlliancePerspective = swerve.getState().Pose.getRotation();
        final Rotation2d headingInOperatorPerspective = headingInBlueAlliancePerspective
                .rotateBy(swerve.getOperatorForwardDirection());
        setLockedHeading(headingInOperatorPerspective);
    }

    private void lockHeadingIfRotationStopped(ManualDriveInput input) {
        if (input.hasRotation()) {
            headingLockStopwatch.reset();
            lockedHeading = Optional.empty();
        } else {
            headingLockStopwatch.startIfNotRunning();
            if (headingLockStopwatch.elapsedTime().gt(kHeadingLockDelay)) {
                setLockedHeadingToCurrent();
            }
        }
    }

    @Override
    public void initialize() {
        currentState = State.IDLING;
        lockedHeading = Optional.empty();
        headingLockStopwatch.reset();
        previousInput = new ManualDriveInput();
        lastForceFieldToggleState = false;
    }

    /** Toggles the force field on/off (called from a controller button binding). */
    public void toggleForceField() {
        forceFieldEnabled = !forceFieldEnabled;
        SmartDashboard.putBoolean(kForceFieldEnabledKey, forceFieldEnabled);
    }

    /** Computes force field velocity offsets if enabled, otherwise returns zero. */
    private ForceResult computeForceField() {
        if (!forceFieldEnabled) {
            Logger.recordOutput("ForceField/Enabled", false);
            return ForceResult.ZERO;
        }
        Logger.recordOutput("ForceField/Enabled", true);
        ForceResult result = forceFieldEngine.compute(swerve.getState().Pose);
        Logger.recordOutput("ForceField/NetForceX", result.velocityOffset().getX());
        Logger.recordOutput("ForceField/NetForceY", result.velocityOffset().getY());
        Logger.recordOutput("ForceField/NetTorque", result.angularVelocityOffset());
        Logger.recordOutput("ForceField/CornerForceFL",
                new double[] { result.cornerForces()[0].getX(), result.cornerForces()[0].getY() });
        Logger.recordOutput("ForceField/CornerForceFR",
                new double[] { result.cornerForces()[1].getX(), result.cornerForces()[1].getY() });
        Logger.recordOutput("ForceField/CornerForceBL",
                new double[] { result.cornerForces()[2].getX(), result.cornerForces()[2].getY() });
        Logger.recordOutput("ForceField/CornerForceBR",
                new double[] { result.cornerForces()[3].getX(), result.cornerForces()[3].getY() });
        return result;
    }

    @Override
    public void execute() {
        final ManualDriveInput input = inputSmoother.getSmoothedInput();
        final boolean isFieldCentric = fieldCentricEntry.getBoolean(true);
        final ForceResult forceResult = computeForceField();
        final boolean currentToggleState = forceFieldToggle.getAsBoolean();
        if (currentToggleState && !lastForceFieldToggleState) {
            toggleForceField();
        }
        lastForceFieldToggleState = currentToggleState;

        // Force field velocity offsets (field-frame, m/s)
        final LinearVelocity ffVx = LinearVelocity.ofBaseUnits(forceResult.velocityOffset().getX(), MetersPerSecond);
        final LinearVelocity ffVy = LinearVelocity.ofBaseUnits(forceResult.velocityOffset().getY(), MetersPerSecond);

        // "Field Centric" toggle controls how we interpret the *left stick
        // translation*:
        // - true: joystick translation is field-centric (current behavior)
        // - false: joystick translation is robot-centric (front always front),
        // while ABXY heading-lock still works.
        final LinearVelocity translationSpeed = isFieldCentric ? Driving.kMaxSpeed : Driving.kLimitedSpeed;

        final LinearVelocity joystickVxRobot = translationSpeed.times(input.forward);
        final LinearVelocity joystickVyRobot = translationSpeed.times(input.left);

        final LinearVelocity joystickVxField;
        final LinearVelocity joystickVyField;

        if (isFieldCentric) {
            // When field-centric is enabled, treat the joystick vector as already in
            // field/operator coordinates.
            joystickVxField = joystickVxRobot;
            joystickVyField = joystickVyRobot;
        } else {
            // Convert robot-centric joystick vector into field/operator coordinates using
            // current heading.
            final Rotation2d headingInOperatorPerspective = swerve.getState().Pose.getRotation()
                    .rotateBy(swerve.getOperatorForwardDirection());

            final double cos = headingInOperatorPerspective.getCos();
            final double sin = headingInOperatorPerspective.getSin();

            final double vxRobot = joystickVxRobot.in(MetersPerSecond);
            final double vyRobot = joystickVyRobot.in(MetersPerSecond);

            final double vxFieldBase = vxRobot * cos - vyRobot * sin;
            final double vyFieldBase = vxRobot * sin + vyRobot * cos;

            joystickVxField = LinearVelocity.ofBaseUnits(vxFieldBase, MetersPerSecond);
            joystickVyField = LinearVelocity.ofBaseUnits(vyFieldBase, MetersPerSecond);
        }

        // Heading-lock state machine.
        // With force field, the robot should never truly idle — forces may be acting.
        final boolean hasForce = forceFieldEnabled && forceResult.velocityOffset().getNorm() > 0.01;

        if (input.hasRotation()) {
            currentState = State.DRIVING_WITH_MANUAL_ROTATION;
        } else if (input.hasTranslation() || hasForce) {
            currentState = lockedHeading.isPresent() ? State.DRIVING_WITH_LOCKED_HEADING
                    : State.DRIVING_WITH_MANUAL_ROTATION;
        } else if (previousInput.hasRotation() || previousInput.hasTranslation()) {
            currentState = hasForce ? State.DRIVING_WITH_MANUAL_ROTATION : State.IDLING;
        }
        previousInput = input;

        switch (currentState) {
            case IDLING:
                swerve.setControl(idleRequest);
                break;
            case DRIVING_WITH_MANUAL_ROTATION:
                lockHeadingIfRotationStopped(input);
                swerve.setControl(
                        fieldCentricRequest
                                .withVelocityX(joystickVxField.plus(ffVx))
                                .withVelocityY(joystickVyField.plus(ffVy))
                                .withRotationalRate(Driving.kMaxRotationalRate.times(input.rotation)
                                        .plus(RadiansPerSecond.of(forceResult.angularVelocityOffset()))));
                break;
            case DRIVING_WITH_LOCKED_HEADING:
                swerve.setControl(
                        fieldCentricFacingAngleRequest
                                .withVelocityX(joystickVxField.plus(ffVx))
                                .withVelocityY(joystickVyField.plus(ffVy))
                                .withTargetDirection(lockedHeading.get()));
                break;
        }
    }

    @Override
    public boolean isFinished() {
        // Default drive command: runs until interrupted
        return false;
    }
}
