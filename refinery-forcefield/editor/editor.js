/* ── Force Field Editor — Main Logic ───────────────────────────── */
"use strict";

// ── Field dimensions (meters) ─────────────────────────────────
// Dynamic — updated when the user switches field year via the dropdown.
// Derived from WPILib's field-size calibration (feet → meters).
let FIELD_W = 16.541;   // default: 2026 REBUILT
let FIELD_H = 8.069;

// ── FRC Field Images ──────────────────────────────────────────
// To add a new season:
//   1. Drop a WPILib field JSON + field PNG into editor/fields/
//   2. Add the JSON filename to editor/fields/manifest.json
//
// The editor fetches fields/manifest.json at startup, loads each
// referenced JSON, and builds the field list dynamically. Calibration
// data (field-corners, field-size) comes from the WPILib JSON —
// no need to duplicate it here.
const FT_TO_M = 0.3048;
let FIELDS = [];       // populated by loadFieldManifest()

/** Fetch manifest → fetch each WPILib JSON → populate FIELDS array. */
async function loadFieldManifest() {
    const resp = await fetch("fields/manifest.json");
    if (!resp.ok) throw new Error(`Failed to load fields/manifest.json (${resp.status})`);
    const filenames = await resp.json();  // e.g. ["2026-rebuilt.json", ...]

    const entries = await Promise.all(filenames.map(async (name) => {
        try {
            const r = await fetch(`fields/${name}`);
            if (!r.ok) { console.warn(`Skipping ${name}: HTTP ${r.status}`); return null; }
            const j = await r.json();
            const yearMatch = name.match(/^(\d{4})/);
            return {
                year:         yearMatch ? parseInt(yearMatch[1]) : 0,
                game:         j["game"] || name,
                imageUrl:     `fields/${j["field-image"]}`,
                fieldCorners: {
                    topLeft:     j["field-corners"]["top-left"],
                    bottomRight: j["field-corners"]["bottom-right"],
                },
                fieldSizeFt:  j["field-size"],
            };
        } catch (err) {
            console.warn(`Skipping ${name}:`, err);
            return null;
        }
    }));
    FIELDS = entries.filter(Boolean);
}

let currentField = null;
let fieldImage = null;       // loaded Image element (or null)
let fieldImageReady = false; // true once image has loaded

// Robot dimensions (meters) — configurable from the sidebar
let robotWidth  = 0.552;  // default 2 × 0.276 (~21.75")
let robotHeight = 0.514;  // default 2 × 0.257 (~20.25")
let ROBOT_CORNERS = [];
let ROBOT_W = robotWidth;
let ROBOT_H = robotHeight;

function updateRobotSize(w, h) {
    robotWidth  = w;
    robotHeight = h;
    ROBOT_W = w;
    ROBOT_H = h;
    const hw = w / 2, hh = h / 2;
    ROBOT_CORNERS = [
        { x:  hw, y:  hh },  // FL
        { x:  hw, y: -hh },  // FR
        { x: -hw, y:  hh },  // BL
        { x: -hw, y: -hh },  // BR
    ];
}
updateRobotSize(robotWidth, robotHeight);  // initialize

// Charge colors — green = attractor, yellow = repulsor (avoids FRC red/blue alliance confusion)
const COLOR_ATTRACT = "166,227,161";   // #a6e3a1  (Catppuccin green)
const COLOR_REPULSE = "249,226,175";   // #f9e2af  (Catppuccin yellow)
const HEX_ATTRACT   = "#a6e3a1";
const HEX_REPULSE   = "#f9e2af";

// ── State ─────────────────────────────────────────────────────
let charges = [];
let selectedIdx = -1;       // primary selection (for props panel & drag handles)
let selectedSet = new Set(); // all selected indices (for highlighting & bulk ops)
let mode = "select";    // select | point | line | radial
let vizMode = "arrows"; // arrows | heatmap | none
let showRobotPreview = true;
let mouseFieldX = 0, mouseFieldY = 0;
let lineDragStart = null;  // { x, y } when dragging to create a line

// Drag-to-move state
let isDragging = false;
let dragTarget = null;  // { index, handle } — handle: 'center' | 'p1' | 'p2' | 'mid'
let dragOffset = { x: 0, y: 0 };  // offset from handle to click point

// ── Undo / Redo ───────────────────────────────────────────────
const MAX_HISTORY = 100;
let undoStack = [];   // past snapshots
let redoStack = [];   // future snapshots (cleared on new mutations)

/** Deep-clone the charges array + selection into a snapshot. */
function takeSnapshot() {
    return { charges: JSON.parse(JSON.stringify(charges)), selectedIdx, selectedSet: new Set(selectedSet) };
}

/** Save current state before a mutation. Clears the redo stack. */
function pushUndo() {
    undoStack.push(takeSnapshot());
    if (undoStack.length > MAX_HISTORY) undoStack.shift();
    redoStack = [];
    updateUndoRedoButtons();
}

/** Restore a snapshot into live state. */
function restoreSnapshot(snap) {
    charges = snap.charges;
    selectedIdx = snap.selectedIdx;
    selectedSet = snap.selectedSet ? new Set(snap.selectedSet) : new Set(selectedIdx >= 0 ? [selectedIdx] : []);
}

/** Select a single charge (clears previous selection). */
function selectSingle(idx) {
    selectedIdx = idx;
    selectedSet.clear();
    if (idx >= 0) selectedSet.add(idx);
}

/** Toggle an index in/out of the selection set (shift‑click). */
function toggleSelect(idx) {
    if (idx < 0) return;
    if (selectedSet.has(idx)) {
        selectedSet.delete(idx);
        // Update primary: pick another member or -1
        selectedIdx = selectedSet.size > 0 ? [...selectedSet][selectedSet.size - 1] : -1;
    } else {
        selectedSet.add(idx);
        selectedIdx = idx;
    }
}

/** Clear all selection state. */
function clearSelection() {
    selectedIdx = -1;
    selectedSet.clear();
}

function undo() {
    if (undoStack.length === 0) return;
    redoStack.push(takeSnapshot());
    restoreSnapshot(undoStack.pop());
    refreshUI(); draw();
    updateUndoRedoButtons();
}

function redo() {
    if (redoStack.length === 0) return;
    undoStack.push(takeSnapshot());
    restoreSnapshot(redoStack.pop());
    refreshUI(); draw();
    updateUndoRedoButtons();
}

function updateUndoRedoButtons() {
    const btnUndo = document.getElementById("btn-undo");
    const btnRedo = document.getElementById("btn-redo");
    if (btnUndo) btnUndo.disabled = undoStack.length === 0;
    if (btnRedo) btnRedo.disabled = redoStack.length === 0;
}

// Map-level settings
let mapSettings = {
    name: "Default Field",
    forceGain: 1.0,
    torqueGain: 0.5,
    maxForceVelocity: 2.0,
    maxForceTorque: 1.5,
};

// ── LocalStorage persistence ──────────────────────────────────
const LS_KEY = "forcefield_editor_state";

function saveToLocalStorage() {
    try {
        const state = { charges, mapSettings, robotWidth, robotHeight };
        localStorage.setItem(LS_KEY, JSON.stringify(state));
    } catch (_) { /* quota exceeded or private browsing — ignore */ }
}

