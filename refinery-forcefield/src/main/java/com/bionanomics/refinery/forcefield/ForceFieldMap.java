package com.bionanomics.refinery.forcefield;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.wpilibj.Filesystem;

/**
 * A collection of {@link Charge} elements that together define the force field layout.
 *
 * <p>Loaded from a JSON file deployed to the roboRIO (or src/main/deploy in simulation).
 * The JSON schema:
 * <pre>{@code
 * {
 *   "name": "Default Field",
 *   "maxForceVelocity": 2.0,
 *   "maxForceTorque": 1.5,
 *   "forceGain": 1.0,
 *   "torqueGain": 0.5,
 *   "charges": [
 *     { "type": "point", "id": "hub", "x": 8.27, "y": 4.1, "strength": 3.0,
 *       "falloff": "gaussian", "sigma": 0.5 },
 *     { "type": "line", "id": "south_wall", "x1": 0, "y1": 0, "x2": 16.54, "y2": 0,
 *       "strength": -2.0, "falloffDistance": 1.0 },
 *     { "type": "radial", "id": "climb_zone", "x": 12.0, "y": 4.0,
 *       "innerRadius": 0.3, "outerRadius": 1.5, "strength": 5.0 }
 *   ]
 * }
 * }</pre>
 */
public class ForceFieldMap {
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String name;
    private final double maxForceVelocity;
    private final double maxForceTorque;
    private final double forceGain;
    private final double torqueGain;
    private final List<Charge> charges;

    public ForceFieldMap(
        String name,
        double maxForceVelocity,
        double maxForceTorque,
        double forceGain,
        double torqueGain,
        List<Charge> charges
    ) {
        this.name = name;
        this.maxForceVelocity = maxForceVelocity;
        this.maxForceTorque = maxForceTorque;
        this.forceGain = forceGain;
        this.torqueGain = torqueGain;
        this.charges = Collections.unmodifiableList(new ArrayList<>(charges));
    }

    /**
     * Computes the net force at a point on the field by summing all charges.
     *
     * @param point Field position in meters
     * @return Net force vector
     */
    public Translation2d getForceAt(Translation2d point) {
        double fx = 0, fy = 0;
        for (Charge charge : charges) {
            Translation2d f = charge.evaluate(point);
            fx += f.getX();
            fy += f.getY();
        }
        return new Translation2d(fx, fy);
    }

    /** Loads a force field map from the deploy directory by preset name. */
    public static ForceFieldMap loadFromDeploy(String presetName) {
        Path path = Filesystem.getDeployDirectory().toPath()
            .resolve("forcefield")
            .resolve(presetName + ".json");
        try {
            String json = Files.readString(path);
            return fromJson(json);
        } catch (IOException e) {
            System.err.println("[ForceField] Failed to load preset '"
                + presetName + "': " + e.getMessage());
            return empty();
        }
    }

    /** Lists available preset names from the deploy/forcefield directory. */
    public static List<String> listPresets() {
        File dir = Filesystem.getDeployDirectory().toPath()
            .resolve("forcefield").toFile();
        List<String> presets = new ArrayList<>();
        if (dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    String fname = f.getName();
                    presets.add(fname.substring(0, fname.length() - ".json".length()));
                }
            }
        }
        return presets;
    }

    /** Parses a ForceFieldMap from a JSON string. */
    public static ForceFieldMap fromJson(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            String name = root.has("name") ? root.get("name").asText() : "Unnamed";
            double maxVel = root.has("maxForceVelocity")
                ? root.get("maxForceVelocity").asDouble() : 2.0;
            double maxTorque = root.has("maxForceTorque")
                ? root.get("maxForceTorque").asDouble() : 1.5;
            double fGain = root.has("forceGain")
                ? root.get("forceGain").asDouble() : 1.0;
            double tGain = root.has("torqueGain")
                ? root.get("torqueGain").asDouble() : 0.5;

            List<Charge> charges = new ArrayList<>();
            if (root.has("charges")) {
                for (JsonNode node : root.get("charges")) {
                    Charge charge = parseCharge(node);
                    if (charge != null) {
                        charges.add(charge);
                    }
                }
            }
            return new ForceFieldMap(name, maxVel, maxTorque, fGain, tGain, charges);
        } catch (Exception e) {
            System.err.println("[ForceField] JSON parse error: " + e.getMessage());
            return empty();
        }
    }

    private static Charge parseCharge(JsonNode node) {
        String type = node.get("type").asText();
        String id = node.has("id")
            ? node.get("id").asText() : type + "_" + node.hashCode();

        return switch (type) {
            case "point" -> new PointCharge(
                id,
                new Translation2d(node.get("x").asDouble(), node.get("y").asDouble()),
                node.get("strength").asDouble(),
                node.has("falloff")
                    ? FalloffType.fromString(node.get("falloff").asText())
                    : FalloffType.INVERSE_SQUARE,
                node.has("sigma") ? node.get("sigma").asDouble() : 0.5
            );
            case "line" -> new LineCharge(
                id,
                new Translation2d(node.get("x1").asDouble(), node.get("y1").asDouble()),
                new Translation2d(node.get("x2").asDouble(), node.get("y2").asDouble()),
                node.get("strength").asDouble(),
                node.has("falloffDistance") ? node.get("falloffDistance").asDouble() : 1.0
            );
            case "radial" -> new RadialZone(
                id,
                new Translation2d(node.get("x").asDouble(), node.get("y").asDouble()),
                node.has("innerRadius") ? node.get("innerRadius").asDouble() : 0.3,
                node.has("outerRadius") ? node.get("outerRadius").asDouble() : 1.5,
                node.get("strength").asDouble()
            );
            default -> {
                System.err.println("[ForceField] Unknown charge type: " + type);
                yield null;
            }
        };
    }

    /** Returns an empty field map with no charges. */
    public static ForceFieldMap empty() {
        return new ForceFieldMap("Empty", 2.0, 1.5, 1.0, 0.5, List.of());
    }

    public String name() { return name; }
    public double maxForceVelocity() { return maxForceVelocity; }
    public double maxForceTorque() { return maxForceTorque; }
    public double forceGain() { return forceGain; }
    public double torqueGain() { return torqueGain; }
    public List<Charge> charges() { return charges; }
}
