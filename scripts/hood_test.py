#!/usr/bin/env python3
"""
Hood servo sweep test.
Moves both linear actuators through key positions and reports actual vs target.
Usage: python3 scripts/hood_test.py
"""

import ntcore
import time

ROBOT_IP = "10.104.11.2"

POSITIONS = [
    (0.01, "MIN   - fully retracted"),
    (0.19, "0.19  - close-range shot (~52 in)"),
    (0.40, "0.40  - mid-range shot (~114 in)"),
    (0.48, "0.48  - far-range shot (~166 in)"),
    (0.77, "MAX   - fully extended"),
    (0.40, "0.40  - park at mid"),
]

SETTLE_TIME = 6.0  # seconds — servo moves at 20mm/s over 100mm max = 5s

def main():
    nt = ntcore.NetworkTableInstance.getDefault()
    nt.startClient4("hood-sweep-test")
    nt.setServer(ROBOT_IP)

    hood = nt.getTable("SmartDashboard").getSubTable("Hood")
    target_pub = hood.getDoubleTopic("Target Position").publish()
    current_sub = hood.getDoubleTopic("Current Position").subscribe(-1.0)

    print(f"Connecting to {ROBOT_IP}...")
    time.sleep(1.5)

    print(f"\n{'':>4} {'Target':>8}  {'Actual':>8}  {'Diff':>6}  Label")
    print("-" * 55)

    for pos, label in POSITIONS:
        target_pub.set(pos)
        time.sleep(SETTLE_TIME)
        actual = current_sub.get()
        diff = actual - pos
        ok = "✅" if abs(diff) < 0.05 else "❌"
        print(f"{ok}  {pos:>8.2f}  {actual:>8.3f}  {diff:>+6.3f}  {label}")

    print("\nSweep complete. Both actuators parked at 0.40.")
    nt.stopClient()

if __name__ == "__main__":
    main()