function loadFromLocalStorage() {
    try {
        const raw = localStorage.getItem(LS_KEY);
        if (!raw) return false;
        const state = JSON.parse(raw);
        if (Array.isArray(state.charges)) charges = state.charges;
        if (state.mapSettings) Object.assign(mapSettings, state.mapSettings);
        if (state.robotWidth != null && state.robotHeight != null) {
            updateRobotSize(state.robotWidth, state.robotHeight);
        }
        return true;
    } catch (_) { return false; }
}

function clearLocalStorage() {
    localStorage.removeItem(LS_KEY);
}

// ── Field image loading ───────────────────────────────────────
function loadFieldImage(field) {
    currentField = field;
    // Update field dimensions from calibration data
    if (field.fieldSizeFt) {
        FIELD_W = +(field.fieldSizeFt[0] * FT_TO_M).toFixed(3);
        FIELD_H = +(field.fieldSizeFt[1] * FT_TO_M).toFixed(3);
    }
    // Update toolbar info text
    const infoEl = document.getElementById("field-info");
    if (infoEl) {
        infoEl.textContent = `${FIELD_W.toFixed(2)}m × ${FIELD_H.toFixed(2)}m  |  Blue alliance origin (bottom-left)`;
    }
    // Toggle obstacle button availability
    updateObstaclesButton();
    fieldImageReady = false;
    fieldImage = new Image();
    fieldImage.onload = () => {
        fieldImageReady = true;
        draw();
    };
    fieldImage.onerror = () => {
        console.warn(`Field image not found: ${field.imageUrl} — using plain field`);
        fieldImageReady = false;
        fieldImage = null;
        draw();
    };
    fieldImage.src = field.imageUrl;
}

function populateFieldSelector() {
    const sel = document.getElementById("field-select");
    sel.innerHTML = "";
    FIELDS.forEach((f, i) => {
        const opt = document.createElement("option");
        opt.value = i;
        opt.textContent = `${f.year} ${f.game}`;
        sel.appendChild(opt);
    });
    sel.value = 0;
    sel.addEventListener("change", () => {
        loadFieldImage(FIELDS[parseInt(sel.value)]);
    });
}

// ── Canvas setup ──────────────────────────────────────────────
const canvas = document.getElementById("field-canvas");
const ctx = canvas.getContext("2d");
const container = document.getElementById("canvas-container");

let scale = 1;   // pixels per meter
let offsetX = 0, offsetY = 0;

function resize() {
    canvas.width = container.clientWidth;
    canvas.height = container.clientHeight;
    fitToWindow();
    draw();
}
window.addEventListener("resize", resize);

function fitToWindow() {
    const padPx = 40;
    const availW = canvas.width - padPx * 2;
    const availH = canvas.height - padPx * 2;
    scale = Math.min(availW / FIELD_W, availH / FIELD_H);
    offsetX = (canvas.width - FIELD_W * scale) / 2;
    offsetY = (canvas.height - FIELD_H * scale) / 2;
}

// Coordinate transforms
function fieldToCanvas(fx, fy) {
    return { x: offsetX + fx * scale, y: offsetY + (FIELD_H - fy) * scale };
}
function canvasToField(cx, cy) {
    return { x: (cx - offsetX) / scale, y: FIELD_H - (cy - offsetY) / scale };
}

// ── Drawing ───────────────────────────────────────────────────
function draw() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    drawField();
    if (vizMode === "arrows") drawForceArrows();
    if (vizMode === "heatmap") drawHeatmap();
    drawCharges();
    if (showRobotPreview) drawRobotPreview();
    if (lineDragStart) drawLineDragPreview();
    updateStatus();
}

function drawField() {
    const tl = fieldToCanvas(0, FIELD_H);
    const br = fieldToCanvas(FIELD_W, 0);
    const fw = br.x - tl.x;
    const fh = br.y - tl.y;

    // Field background
    ctx.fillStyle = "#2a2a3a";
    ctx.fillRect(tl.x, tl.y, fw, fh);

    // Field image (if loaded) — calibrated via WPILib field-corners metadata
    if (fieldImageReady && fieldImage && currentField.fieldCorners) {
        const fc = currentField.fieldCorners;
        const fsz = currentField.fieldSizeFt;
        // Pixels-per-meter within the calibrated region of the image
        const imgFieldW_m = fsz[0] * FT_TO_M;
        const imgFieldH_m = fsz[1] * FT_TO_M;
        const cornersPxW = fc.bottomRight[0] - fc.topLeft[0];
        const cornersPxH = fc.bottomRight[1] - fc.topLeft[1];
        const ppm_x = cornersPxW / imgFieldW_m;  // image px per meter (x)
        const ppm_y = cornersPxH / imgFieldH_m;  // image px per meter (y)
        // Source rectangle in image pixels that maps to [0,0]→[FIELD_W, FIELD_H]
        const sx = fc.topLeft[0];
        const sy = fc.topLeft[1];
        const sw = FIELD_W * ppm_x;
        const sh = FIELD_H * ppm_y;
        ctx.save();
        ctx.globalAlpha = 0.65;
        ctx.drawImage(fieldImage, sx, sy, sw, sh, tl.x, tl.y, fw, fh);
        ctx.restore();
    } else if (fieldImageReady && fieldImage) {
        // Fallback: no calibration data — stretch entire image
        ctx.save();
        ctx.globalAlpha = 0.65;
        ctx.drawImage(fieldImage, tl.x, tl.y, fw, fh);
        ctx.restore();
    }

    // Border
    ctx.strokeStyle = "#585b70";
    ctx.lineWidth = 2;
    ctx.strokeRect(tl.x, tl.y, fw, fh);
    // Center line
    const cl = fieldToCanvas(FIELD_W / 2, 0);
    const ct = fieldToCanvas(FIELD_W / 2, FIELD_H);
    ctx.strokeStyle = "#45475a";
    ctx.lineWidth = 1;
    ctx.setLineDash([8, 6]);
    ctx.beginPath();
    ctx.moveTo(cl.x, cl.y);
    ctx.lineTo(ct.x, ct.y);
    ctx.stroke();
    ctx.setLineDash([]);
    // Grid (1m)
    ctx.strokeStyle = "#31324433";
    ctx.lineWidth = 0.5;
    for (let x = 1; x < FIELD_W; x++) {
        const p = fieldToCanvas(x, 0);
        const q = fieldToCanvas(x, FIELD_H);
        ctx.beginPath(); ctx.moveTo(p.x, p.y); ctx.lineTo(q.x, q.y); ctx.stroke();
    }
    for (let y = 1; y < FIELD_H; y++) {
        const p = fieldToCanvas(0, y);
        const q = fieldToCanvas(FIELD_W, y);
        ctx.beginPath(); ctx.moveTo(p.x, p.y); ctx.lineTo(q.x, q.y); ctx.stroke();
    }
    // Alliance labels (only when no field image, to avoid clutter)
    if (!fieldImageReady) {
        ctx.font = "bold 14px sans-serif";
        ctx.fillStyle = "#89b4fa66";
        ctx.textAlign = "center";
        const blueLabel = fieldToCanvas(1.5, FIELD_H / 2);
        ctx.fillText("BLUE", blueLabel.x, blueLabel.y);
        ctx.fillStyle = "#f38ba866";
        const redLabel = fieldToCanvas(FIELD_W - 1.5, FIELD_H / 2);
        ctx.fillText("RED", redLabel.x, redLabel.y);
    }
}

