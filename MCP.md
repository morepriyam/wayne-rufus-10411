# MCP Server — AI Assistant Integration

The robot runs an embedded [MCP](https://modelcontextprotocol.io/) (Model Context Protocol) server that exposes read-only robot state to AI assistants like GitHub Copilot and Claude. This lets you ask your AI assistant questions about robot status, battery voltage, match info, CAN bus health, and NetworkTables values — all in real time.

## Enabling the MCP Server

The MCP server is **on by default**. Toggle it off at runtime:

1. Open **Shuffleboard** (or Elastic) while connected to the robot.
2. Find the **"MCP Server"** toggle on the **Robot** tab.
3. Set it to **true** to start the server, **false** to stop it.

The server runs on **port 8765**. When running in simulation, it listens on `localhost:8765`. On the real robot, it listens on `10.104.11.2:8765`.

## VS Code / GitHub Copilot Setup

The project includes a [`.vscode/mcp.json`](.vscode/mcp.json) file with two server entries pre-configured:

| Entry | URL | When to use |
|---|---|---|
| `roborio-mcp-sim` | `http://localhost:8765/mcp` | Robot simulation on your laptop |
| `roborio-mcp` | `http://10.104.11.2:8765/mcp` | Real robot over the robot network |

VS Code will show these servers in the MCP panel. Once the MCP server is enabled on the robot (or sim), tools will appear in Copilot Chat (Agent mode).

### If your team number is different

Edit `.vscode/mcp.json` and replace `10.104.11.2` with your roboRIO's IP (`10.TE.AM.2`).

## Claude Desktop Setup

Edit `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS) or `%APPDATA%\Claude\claude_desktop_config.json` (Windows):

```json
{
  "mcpServers": {
    "roborio": {
      "url": "http://localhost:8765/mcp"
    }
  }
}
```

Replace `localhost` with `10.104.11.2` for a real robot connection.

## Available Tools

All tools are **read-only** — the MCP server cannot control the robot.

| Tool | Description |
|---|---|
| `get_robot_status` | Enabled state, operating mode (Teleop/Auto/Test/Disabled), e-stop, battery voltage, brownout, system active |
| `get_battery_voltage` | Battery voltage and input voltage as numeric values |
| `get_match_info` | Alliance color/station, match type/number, game-specific message, remaining match time, FMS attached |
| `get_robot_stats` | CAN bus utilization and error counts, 3.3V/5V/6V rail voltages, currents, fault counts, input voltage |
| `get_connection_info` | Driver Station attached, FMS attached |
| `get_subsystems` | Registered command-based subsystems and scheduler info |
| `get_networktables` | Browse the NetworkTables tree one level at a time. Pass `{"path": "/SmartDashboard"}` to navigate deeper |

### Example: Browsing NetworkTables

Ask your AI assistant:
> "What's in the SmartDashboard NetworkTables?"

It will call `get_networktables` with `path: /SmartDashboard` and return the subtables and values at that level. You can then ask it to go deeper into any subtable.

### Example: Quick diagnostics

> "Is the robot brownedOut? What's the CAN bus utilization?"

The assistant calls `get_robot_status` and `get_robot_stats` and summarizes the results.

## Testing with curl

You can test the MCP server directly:

```bash
# List available tools
curl -s -X POST http://localhost:8765/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | python3 -m json.tool

# Get robot status
curl -s -X POST http://localhost:8765/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_robot_status","arguments":{}}}' | python3 -m json.tool

# Browse NetworkTables root
curl -s -X POST http://localhost:8765/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_networktables","arguments":{"path":"/"}}}' | python3 -m json.tool
```

## Architecture

The MCP server is provided by the [`refinery-roborio-mcp`](refinery-roborio-mcp/) library, included as a Gradle composite build. It has zero external dependencies — all JSON handling is built in.

See [refinery-roborio-mcp/README.md](refinery-roborio-mcp/README.md) for library details, vendordep installation for other teams, and the full API reference.
