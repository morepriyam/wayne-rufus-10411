import struct

with open('akit_26-03-06_09-04-04.wpilog', 'rb') as f:
    data = f.read()

extra_len = struct.unpack_from('<I', data, 8)[0]
pos = 12 + extra_len

entry_names = {}
records = []

while pos < len(data) - 4:
    try:
        bitfield = data[pos]; pos += 1
        entry_len = (bitfield & 0x3) + 1
        size_len = ((bitfield >> 2) & 0x3) + 1
        ts_len = ((bitfield >> 4) & 0x3) + 1

        entry_id = int.from_bytes(data[pos:pos+entry_len], 'little'); pos += entry_len
        payload_size = int.from_bytes(data[pos:pos+size_len], 'little'); pos += size_len
        timestamp_us = int.from_bytes(data[pos:pos+ts_len], 'little'); pos += ts_len
        payload = data[pos:pos+payload_size]; pos += payload_size

        if entry_id == 0 and len(payload) > 5:
            if payload[0] == 0:
                eid = struct.unpack_from('<I', payload, 1)[0]
                name_len = struct.unpack_from('<I', payload, 5)[0]
                name = payload[9:9+name_len].decode('utf-8', errors='replace')
                entry_names[eid] = name
        else:
            records.append((timestamp_us, entry_id, payload))
    except Exception:
        break

by_name = {}
for ts, eid, payload in records:
    name = entry_names.get(eid)
    if name:
        by_name.setdefault(name, []).append((ts / 1e6, payload))

def get_double(payload):
    return struct.unpack_from('<d', payload)[0] if len(payload) >= 8 else None

def get_bool(payload):
    return bool(payload[0]) if payload else None

def get_string(payload):
    return payload.decode('utf-8', errors='replace')

keys_of_interest = [
    '/RealOutputs/Shooter/Left/RPM',
    '/RealOutputs/Shooter/TargetRPM',
    '/RealOutputs/Shooter/ReadyToShoot',
    '/RealOutputs/Shooter/AboveFeedThreshold',
    '/RealOutputs/Shooter/ActiveCommand',
    '/RealOutputs/Feeder/ActiveCommand',
    '/RealOutputs/Feeder/RPM',
    '/RealOutputs/Floor/ActiveCommand',
    '/RealOutputs/Floor/RPM',
]

print("=== All state changes (value-on-change only) ===")
events = []
for key in keys_of_interest:
    for t, payload in by_name.get(key, []):
        if 'RPM' in key:
            val = get_double(payload)
            if val is not None: val = round(val, 1)
        elif 'Command' in key:
            val = get_string(payload)
        else:
            val = get_bool(payload)
        label = '/'.join(key.split('/')[-2:])
        events.append((t, label, val))

events.sort()
for t, name, val in events:
    print(f"  t={t:.3f}s  {name} = {val}")