function drawCharges() {
    charges.forEach((c, i) => {
        const isSel = selectedSet.has(i);
        const alpha = isSel ? 1.0 : 0.7;

        if (c.type === "point") {
            const p = fieldToCanvas(c.x, c.y);
            const sigma = c.sigma || 0.5;
            const color = c.strength >= 0 ? `rgba(${COLOR_ATTRACT},${alpha})` : `rgba(${COLOR_REPULSE},${alpha})`;
            const colorBase = c.strength >= 0 ? `rgba(${COLOR_ATTRACT},` : `rgba(${COLOR_REPULSE},`;
            // Sigma radius circle
            ctx.beginPath();
            ctx.arc(p.x, p.y, sigma * scale, 0, Math.PI * 2);
            ctx.fillStyle = colorBase + "0.06)";
            ctx.fill();
            ctx.strokeStyle = colorBase + (isSel ? "0.5)" : "0.2)");
            ctx.lineWidth = isSel ? 1.5 : 1;
            ctx.setLineDash([4, 3]);
            ctx.stroke();
            ctx.setLineDash([]);
            // Center dot
            ctx.beginPath();
            ctx.arc(p.x, p.y, isSel ? 10 : 7, 0, Math.PI * 2);
            ctx.fillStyle = color;
            ctx.fill();
            if (isSel) {
                ctx.strokeStyle = "#cba6f7"; ctx.lineWidth = 2; ctx.stroke();
                // Drag handle on sigma radius (right edge)
                const srx = p.x + sigma * scale;
                ctx.beginPath(); ctx.arc(srx, p.y, 6, 0, Math.PI * 2);
                ctx.fillStyle = "rgba(249,226,175,0.35)"; ctx.fill();
                ctx.strokeStyle = "#f9e2af"; ctx.lineWidth = 2; ctx.stroke();
            }
            // Label
            ctx.font = "11px sans-serif";
            ctx.fillStyle = "#cdd6f4";
            ctx.textAlign = "center";
            ctx.fillText(c.id, p.x, p.y - Math.max(14, sigma * scale + 8));
        } else if (c.type === "line") {
            const p1 = fieldToCanvas(c.x1, c.y1);
            const p2 = fieldToCanvas(c.x2, c.y2);
            const color = c.strength >= 0 ? `rgba(${COLOR_ATTRACT},${alpha})` : `rgba(${COLOR_REPULSE},${alpha})`;
            ctx.strokeStyle = color;
            ctx.lineWidth = isSel ? 4 : 2.5;
            ctx.beginPath();
            ctx.moveTo(p1.x, p1.y);
            ctx.lineTo(p2.x, p2.y);
            ctx.stroke();
            // Endpoints
            [p1, p2].forEach(p => {
                ctx.beginPath(); ctx.arc(p.x, p.y, 4, 0, Math.PI * 2);
                ctx.fillStyle = color; ctx.fill();
            });
            if (isSel) {
                // Drag handles at endpoints
                ctx.lineWidth = 2;
                [p1, p2].forEach(p => {
                    ctx.beginPath(); ctx.arc(p.x, p.y, 7, 0, Math.PI * 2);
                    ctx.fillStyle = "rgba(203,166,247,0.3)"; ctx.fill();
                    ctx.strokeStyle = "#cba6f7"; ctx.stroke();
                });
                // Drag handle at midpoint
                const mid = { x: (p1.x + p2.x) / 2, y: (p1.y + p2.y) / 2 };
                ctx.beginPath(); ctx.arc(mid.x, mid.y, 6, 0, Math.PI * 2);
                ctx.fillStyle = "rgba(203,166,247,0.25)"; ctx.fill();
                ctx.strokeStyle = "#cba6f7"; ctx.setLineDash([3, 2]);
                ctx.stroke(); ctx.setLineDash([]);
            }
            // Label at midpoint
            const mx = (p1.x + p2.x) / 2, my = (p1.y + p2.y) / 2;
            ctx.font = "11px sans-serif"; ctx.fillStyle = "#cdd6f4"; ctx.textAlign = "center";
            ctx.fillText(c.id, mx, my - 10);
        } else if (c.type === "radial") {
            const p = fieldToCanvas(c.x, c.y);
            const color = c.strength >= 0 ? `rgba(${COLOR_ATTRACT},` : `rgba(${COLOR_REPULSE},`;
            // Outer radius
            ctx.beginPath();
            ctx.arc(p.x, p.y, c.outerRadius * scale, 0, Math.PI * 2);
            ctx.fillStyle = color + "0.08)";
            ctx.fill();
            ctx.strokeStyle = color + (isSel ? "0.8)" : "0.4)");
            ctx.lineWidth = isSel ? 2 : 1;
            ctx.stroke();
            // Inner radius
            ctx.beginPath();
            ctx.arc(p.x, p.y, c.innerRadius * scale, 0, Math.PI * 2);
            ctx.fillStyle = color + "0.15)";
            ctx.fill();
            ctx.strokeStyle = color + "0.6)";
            ctx.setLineDash([4, 3]);
            ctx.stroke();
            ctx.setLineDash([]);
            // Center dot
            ctx.beginPath(); ctx.arc(p.x, p.y, 4, 0, Math.PI * 2);
            ctx.fillStyle = color + `${alpha})`; ctx.fill();
            if (isSel) {
                // Drag handle on center
                ctx.beginPath(); ctx.arc(p.x, p.y, 7, 0, Math.PI * 2);
                ctx.fillStyle = "rgba(203,166,247,0.3)"; ctx.fill();
                ctx.strokeStyle = "#cba6f7"; ctx.lineWidth = 2; ctx.stroke();
                // Drag handle on outer radius (right edge)
                const orx = p.x + c.outerRadius * scale;
                ctx.beginPath(); ctx.arc(orx, p.y, 6, 0, Math.PI * 2);
                ctx.fillStyle = "rgba(249,226,175,0.35)"; ctx.fill();
                ctx.strokeStyle = "#f9e2af"; ctx.lineWidth = 2; ctx.stroke();
                // Drag handle on inner radius (right edge)
                const irx = p.x + c.innerRadius * scale;
                ctx.beginPath(); ctx.arc(irx, p.y, 5, 0, Math.PI * 2);
                ctx.fillStyle = "rgba(166,227,161,0.35)"; ctx.fill();
                ctx.strokeStyle = "#a6e3a1"; ctx.lineWidth = 2; ctx.stroke();
            }
            // Label
            ctx.font = "11px sans-serif"; ctx.fillStyle = "#cdd6f4"; ctx.textAlign = "center";
            ctx.fillText(c.id, p.x, p.y - c.outerRadius * scale - 8);
        }
    });
}

// ── Force computation (mirrors Java ForceFieldEngine) ─────────
function evaluatePointCharge(c, px, py) {
    const dx = c.x - px, dy = c.y - py;
    let r = Math.hypot(dx, dy);
    const MIN_DIST = 0.05;
    let mag;
    if (c.falloff === "gaussian") {
        const sigma = c.sigma || 0.5;
        mag = c.strength * Math.exp(-(r * r) / (2 * sigma * sigma));
    } else if (c.falloff === "inverse_linear") {
        if (r < MIN_DIST) r = MIN_DIST;
        mag = c.strength / r;
    } else { // inverse_square
        if (r < MIN_DIST) r = MIN_DIST;
        mag = c.strength / (r * r);
    }
    if (r < 1e-9) return { fx: 0, fy: 0 };
    return { fx: (dx / Math.hypot(dx, dy)) * mag, fy: (dy / Math.hypot(dx, dy)) * mag };
}

