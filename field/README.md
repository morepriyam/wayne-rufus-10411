# Field images for Sim / Glass

Put your field image and calibration JSON **in this folder** (same directory).

- **PNG** – field background image (e.g. `2026-rebuilt-welded.png`)
- **JSON** – field calibration (e.g. `2026-rebuilt-welded.json`)

Then in the Sim GUI or Glass: Field2d widget → right‑click → **Image** → **Custom** → **Choose JSON/image…** and select the `.json` file from this folder.

### Why doesn’t the custom field stay saved?

- The Sim GUI stores layout in `imgui.ini` in the **working directory** of the sim process. If you run sim from different places (e.g. terminal vs IDE “Simulate”), that directory can change, so the saved layout (and sometimes the custom image path) isn’t found next time.
- Some builds of the Sim GUI don’t persist the **Custom** file path—only the dropdown choice (e.g. a built‑in field). So you may need to pick **Custom** → **Choose JSON/image…** again each run.

**Tip:** Always start sim the same way (e.g. `./gradlew simulateJava` from the project root). If the custom field still resets, re-select `field/2026-rebuilt.json` once after opening the Sim—it’s a known GUI limitation.
