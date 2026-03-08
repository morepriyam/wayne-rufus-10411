# Limelight Configuration

## 2026 AprilTag Field Layouts

Two variants are available depending on which field perimeter your event uses:

| File | Field Type |
|---|---|
| `2026-rebuilt-andymark.json` | AndyMark (standard competition) |
| `2026-rebuilt-welded.json` | Welded (some district/offseason events) |

## How to Upload to the Limelight

1. Connect to the Limelight's web UI (default: `http://limelight.local:5801` or `http://10.104.11.11:5801`)
2. Go to **Settings → AprilTag Field Map**
3. Upload the appropriate JSON file for your event
4. Confirm the field map is active on the AprilTag pipeline

## Notes

- Use **welded** for most official FRC district and regional events.
- Use **andymark** if your event specifies it (check the event's field setup notes).
- The Limelight must also have its **camera pose** (mount position/angle on the robot) calibrated in the web UI for accurate MegaTag pose estimates.