function evaluateLineCharge(c, px, py) {
    const sx = c.x2 - c.x1, sy = c.y2 - c.y1;
    const segLenSq = sx * sx + sy * sy;
    const MIN_DIST = 0.02;
    const falloff = c.falloffDistance || 1.0;
    if (segLenSq < 1e-9) {
        const dx = c.x1 - px, dy = c.y1 - py;
        let r = Math.hypot(dx, dy);
        if (r > falloff) return { fx: 0, fy: 0 };
        if (r < MIN_DIST) r = MIN_DIST;
        const mag = c.strength * Math.max(0, 1 - r / falloff);
        if (r < 1e-9) return { fx: 0, fy: 0 };
        return { fx: (dx / Math.hypot(dx, dy)) * mag, fy: (dy / Math.hypot(dx, dy)) * mag };
    }
    let t = ((px - c.x1) * sx + (py - c.y1) * sy) / segLenSq;
    t = Math.max(0, Math.min(1, t));
    const closestX = c.x1 + sx * t, closestY = c.y1 + sy * t;
    const dx = closestX - px, dy = closestY - py;
    let r = Math.hypot(dx, dy);
    if (r > falloff) return { fx: 0, fy: 0 };
    if (r < MIN_DIST) r = MIN_DIST;
    const mag = c.strength * (1 - r / falloff);
    const norm = Math.hypot(dx, dy);
    if (norm < 1e-9) return { fx: 0, fy: 0 };
    return { fx: (dx / norm) * mag, fy: (dy / norm) * mag };
}

function evaluateRadialZone(c, px, py) {
    const dx = c.x - px, dy = c.y - py;
    const r = Math.hypot(dx, dy);
    if (r > c.outerRadius) return { fx: 0, fy: 0 };
    let mag;
    if (r <= c.innerRadius) {
        mag = c.strength;
    } else {
        mag = c.strength * (1 - (r - c.innerRadius) / (c.outerRadius - c.innerRadius));
    }
    if (r < 1e-9) return { fx: 0, fy: 0 };
    return { fx: (dx / r) * mag, fy: (dy / r) * mag };
}

function getForceAt(px, py) {
    let fx = 0, fy = 0;
    for (const c of charges) {
        let f;
        if (c.type === "point") f = evaluatePointCharge(c, px, py);
        else if (c.type === "line") f = evaluateLineCharge(c, px, py);
        else if (c.type === "radial") f = evaluateRadialZone(c, px, py);
        else continue;
        fx += f.fx;
        fy += f.fy;
    }
    return { fx, fy };
}

// ── Visualization ─────────────────────────────────────────────
function drawForceArrows() {
    const step = 0.5; // meters
    const maxLen = 20; // max arrow length in pixels
    for (let x = step / 2; x < FIELD_W; x += step) {
        for (let y = step / 2; y < FIELD_H; y += step) {
            const { fx, fy } = getForceAt(x, y);
            const mag = Math.hypot(fx, fy);
            if (mag < 0.01) continue;
            const p = fieldToCanvas(x, y);
            const len = Math.min(mag * 5, maxLen);
            const angle = Math.atan2(-fy, fx); // canvas Y is inverted
            // Color: green = attractive (positive component toward center), yellow = repulsive
            const isAttractive = fx * (FIELD_W / 2 - x) + fy * (FIELD_H / 2 - y) > 0;
            const intensity = Math.min(mag / 3, 1);
            ctx.strokeStyle = isAttractive
                ? `rgba(${COLOR_ATTRACT},${0.2 + intensity * 0.6})`
                : `rgba(${COLOR_REPULSE},${0.2 + intensity * 0.6})`;
            ctx.lineWidth = 1.5;
            const ex = p.x + Math.cos(angle) * len;
            const ey = p.y + Math.sin(angle) * len;
            ctx.beginPath();
            ctx.moveTo(p.x, p.y);
            ctx.lineTo(ex, ey);
            ctx.stroke();
            // Arrowhead
            const headLen = 4;
            ctx.beginPath();
            ctx.moveTo(ex, ey);
            ctx.lineTo(ex - Math.cos(angle - 0.4) * headLen, ey - Math.sin(angle - 0.4) * headLen);
            ctx.moveTo(ex, ey);
            ctx.lineTo(ex - Math.cos(angle + 0.4) * headLen, ey - Math.sin(angle + 0.4) * headLen);
            ctx.stroke();
        }
    }
}

function drawHeatmap() {
    const step = 0.25;
    const cellW = step * scale;
    const cellH = step * scale;
    for (let x = 0; x < FIELD_W; x += step) {
        for (let y = 0; y < FIELD_H; y += step) {
            const { fx, fy } = getForceAt(x + step / 2, y + step / 2);
            const mag = Math.hypot(fx, fy);
            if (mag < 0.05) continue;
            const p = fieldToCanvas(x, y + step);
            const intensity = Math.min(mag / 5, 1);
            // Determine dominant direction relative to nearest charge
            const netStr = charges.reduce((s, c) => {
                if (c.type === "point" || c.type === "radial") {
                    const dx = (c.x || 0) - (x + step / 2);
                    const dy = (c.y || 0) - (y + step / 2);
                    const dot = fx * dx + fy * dy;
                    return s + (dot > 0 ? dot : dot);
                }
                return s;
            }, 0);
            // green = attractive, yellow = repulsive
            ctx.fillStyle = netStr >= 0
                ? `rgba(${COLOR_ATTRACT},${intensity * 0.4})`
                : `rgba(${COLOR_REPULSE},${intensity * 0.4})`;
            ctx.fillRect(p.x, p.y, cellW + 1, cellH + 1);
        }
    }
}

function drawRobotPreview() {
    if (mouseFieldX < -0.5 || mouseFieldX > FIELD_W + 0.5) return;
    const center = fieldToCanvas(mouseFieldX, mouseFieldY);
    // Draw robot outline
    const hw = (ROBOT_W / 2) * scale;
    const hh = (ROBOT_H / 2) * scale;
    ctx.strokeStyle = "rgba(205,214,244,0.4)";
    ctx.lineWidth = 1;
    ctx.strokeRect(center.x - hw, center.y - hh, hw * 2, hh * 2);
    // Draw corner forces
    ROBOT_CORNERS.forEach(corner => {
        const cx = mouseFieldX + corner.x;
        const cy = mouseFieldY + corner.y;
        const cp = fieldToCanvas(cx, cy);
        const { fx, fy } = getForceAt(cx, cy);
        const mag = Math.hypot(fx, fy);
        if (mag < 0.01) return;
        const len = Math.min(mag * 8, 30);
        const angle = Math.atan2(-fy, fx);
        ctx.strokeStyle = "rgba(203,166,247,0.8)";
        ctx.lineWidth = 2;
        const ex = cp.x + Math.cos(angle) * len;
        const ey = cp.y + Math.sin(angle) * len;
        ctx.beginPath(); ctx.moveTo(cp.x, cp.y); ctx.lineTo(ex, ey); ctx.stroke();
        // Arrowhead
        const hl = 5;
        ctx.beginPath();
        ctx.moveTo(ex, ey);
        ctx.lineTo(ex - Math.cos(angle - 0.4) * hl, ey - Math.sin(angle - 0.4) * hl);
        ctx.moveTo(ex, ey);
        ctx.lineTo(ex - Math.cos(angle + 0.4) * hl, ey - Math.sin(angle + 0.4) * hl);
        ctx.stroke();
        // Corner dot
        ctx.beginPath(); ctx.arc(cp.x, cp.y, 3, 0, Math.PI * 2);
        ctx.fillStyle = "rgba(203,166,247,0.6)"; ctx.fill();
    });
}

