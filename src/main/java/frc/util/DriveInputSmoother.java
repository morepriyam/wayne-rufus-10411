package frc.util;

import java.util.function.DoubleSupplier;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.Vector;
import edu.wpi.first.math.numbers.N2;

public class DriveInputSmoother {
    /** Left-stick translation: smaller deadband + gentler curve → more sensitive mid-stick. */
    private static final double kTranslationDeadband = 0.08;
    private static final double kTranslationCurveExponent = 1.15;
    /** Right-stick rotation: unchanged feel vs. prior single deadband/curve. */
    private static final double kRotationDeadband = 0.15;
    private static final double kRotationCurveExponent = 1.5;

    private final DoubleSupplier forwardInput;
    private final DoubleSupplier leftInput;
    private final DoubleSupplier rotationInput;

    public DriveInputSmoother(DoubleSupplier forwardInput, DoubleSupplier leftInput, DoubleSupplier rotationInput) {
        this.forwardInput = forwardInput;
        this.leftInput = leftInput;
        this.rotationInput = rotationInput;
    }

    public DriveInputSmoother(DoubleSupplier forwardInput, DoubleSupplier leftInput) {
        this(forwardInput, leftInput, () -> 0);
    }

    public ManualDriveInput getSmoothedInput() {
        final Vector<N2> rawTranslationInput = VecBuilder.fill(forwardInput.getAsDouble(), leftInput.getAsDouble());
        final Vector<N2> deadbandedTranslationInput = MathUtil.applyDeadband(rawTranslationInput, kTranslationDeadband);
        final Vector<N2> curvedTranslationInput = MathUtil.copyDirectionPow(deadbandedTranslationInput,
                kTranslationCurveExponent);

        final double rawRotationInput = rotationInput.getAsDouble();
        final double deadbandedRotationInput = MathUtil.applyDeadband(rawRotationInput, kRotationDeadband);
        final double curvedRotationInput = MathUtil.copyDirectionPow(deadbandedRotationInput, kRotationCurveExponent);

        return new ManualDriveInput(
                curvedTranslationInput.get(0),
                curvedTranslationInput.get(1),
                curvedRotationInput);
    }
}
