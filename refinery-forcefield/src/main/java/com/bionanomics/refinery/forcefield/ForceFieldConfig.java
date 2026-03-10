package com.bionanomics.refinery.forcefield;

import java.util.List;

import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

/**
 * Manages force field preset selection, loading, and live NetworkTables parameter tuning.
 *
 * <p>On construction, scans the deploy/forcefield/ directory for JSON presets and
 * publishes a chooser to SmartDashboard. The active map can be switched at any time.
 *
 * <p>Also publishes the active preset's gains to SmartDashboard so they can be
 * adjusted live via Elastic / Shuffleboard / OutlineViewer without redeploying.
 */
public class ForceFieldConfig {
    private static final String kChooserKey = "ForceField/Preset";
    private static final String kForceGainKey = "ForceField/ForceGain";
    private static final String kTorqueGainKey = "ForceField/TorqueGain";
    private static final String kMaxVelocityKey = "ForceField/MaxVelocity";
    private static final String kMaxTorqueKey = "ForceField/MaxTorque";

    private final SendableChooser<String> presetChooser = new SendableChooser<>();
    private final ForceFieldEngine engine;

    private String activePresetName = "";

    /**
     * @param engine        The force field engine whose map will be swapped on preset changes
     * @param defaultPreset Name of the default preset (without .json extension)
     */
    public ForceFieldConfig(ForceFieldEngine engine, String defaultPreset) {
        this.engine = engine;

        List<String> presets = ForceFieldMap.listPresets();
        if (presets.isEmpty()) {
            System.out.println("[ForceField] No presets found in deploy/forcefield/");
            presetChooser.setDefaultOption("(none)", "");
        } else {
            for (String preset : presets) {
                if (preset.equals(defaultPreset)) {
                    presetChooser.setDefaultOption(preset, preset);
                } else {
                    presetChooser.addOption(preset, preset);
                }
            }
        }
        SmartDashboard.putData(kChooserKey, presetChooser);

        loadPreset(defaultPreset);

        ForceFieldMap map = engine.getMap();
        SmartDashboard.putNumber(kForceGainKey, map.forceGain());
        SmartDashboard.putNumber(kTorqueGainKey, map.torqueGain());
        SmartDashboard.putNumber(kMaxVelocityKey, map.maxForceVelocity());
        SmartDashboard.putNumber(kMaxTorqueKey, map.maxForceTorque());
    }

    /**
     * Call periodically (e.g., in robotPeriodic) to detect preset changes and apply
     * live-tuned gains.
     */
    public void update() {
        String selected = presetChooser.getSelected();
        if (selected != null && !selected.equals(activePresetName) && !selected.isEmpty()) {
            loadPreset(selected);
        }

        ForceFieldMap current = engine.getMap();
        double forceGain = SmartDashboard.getNumber(kForceGainKey, current.forceGain());
        double torqueGain = SmartDashboard.getNumber(kTorqueGainKey, current.torqueGain());
        double maxVel = SmartDashboard.getNumber(kMaxVelocityKey, current.maxForceVelocity());
        double maxTorque = SmartDashboard.getNumber(kMaxTorqueKey, current.maxForceTorque());

        if (forceGain != current.forceGain()
            || torqueGain != current.torqueGain()
            || maxVel != current.maxForceVelocity()
            || maxTorque != current.maxForceTorque()
        ) {
            engine.setMap(new ForceFieldMap(
                current.name(), maxVel, maxTorque,
                forceGain, torqueGain, current.charges()
            ));
        }
    }

    /** Returns the name of the currently active preset. */
    public String activePresetName() {
        return activePresetName;
    }

    private void loadPreset(String presetName) {
        ForceFieldMap map = ForceFieldMap.loadFromDeploy(presetName);
        engine.setMap(map);
        activePresetName = presetName;

        SmartDashboard.putNumber(kForceGainKey, map.forceGain());
        SmartDashboard.putNumber(kTorqueGainKey, map.torqueGain());
        SmartDashboard.putNumber(kMaxVelocityKey, map.maxForceVelocity());
        SmartDashboard.putNumber(kMaxTorqueKey, map.maxForceTorque());

        System.out.println("[ForceField] Loaded preset: " + presetName
            + " (" + map.charges().size() + " charges)");
    }
}