function drawLineDragPreview() {
    if (!lineDragStart) return;
    const p1 = fieldToCanvas(lineDragStart.x, lineDragStart.y);
    const p2 = fieldToCanvas(mouseFieldX, mouseFieldY);
    ctx.strokeStyle = "rgba(249,226,175,0.6)";
    ctx.lineWidth = 2;
    ctx.setLineDash([6, 4]);
    ctx.beginPath(); ctx.moveTo(p1.x, p1.y); ctx.lineTo(p2.x, p2.y); ctx.stroke();
    ctx.setLineDash([]);
}

// ── Drag handle hit-testing ────────────────────────────────────
const HANDLE_RADIUS_M = 0.25;  // grab radius in meters

/** Returns { index, handle } or null. handle = 'center' | 'p1' | 'p2' | 'mid' */
function findDragHandle(fx, fy) {
    let best = null, bestDist = HANDLE_RADIUS_M;
    charges.forEach((c, i) => {
        if (c.type === "point") {
            const sigma = c.sigma || 0.5;
            const sigmaHandleX = c.x + sigma, sigmaHandleY = c.y;
            const dSigma = Math.hypot(sigmaHandleX - fx, sigmaHandleY - fy);
            const d = Math.hypot(c.x - fx, c.y - fy);
            if (dSigma < bestDist) { bestDist = dSigma; best = { index: i, handle: "sigma" }; }
            if (d < bestDist) { bestDist = d; best = { index: i, handle: "center" }; }
        } else if (c.type === "radial") {
            // Check radius handles first (on the right edge), then center
            const distFromCenter = Math.hypot(c.x - fx, c.y - fy);
            const outerHandleX = c.x + c.outerRadius, outerHandleY = c.y;
            const innerHandleX = c.x + c.innerRadius, innerHandleY = c.y;
            const dOuter = Math.hypot(outerHandleX - fx, outerHandleY - fy);
            const dInner = Math.hypot(innerHandleX - fx, innerHandleY - fy);
            if (dOuter < bestDist) { bestDist = dOuter; best = { index: i, handle: "outerRadius" }; }
            if (dInner < bestDist) { bestDist = dInner; best = { index: i, handle: "innerRadius" }; }
            if (distFromCenter < bestDist) { bestDist = distFromCenter; best = { index: i, handle: "center" }; }
        } else if (c.type === "line") {
            // Check endpoints first (more specific), then midpoint
            const d1 = Math.hypot(c.x1 - fx, c.y1 - fy);
            const d2 = Math.hypot(c.x2 - fx, c.y2 - fy);
            const mx = (c.x1 + c.x2) / 2, my = (c.y1 + c.y2) / 2;
            const dm = Math.hypot(mx - fx, my - fy);
            if (d1 < bestDist) { bestDist = d1; best = { index: i, handle: "p1" }; }
            if (d2 < bestDist) { bestDist = d2; best = { index: i, handle: "p2" }; }
            if (dm < bestDist) { bestDist = dm; best = { index: i, handle: "mid" }; }
        }
    });
    return best;
}

function applyDrag(fx, fy) {
    if (!dragTarget) return;
    const c = charges[dragTarget.index];
    const nx = round2(fx - dragOffset.x);
    const ny = round2(fy - dragOffset.y);
    if (dragTarget.handle === "center") {
        c.x = nx; c.y = ny;
    } else if (dragTarget.handle === "p1") {
        c.x1 = nx; c.y1 = ny;
    } else if (dragTarget.handle === "p2") {
        c.x2 = nx; c.y2 = ny;
    } else if (dragTarget.handle === "mid") {
        const hw = (c.x2 - c.x1) / 2, hh = (c.y2 - c.y1) / 2;
        c.x1 = nx - hw; c.y1 = ny - hh;
        c.x2 = nx + hw; c.y2 = ny + hh;
    } else if (dragTarget.handle === "outerRadius" || dragTarget.handle === "innerRadius") {
        const r = round2(Math.max(0.05, Math.hypot(fx - c.x, fy - c.y)));
        if (dragTarget.handle === "outerRadius") {
            c.outerRadius = Math.max(c.innerRadius + 0.05, r);
        } else {
            c.innerRadius = Math.min(c.outerRadius - 0.05, r);
        }
    } else if (dragTarget.handle === "sigma") {
        c.sigma = round2(Math.max(0.05, Math.hypot(fx - c.x, fy - c.y)));
    }
}

function getDragCursor(fx, fy) {
    if (mode !== "select") return "crosshair";
    return findDragHandle(fx, fy) ? "grab" : "default";
}

// ── Mouse handling ────────────────────────────────────────────
canvas.addEventListener("mousemove", e => {
    const rect = canvas.getBoundingClientRect();
    const cp = canvasToField(e.clientX - rect.left, e.clientY - rect.top);
    mouseFieldX = cp.x;
    mouseFieldY = cp.y;

    if (isDragging && dragTarget) {
        applyDrag(cp.x, cp.y);
        canvas.style.cursor = "grabbing";
    } else {
        canvas.style.cursor = getDragCursor(cp.x, cp.y);
    }
    draw();
});

canvas.addEventListener("mousedown", e => {
    const rect = canvas.getBoundingClientRect();
    const cp = canvasToField(e.clientX - rect.left, e.clientY - rect.top);
    const fx = cp.x, fy = cp.y;

    if (mode === "point") {
        pushUndo();
        charges.push({
            type: "point", id: `point_${charges.length}`,
            x: round2(fx), y: round2(fy),
            strength: 2.0, falloff: "gaussian", sigma: 0.5,
        });
        selectSingle(charges.length - 1);
        refreshUI();
    } else if (mode === "radial") {
        pushUndo();
        charges.push({
            type: "radial", id: `radial_${charges.length}`,
            x: round2(fx), y: round2(fy),
            innerRadius: 0.3, outerRadius: 1.5, strength: 3.0,
        });
        selectSingle(charges.length - 1);
        refreshUI();
    } else if (mode === "line") {
        if (!lineDragStart) {
            lineDragStart = { x: round2(fx), y: round2(fy) };
        }
        // mouseup will finalize
    } else {
        // Select mode: try drag handle first, then fall back to selection
        const handle = findDragHandle(fx, fy);
        if (handle) {
            pushUndo();  // save state before drag begins
            isDragging = true;
            dragTarget = handle;
            if (!selectedSet.has(handle.index)) selectSingle(handle.index);
            selectedIdx = handle.index;
            const c = charges[handle.index];
            // Compute offset so the charge doesn't "snap" to cursor
            if (handle.handle === "center") {
                dragOffset = { x: fx - c.x, y: fy - c.y };
            } else if (handle.handle === "p1") {
                dragOffset = { x: fx - c.x1, y: fy - c.y1 };
            } else if (handle.handle === "p2") {
                dragOffset = { x: fx - c.x2, y: fy - c.y2 };
            } else if (handle.handle === "mid") {
                const mx = (c.x1 + c.x2) / 2, my = (c.y1 + c.y2) / 2;
                dragOffset = { x: fx - mx, y: fy - my };
            } else {
                // innerRadius / outerRadius — no offset needed, applyDrag uses distance from center
                dragOffset = { x: 0, y: 0 };
            }
            canvas.style.cursor = "grabbing";
            refreshUI();
        } else {
            const clicked = findClickedCharge(fx, fy);
            if (e.shiftKey && clicked >= 0) {
                toggleSelect(clicked);
            } else {
                selectSingle(clicked);
            }
            refreshUI();
        }
    }
    draw();
});

canvas.addEventListener("mouseup", e => {
    if (isDragging) {
        isDragging = false;
        dragTarget = null;
        canvas.style.cursor = "default";
        refreshUI();  // Update properties panel with final position
        draw();
        return;
    }
    if (mode === "line" && lineDragStart) {
        const rect = canvas.getBoundingClientRect();
        const cp = canvasToField(e.clientX - rect.left, e.clientY - rect.top);
        const dist = Math.hypot(cp.x - lineDragStart.x, cp.y - lineDragStart.y);
        if (dist > 0.1) {
            pushUndo();
            charges.push({
                type: "line", id: `line_${charges.length}`,
                x1: lineDragStart.x, y1: lineDragStart.y,
                x2: round2(cp.x), y2: round2(cp.y),
                strength: -3.0, falloffDistance: 1.0,
            });
            selectSingle(charges.length - 1);
        }
        lineDragStart = null;
        refreshUI();
        draw();
    }
});

canvas.addEventListener("mouseleave", () => {
    if (isDragging) {
        isDragging = false;
        dragTarget = null;
        canvas.style.cursor = "default";
        refreshUI();
        draw();
    }
});

function findClickedCharge(fx, fy) {
    let best = -1, bestDist = 0.3; // 30cm click radius
    charges.forEach((c, i) => {
        let d;
        if (c.type === "point" || c.type === "radial") {
            d = Math.hypot(c.x - fx, c.y - fy);
        } else if (c.type === "line") {
            d = pointToSegmentDist(fx, fy, c.x1, c.y1, c.x2, c.y2);
        }
        if (d < bestDist) { bestDist = d; best = i; }
    });
    return best;
}

function pointToSegmentDist(px, py, x1, y1, x2, y2) {
    const sx = x2 - x1, sy = y2 - y1;
    const lenSq = sx * sx + sy * sy;
    if (lenSq < 1e-9) return Math.hypot(px - x1, py - y1);
    let t = ((px - x1) * sx + (py - y1) * sy) / lenSq;
    t = Math.max(0, Math.min(1, t));
    return Math.hypot(px - (x1 + sx * t), py - (y1 + sy * t));
}

// ── UI updates ────────────────────────────────────────────────
function refreshUI() {
    updateChargeList();
    updatePropsPanel();
    updateModeButtons();
    saveToLocalStorage();
}

function updateChargeList() {
    const list = document.getElementById("charge-list");
    list.innerHTML = "";
    charges.forEach((c, i) => {
        const div = document.createElement("div");
        div.className = "charge-item" + (selectedSet.has(i) ? " selected" : "");
        div.innerHTML = `
            <span>
                <span class="type-badge ${c.type}">${c.type}</span>
                &nbsp; ${c.id}
                <span style="color: ${c.strength >= 0 ? HEX_ATTRACT : HEX_REPULSE}; font-size: 11px;">
                    (${c.strength >= 0 ? '+' : ''}${c.strength})
                </span>
            </span>
            <span class="delete-btn" data-idx="${i}" title="Delete">✕</span>
        `;
        div.addEventListener("click", e => {
            if (e.target.classList.contains("delete-btn")) return;
            if (e.shiftKey) {
                toggleSelect(i);
            } else {
                selectSingle(i);
            }
            refreshUI();
            draw();
        });
        div.querySelector(".delete-btn").addEventListener("click", e => {
            e.stopPropagation();
            pushUndo();
            charges.splice(i, 1);
            // Rebuild selectedSet — indices above i shifted down by 1
            const newSet = new Set();
            for (const s of selectedSet) {
                if (s < i) newSet.add(s);
                else if (s > i) newSet.add(s - 1);
                // s === i is removed
            }
            selectedSet = newSet;
            selectedIdx = selectedSet.size > 0 ? [...selectedSet][selectedSet.size - 1] : -1;
            refreshUI();
            draw();
        });
        list.appendChild(div);
    });
    document.getElementById("status-charges").textContent = `Charges: ${charges.length}`;
}

function updatePropsPanel() {
    const panel = document.getElementById("props-panel");
    const grid = document.getElementById("props-grid");
    if (selectedSet.size !== 1 || selectedIdx < 0 || selectedIdx >= charges.length) {
        panel.style.display = "none";
        return;
    }
    panel.style.display = "block";
    const c = charges[selectedIdx];
    grid.innerHTML = "";

    const fields = [];
    fields.push({ label: "ID", key: "id", type: "text" });
    fields.push({ label: "Strength", key: "strength", type: "number", step: 0.5 });

    if (c.type === "point") {
        fields.push({ label: "X (m)", key: "x", type: "number", step: 0.1 });
        fields.push({ label: "Y (m)", key: "y", type: "number", step: 0.1 });
        fields.push({ label: "Falloff", key: "falloff", type: "select", options: ["gaussian", "inverse_square", "inverse_linear"] });
        fields.push({ label: "Sigma (m)", key: "sigma", type: "number", step: 0.1 });
    } else if (c.type === "line") {
        fields.push({ label: "X1 (m)", key: "x1", type: "number", step: 0.1 });
        fields.push({ label: "Y1 (m)", key: "y1", type: "number", step: 0.1 });
        fields.push({ label: "X2 (m)", key: "x2", type: "number", step: 0.1 });
        fields.push({ label: "Y2 (m)", key: "y2", type: "number", step: 0.1 });
        fields.push({ label: "Falloff (m)", key: "falloffDistance", type: "number", step: 0.1 });
    } else if (c.type === "radial") {
        fields.push({ label: "X (m)", key: "x", type: "number", step: 0.1 });
        fields.push({ label: "Y (m)", key: "y", type: "number", step: 0.1 });
        fields.push({ label: "Inner R (m)", key: "innerRadius", type: "number", step: 0.1, min: 0 });
        fields.push({ label: "Outer R (m)", key: "outerRadius", type: "number", step: 0.1, min: 0 });
    }

    fields.forEach(f => {
        const label = document.createElement("label");
        label.textContent = f.label;
        grid.appendChild(label);

        if (f.type === "select") {
            const sel = document.createElement("select");
            f.options.forEach(opt => {
                const o = document.createElement("option");
                o.value = opt; o.textContent = opt;
                if (c[f.key] === opt) o.selected = true;
                sel.appendChild(o);
            });
            sel.addEventListener("change", () => {
                pushUndo();
                c[f.key] = sel.value;
                draw();
            });
            grid.appendChild(sel);
        } else {
            const input = document.createElement("input");
            input.type = f.type;
            input.value = c[f.key];
            if (f.step) input.step = f.step;
            if (f.min !== undefined) input.min = f.min;
            input.addEventListener("focus", () => {
                input._undoPushed = false;
            });
            input.addEventListener("input", () => {
                if (!input._undoPushed) { pushUndo(); input._undoPushed = true; }
                if (f.type === "number") {
                    c[f.key] = parseFloat(input.value) || 0;
                } else {
                    c[f.key] = input.value;
                }
                updateChargeList();
                draw();
            });
            grid.appendChild(input);
        }
    });
}

function updateModeButtons() {
    document.getElementById("btn-add-point").classList.toggle("active", mode === "point");
    document.getElementById("btn-add-line").classList.toggle("active", mode === "line");
    document.getElementById("btn-add-radial").classList.toggle("active", mode === "radial");
    document.getElementById("status-mode").textContent =
        mode === "select" ? "Mode: Select" : `Mode: Place ${mode}`;
}

function updateStatus() {
    document.getElementById("status-pos").textContent =
        `Mouse: (${mouseFieldX.toFixed(2)}, ${mouseFieldY.toFixed(2)}) m`;
}

// ── Button bindings ───────────────────────────────────────────
document.getElementById("btn-add-point").addEventListener("click", () => {
    mode = mode === "point" ? "select" : "point";
    lineDragStart = null;
    refreshUI();
});
document.getElementById("btn-add-line").addEventListener("click", () => {
    mode = mode === "line" ? "select" : "line";
    lineDragStart = null;
    refreshUI();
});
document.getElementById("btn-add-radial").addEventListener("click", () => {
    mode = mode === "radial" ? "select" : "radial";
    lineDragStart = null;
    refreshUI();
});

["arrows", "heatmap", "none"].forEach(m => {
    document.getElementById(`btn-${m}`).addEventListener("click", () => {
        vizMode = m;
        document.getElementById("btn-arrows").classList.toggle("active", m === "arrows");
        document.getElementById("btn-heatmap").classList.toggle("active", m === "heatmap");
        document.getElementById("btn-none").classList.toggle("active", m === "none");
        draw();
    });
});

// Field-element presets
document.getElementById("btn-add-walls").addEventListener("click", addWalls);
document.getElementById("btn-add-obstacles").addEventListener("click", addObstacles);

document.getElementById("chk-robot-preview").addEventListener("change", e => {
    showRobotPreview = e.target.checked;
    draw();
});

// Robot size inputs
["robot-width", "robot-height"].forEach(id => {
    document.getElementById(id).addEventListener("input", () => {
        const w = parseFloat(document.getElementById("robot-width").value) || 0.552;
        const h = parseFloat(document.getElementById("robot-height").value) || 0.514;
        updateRobotSize(w, h);
        saveToLocalStorage();
        draw();
    });
});

document.getElementById("btn-zoom-in").addEventListener("click", () => { scale *= 1.2; draw(); });
document.getElementById("btn-zoom-out").addEventListener("click", () => { scale /= 1.2; draw(); });
document.getElementById("btn-zoom-fit").addEventListener("click", () => { fitToWindow(); draw(); });
document.getElementById("btn-undo").addEventListener("click", undo);
document.getElementById("btn-redo").addEventListener("click", redo);

// Pinch-to-zoom (trackpad / touch) — zoom toward the cursor position
canvas.addEventListener("wheel", e => {
    e.preventDefault();
    const rect = canvas.getBoundingClientRect();
    const cx = e.clientX - rect.left;
    const cy = e.clientY - rect.top;
    const factor = e.deltaY < 0 ? 1.05 : 1 / 1.05;
    // Zoom toward cursor: adjust offset so the field point under the cursor stays fixed
    const newScale = scale * factor;
    offsetX = cx - (cx - offsetX) * (newScale / scale);
    offsetY = cy - (cy - offsetY) * (newScale / scale);
    scale = newScale;
    draw();
}, { passive: false });

// Map settings
["map-name", "gain-force", "gain-torque", "gain-max-vel", "gain-max-torque"].forEach(id => {
    document.getElementById(id).addEventListener("input", () => {
        mapSettings.name = document.getElementById("map-name").value;
        mapSettings.forceGain = parseFloat(document.getElementById("gain-force").value) || 1;
        mapSettings.torqueGain = parseFloat(document.getElementById("gain-torque").value) || 0.5;
        mapSettings.maxForceVelocity = parseFloat(document.getElementById("gain-max-vel").value) || 2;
        mapSettings.maxForceTorque = parseFloat(document.getElementById("gain-max-torque").value) || 1.5;
        saveToLocalStorage();
    });
});

// ── Export / Import ───────────────────────────────────────────
document.getElementById("btn-export").addEventListener("click", () => {
    const data = {
        name: mapSettings.name,
        maxForceVelocity: mapSettings.maxForceVelocity,
        maxForceTorque: mapSettings.maxForceTorque,
        forceGain: mapSettings.forceGain,
        torqueGain: mapSettings.torqueGain,
        charges: charges,
    };
    const json = JSON.stringify(data, null, 2);
    const blob = new Blob([json], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = (mapSettings.name.replace(/\s+/g, "_").toLowerCase() || "forcefield") + ".json";
    a.click();
    URL.revokeObjectURL(url);
});

document.getElementById("btn-import").addEventListener("click", () => {
    document.getElementById("file-input").click();
});

document.getElementById("file-input").addEventListener("change", e => {
    const file = e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = ev => {
        try {
            const data = JSON.parse(ev.target.result);
            pushUndo();
            charges = data.charges || [];
            mapSettings.name = data.name || "Imported";
            mapSettings.forceGain = data.forceGain ?? 1;
            mapSettings.torqueGain = data.torqueGain ?? 0.5;
            mapSettings.maxForceVelocity = data.maxForceVelocity ?? 2;
            mapSettings.maxForceTorque = data.maxForceTorque ?? 1.5;
            document.getElementById("map-name").value = mapSettings.name;
            document.getElementById("gain-force").value = mapSettings.forceGain;
            document.getElementById("gain-torque").value = mapSettings.torqueGain;
            document.getElementById("gain-max-vel").value = mapSettings.maxForceVelocity;
            document.getElementById("gain-max-torque").value = mapSettings.maxForceTorque;
            clearSelection();
            refreshUI();
            draw();
        } catch (err) {
            alert("Failed to parse JSON: " + err.message);
        }
    };
    reader.readAsText(file);
    e.target.value = ""; // allow re-importing same file
});

document.getElementById("btn-clear").addEventListener("click", () => {
    if (confirm("Delete all charges?")) {
        pushUndo();
        charges = [];
        clearSelection();
        refreshUI();
        draw();
    }
});

document.getElementById("btn-reset").addEventListener("click", () => {
    if (confirm("Reset editor to defaults? This will clear all charges, settings, and undo history.")) {
        clearLocalStorage();
        charges = [];
        mapSettings = { name: "Default Field", forceGain: 1.0, torqueGain: 0.5, maxForceVelocity: 2.0, maxForceTorque: 1.5 };
        updateRobotSize(0.552, 0.514);
        undoStack = []; redoStack = [];
        clearSelection();
        document.getElementById("map-name").value = mapSettings.name;
        document.getElementById("gain-force").value = mapSettings.forceGain;
        document.getElementById("gain-torque").value = mapSettings.torqueGain;
        document.getElementById("gain-max-vel").value = mapSettings.maxForceVelocity;
        document.getElementById("gain-max-torque").value = mapSettings.maxForceTorque;
        document.getElementById("robot-width").value = robotWidth;
        document.getElementById("robot-height").value = robotHeight;
        updateUndoRedoButtons();
        refreshUI();
        draw();
    }
});

// Keyboard shortcuts
document.addEventListener("keydown", e => {
    if (e.target.tagName === "INPUT" || e.target.tagName === "SELECT") return;
    if (e.key === "Delete" || e.key === "Backspace") {
        if (selectedSet.size > 0) {
            pushUndo();
            // Delete selected indices in descending order to keep indices stable
            const sorted = [...selectedSet].sort((a, b) => b - a);
            for (const idx of sorted) charges.splice(idx, 1);
            clearSelection();
            refreshUI();
            draw();
        }
    }
    // Undo / Redo
    if ((e.metaKey || e.ctrlKey) && e.key === "z" && !e.shiftKey) {
        e.preventDefault(); undo();
    }
    if ((e.metaKey || e.ctrlKey) && (e.key === "Z" || e.key === "y")) {
        e.preventDefault(); redo();
    }
    // Duplicate-mirror selected charges about the field center (Ctrl/Cmd+D)
    if ((e.metaKey || e.ctrlKey) && e.key === "d") {
        e.preventDefault();
        if (selectedSet.size > 0) {
            pushUndo();
            const cx = FIELD_W / 2, cy = FIELD_H / 2;
            const newIndices = [];
            for (const idx of selectedSet) {
                const orig = charges[idx];
                const dup = JSON.parse(JSON.stringify(orig));
                dup.id = orig.id + "_mirror";
                if (dup.type === "point" || dup.type === "radial") {
                    dup.x = round2(2 * cx - orig.x);
                    dup.y = round2(2 * cy - orig.y);
                } else if (dup.type === "line") {
                    dup.x1 = round2(2 * cx - orig.x1);
                    dup.y1 = round2(2 * cy - orig.y1);
                    dup.x2 = round2(2 * cx - orig.x2);
                    dup.y2 = round2(2 * cy - orig.y2);
                }
                charges.push(dup);
                newIndices.push(charges.length - 1);
            }
            // Select the newly created mirrored charges
            selectedSet.clear();
            newIndices.forEach(i => selectedSet.add(i));
            selectedIdx = newIndices[newIndices.length - 1];
            refreshUI(); draw();
        }
    }
    if (e.key === "Escape") {
        mode = "select";
        lineDragStart = null;
        clearSelection();
        refreshUI();
        draw();
    }
    if (e.key === "p") { mode = "point"; lineDragStart = null; refreshUI(); }
    if (e.key === "l") { mode = "line"; lineDragStart = null; refreshUI(); }
    if (e.key === "r") { mode = "radial"; lineDragStart = null; refreshUI(); }
    // Arrow key panning
    const PAN_PX = 40;
    if (e.key === "ArrowLeft")  { e.preventDefault(); offsetX += PAN_PX; draw(); }
    if (e.key === "ArrowRight") { e.preventDefault(); offsetX -= PAN_PX; draw(); }
    if (e.key === "ArrowUp")    { e.preventDefault(); offsetY += PAN_PX; draw(); }
    if (e.key === "ArrowDown")  { e.preventDefault(); offsetY -= PAN_PX; draw(); }
});

// ── Helpers ───────────────────────────────────────────────────
function round2(v) { return Math.round(v * 100) / 100; }

// ── Known game obstacles (coordinates in meters, WPILib blue-origin) ──
/**
 * Factory functions keyed by year.  Each returns an array of charge objects
 * representing the major fixed obstacles robots cannot drive through.
 * Coordinates come from WPILib AprilTag layouts and FIRST field drawings.
 */
const GAME_OBSTACLES = {
    2025: function reefscapeObstacles() {
        // 2025 REEFSCAPE — two reef hexagons (blue + red side)
        // Regular hexagon: apothem ≈ 0.832 m, circumradius ≈ 0.961 m
        // Derived from AprilTag face-center positions on the reef.
        const R = 0.961;
        const reefs = [
            { cx: 4.490, cy: 4.026, prefix: "reef_blue" },
            { cx: 13.058, cy: 4.026, prefix: "reef_red" },
        ];
        const out = [];
        for (const reef of reefs) {
            for (let i = 0; i < 6; i++) {
                const a1 = (30 + 60 * i) * Math.PI / 180;
                const a2 = (30 + 60 * (i + 1)) * Math.PI / 180;
                out.push({
                    type: "line", id: `${reef.prefix}_edge${i}`,
                    x1: round2(reef.cx + R * Math.cos(a1)),
                    y1: round2(reef.cy + R * Math.sin(a1)),
                    x2: round2(reef.cx + R * Math.cos(a2)),
                    y2: round2(reef.cy + R * Math.sin(a2)),
                    strength: -3.0, falloffDistance: 0.5,
                });
            }
        }
        return out;
    },
};

/** Add 4 repulsive line charges along the field perimeter. */
function addWalls() {
    pushUndo();
    const wallIds = ["wall_bottom", "wall_top", "wall_left", "wall_right"];
    // Remove existing walls (by id prefix) to allow re-generation on field switch
    charges = charges.filter(c => !wallIds.includes(c.id));
    charges.push(
        { type: "line", id: "wall_bottom", x1: 0, y1: 0, x2: round2(FIELD_W), y2: 0, strength: -3.0, falloffDistance: 0.75 },
        { type: "line", id: "wall_top",    x1: 0, y1: round2(FIELD_H), x2: round2(FIELD_W), y2: round2(FIELD_H), strength: -3.0, falloffDistance: 0.75 },
        { type: "line", id: "wall_left",   x1: 0, y1: 0, x2: 0, y2: round2(FIELD_H), strength: -3.0, falloffDistance: 0.75 },
        { type: "line", id: "wall_right",  x1: round2(FIELD_W), y1: 0, x2: round2(FIELD_W), y2: round2(FIELD_H), strength: -3.0, falloffDistance: 0.75 },
    );
    selectSingle(charges.length - 4); // select first wall
    refreshUI(); draw();
}

/** Add known game obstacles for the currently selected field year. */
function addObstacles() {
    if (!currentField) return;
    const factory = GAME_OBSTACLES[currentField.year];
    if (!factory) return;
    pushUndo();
    const newObstacles = factory();
    // Remove existing obstacles with matching id prefixes to allow re-generation
    const newIds = new Set(newObstacles.map(c => c.id));
    charges = charges.filter(c => !newIds.has(c.id));
    charges.push(...newObstacles);
    selectSingle(charges.length - newObstacles.length);
    refreshUI(); draw();
}

/** Enable / disable the obstacles button based on the selected field year. */
function updateObstaclesButton() {
    const btn = document.getElementById("btn-add-obstacles");
    if (currentField && GAME_OBSTACLES[currentField.year]) {
        btn.disabled = false;
        btn.title = `Add ${currentField.game} obstacles`;
    } else {
        btn.disabled = true;
        btn.title = "No known obstacles for this field year";
    }
}

// ── Init ──────────────────────────────────────────────────────
async function init() {
    try {
        await loadFieldManifest();
    } catch (err) {
        console.error("Could not load field manifest:", err);
    }
    populateFieldSelector();
    if (FIELDS.length > 0) loadFieldImage(FIELDS[0]);

    // Restore saved state from localStorage
    if (loadFromLocalStorage()) {
        document.getElementById("map-name").value = mapSettings.name;
        document.getElementById("gain-force").value = mapSettings.forceGain;
        document.getElementById("gain-torque").value = mapSettings.torqueGain;
        document.getElementById("gain-max-vel").value = mapSettings.maxForceVelocity;
        document.getElementById("gain-max-torque").value = mapSettings.maxForceTorque;
        document.getElementById("robot-width").value = robotWidth;
        document.getElementById("robot-height").value = robotHeight;
    }

    resize();
    refreshUI();
}
init();
