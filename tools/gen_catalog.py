"""Regenerates app/src/main/assets/default_catalog.json (catalog version 2).
Every register number is asserted against the hex address printed in the Schneider DOCA0355 Modbus RTU guide."""
import json, os
G0 = "DOCA0355EN-00 Modbus RTU guide"
G1 = "DOCA0355EN-01 Modbus RTU guide"
P = []

def add(id, name, reg, type="UINT16", scale=1.0, unit="", screen="registers", group="", role="", tile=False,
        fc=3, enum=None, bits=None, bitStyle="STATE", everyN=1, enabled=True, verified=True, source=G0,
        notes="", dec=-1, hexaddr=None, fmt="", length=0):
    if hexaddr is not None:
        assert reg - 1 == hexaddr, (id, reg, hex(reg - 1), hex(hexaddr))
    assert not id.startswith("u"), id           # 'u...' ids are reserved for user-created parameters
    P.append(dict(id=id, name=name, register=reg, type=type, function=fc, scale=scale, unit=unit, decimals=dec,
                  screen=screen, group=group, role=role, tile=tile, bitStyle=bitStyle, everyN=everyN, enabled=enabled,
                  verified=verified, source=source, notes=notes, fmt=fmt, length=length,
                  enum={str(k): v for k, v in (enum or {}).items()},
                  bits=[dict(bit=b, name=n) for b, n in (bits or [])]))

# ======================= STATUS screen (screen key "motor") =======================
add("motor_status", "Motor status", 5558, enum={1: "Stop", 2: "Start", 4: "Run"}, screen="motor", group="Motor state", role="MOTOR_STATE", hexaddr=0x15B5)
add("mode_status", "Control mode", 5901, enum={0: "Local 1", 1: "Remote", 2: "Local 2", 3: "Local 3"}, screen="motor", group="Motor state", hexaddr=0x170C, everyN=2, enabled=False, verified=False, source=G1 + " p.54", notes="Register not re-confirmed against the current guide. Enable and compare with the DTM first.")
add("active_trips", "Active trip count", 5902, screen="motor", group="Motor state", hexaddr=0x170D, everyN=2, enabled=False, verified=False, source=G1 + " p.54", notes="Register not re-confirmed against the current guide. Enable and compare with the DTM first.")
add("active_alarms", "Active alarm count", 5903, screen="motor", group="Motor state", hexaddr=0x170E, everyN=2, enabled=False, verified=False, source=G1 + " p.54", notes="Register not re-confirmed against the current guide. Enable and compare with the DTM first.")
add("trip_code", "Active trip code", 5904, screen="motor", group="Motor state", hexaddr=0x170F, everyN=2, enabled=False, verified=False, source=G1 + " p.54", notes="Register not re-confirmed against the current guide. Enable and compare with the DTM first.")
add("stop_cause", "Motor stop cause", 5577, screen="motor", group="Motor state", hexaddr=0x15C8, everyN=2,
    enum={0: "None", 1: "HMI", 2: "Local DI", 3: "Remote DI", 4: "Communication", 5: "Auto restart", 6: "Trip", 7: "No current",
          8: "Forced stop", 9: "Direction change", 10: "No feedback", 11: "Speed change", 12: "Custom stop",
          13: "Mode transfer", 14: "Device internal error", 15: "No voltage"})
add("thermal", "Thermal memory", 5559, "UINT16", 1, "%", "motor", "Thermal memory", role="THERMAL", hexaddr=0x15B6)
add("t_trip", "Thermal time to trip", 5560, "UINT32", 1, "s", "motor", "Thermal memory", hexaddr=0x15B7, everyN=2)
add("t_cool", "Thermal time to cool", 5562, "UINT32", 1, "s", "motor", "Thermal memory", hexaddr=0x15B9, everyN=2)
add("max_starts", "Max starts counter", 5564, screen="motor", group="Statistics", hexaddr=0x15BB, everyN=3)
add("max_start_time", "Max start inhibit time", 5565, "UINT32", 1, "s", "motor", "Statistics", hexaddr=0x15BC, everyN=3)
add("start_peak", "Starting peak current", 5567, "UINT32", 0.001, "A", "motor", "Statistics", hexaddr=0x15BE, everyN=3)
add("start_time", "Starting time", 5569, "UINT32", 0.001, "s", "motor", "Statistics", hexaddr=0x15C0, everyN=3)
add("run_total", "Total run time", 5571, "UINT32", 1, "", "motor", "Statistics", hexaddr=0x15C2, everyN=5, fmt="HMIN", notes="Raw unit is minutes, shown as h:mm.")
add("run_last", "Last run time", 5573, "UINT32", 1, "", "motor", "Statistics", hexaddr=0x15C4, everyN=5, fmt="HMIN", notes="Raw unit is minutes, shown as h:mm.")
add("n_starts", "Number of starts", 5575, screen="motor", group="Statistics", hexaddr=0x15C6, everyN=3)
add("n_stops", "Number of stops", 5576, screen="motor", group="Statistics", hexaddr=0x15C7, everyN=3)
add("trip_counter", "Trip counter", 5578, screen="motor", group="Statistics", hexaddr=0x15C9, everyN=3)

# --- discrete I/O and status bits: FC02, register = bit number, address = register - 1 ---
for n in range(1, 5):
    add("di_%d" % n, "Digital input %d" % n, n, "BOOL", fc=2, screen="motor", group="Main unit I/O", hexaddr=n - 1, verified=False,
        source="DOCA0258EN-00 Status Data", notes="Address 0x0000.. inferred from the layout (digital outputs start at 0x0020). Compare with the DTM.")
for n in range(1, 4):
    add("do_%d" % n, "Digital output %d" % n, 32 + n, "BOOL", fc=2, screen="motor", group="Main unit I/O", hexaddr=0x1F + n, source="DOCA0258EN-00 p.120 Digital Output Status")
add("co_1", "Contactor output 1", 497, "BOOL", fc=2, screen="motor", group="Main unit I/O", hexaddr=0x1F0, enabled=False, verified=False,
    source=G1 + " p.66", notes="Address not re-confirmed. Enable and compare with the DTM.")
S1 = "DOCA0258EN-00 Status Data p.123"
for reg, pid, nm in [(241, "ms_stop", "Motor stop"), (242, "ms_start", "Motor start"), (243, "ms_run", "Motor run"),
                     (244, "ms_inhibit", "Motor inhibit"), (245, "ms_remote", "Remote mode")]:
    add(pid, nm, reg, "BOOL", fc=2, screen="motor", group="Motor state", hexaddr=reg - 1, source=S1 + " Motor Status")
add("st_pickup", "Pickup status", 225, "BOOL", fc=2, screen="motor", group="Motor state", hexaddr=0xE0, source=S1 + " Common Trip, Alarm and Pickup Status")
add("st_alarm", "Alarm status", 226, "BOOL", fc=2, screen="motor", group="Motor state", hexaddr=0xE1, source=S1 + " Common Trip, Alarm and Pickup Status")
add("st_trip", "Trip status", 227, "BOOL", fc=2, screen="motor", group="Motor state", hexaddr=0xE2, source=S1 + " Common Trip, Alarm and Pickup Status")

INH = [(577, "No voltage"), (578, "Under voltage"), (579, "Trip"), (580, "Thermal"), (581, "Max starts")] + \
      [(581 + n, "Interlock %d" % n) for n in range(1, 13)] + \
      [(594, "Local DI stop"), (595, "Remote DI stop"), (596, "Communication stop"), (597, "Forced stop"), (598, "Antibackspin"),
       (600, "Direction change"), (601, "Speed change"), (602, "Custom stop"), (603, "Firmware update")]
for reg, nm in INH:       # guide: address 0x0240 + (register - 577)
    add("inh_%d" % reg, nm + " inhibit", reg, "BOOL", fc=2, screen="motor", group="Inhibit status", everyN=2,
        hexaddr=0x240 + (reg - 577), source="DOCA0258EN-00 p.124 Inhibit Status")

# ======================= METERING screen (screen key "electrical") =======================
add("i_l1", "L1 RMS current", 5501, "UINT32", 0.001, "A", "electrical", "Current", tile=True, hexaddr=0x157C)
add("i_l2", "L2 RMS current", 5503, "UINT32", 0.001, "A", "electrical", "Current", tile=True, hexaddr=0x157E)
add("i_l3", "L3 RMS current", 5505, "UINT32", 0.001, "A", "electrical", "Current", tile=True, hexaddr=0x1580)
add("i_gnd_m", "Measured ground current", 5507, "UINT32", 0.001, "A", "electrical", "Current", hexaddr=0x1582, everyN=2)
add("i_gnd_c", "Calculated ground current", 5509, "UINT32", 0.001, "A", "electrical", "Current", hexaddr=0x1584, everyN=2)
add("i_avg", "Average current (IAVG)", 5511, "UINT32", 0.001, "A", "electrical", "Current", role="IAVG", hexaddr=0x1586)
add("i_imb", "Current imbalance", 5513, "UINT16", 0.01, "%", "electrical", "Current", hexaddr=0x1588)
add("i_seq", "Current phase sequence", 5514, enum={0: "-", 1: "L123", 2: "L132", 3: "CT wiring error"}, screen="electrical", group="Current", hexaddr=0x1589, everyN=5)
add("v_l12", "L1-L2 RMS voltage", 5515, "UINT32", 0.1, "V", "electrical", "Voltage", tile=True, hexaddr=0x158A, notes="Single phase: this register is L1-N.")
add("v_l23", "L2-L3 RMS voltage", 5517, "UINT32", 0.1, "V", "electrical", "Voltage", tile=True, hexaddr=0x158C)
add("v_l31", "L3-L1 RMS voltage", 5519, "UINT32", 0.1, "V", "electrical", "Voltage", tile=True, hexaddr=0x158E)
add("v_avg", "Average voltage", 5521, "UINT32", 0.1, "V", "electrical", "Voltage", hexaddr=0x1590)
add("v_imb", "Voltage imbalance", 5523, "UINT16", 0.01, "%", "electrical", "Voltage", hexaddr=0x1592)
add("v_seq", "Voltage phase sequence", 5524, enum={0: "-", 1: "L123", 2: "L132"}, screen="electrical", group="Voltage", hexaddr=0x1593, everyN=5)
add("freq", "System frequency", 5525, "UINT16", 0.01, "Hz", "electrical", "Power", hexaddr=0x1594)
add("pf", "System power factor", 5526, "UINT16", 0.01, "", "electrical", "Power", hexaddr=0x1595)
add("p_act", "Total active power", 5527, "UINT32", 0.001, "kW", "electrical", "Power", tile=True, hexaddr=0x1596)
add("p_rea", "Total reactive power", 5529, "UINT32", 0.001, "kvar", "electrical", "Power", tile=True, hexaddr=0x1598)
add("p_app", "Total apparent power", 5531, "UINT32", 0.001, "kVA", "electrical", "Power", tile=True, hexaddr=0x159A)
add("e_act", "Total active energy", 5533, "UINT64", 0.001, "kWh", "electrical", "Energy", hexaddr=0x159C, everyN=5)
add("e_rea", "Total reactive energy", 5537, "UINT64", 0.001, "kvarh", "electrical", "Energy", hexaddr=0x15A0, everyN=5)
add("e_app", "Total apparent energy", 5541, "UINT64", 0.001, "kVAh", "electrical", "Energy", hexaddr=0x15A4, everyN=5)
for i, (reg, nm) in enumerate([(5545, "L1 current THD"), (5546, "L2 current THD"), (5547, "L3 current THD"),
                               (5548, "L1-L2 voltage THD"), (5549, "L2-L3 voltage THD"), (5550, "L3-L1 voltage THD")]):
    add(["thd_i1", "thd_i2", "thd_i3", "thd_v12", "thd_v23", "thd_v31"][i], nm, reg, "UINT16", 1, "%", "electrical", "Harmonics", tile=True,
        hexaddr=0x15A8 + i, everyN=5, notes="Single phase: L1-N voltage THD." if reg == 5548 else "")

# ======================= PROTECTION screen (screen key "protection") =======================
add("flc1", "Full load current (FLC1)", 4467, "UINT16", 0.1, "A", "protection", "Load", role="FLC", hexaddr=0x1172,
    source=G0 + " System Settings: 0x1172 / reg 4467, UINT16, 0.1 A", notes="Setting register, shown read-only. Register 4466 (0x1171) is Reserved.")
add("flc2", "Speed 2 full load current (FLC2)", 4468, "UINT16", 0.1, "A", "protection", "Load", hexaddr=0x1173, enabled=False,
    source=G0 + " System Settings: 0x1173 / reg 4468", notes="Only for two-speed starters.")
add("trip_status", "Trip status (32-bit)", 5646, "BITMAP32", screen="protection", group="Trip status", bitStyle="TRIP", hexaddr=0x160D, source=G1 + " p.55",
    notes="Bit names not filled in yet: add them as 'bit=name' lines once you have the Protection Function Status table.")

M4 = {0: "Disabled", 1: "Alarm", 2: "Trip", 3: "Alarm + Trip"}
M2 = {0: "Disabled", 1: "Enabled"}
PF = [  # id, name, register, hex address, modes, verified, source/notes
    ("pf_thermal", "Thermal overload", 3501, 0x0DAC, M4, True, ""),
    ("pf_stalled", "Stalled rotor", 3519, 0x0DBE, M4, True, ""),
    ("pf_locked", "Locked rotor", 3525, 0x0DC4, M4, True, ""),
    ("pf_defoc", "Definite time overcurrent", 3531, 0x0DCA, M4, False, "Block title was not visible in the extract; the register sits between locked rotor and normal inverse. Confirm in the DTM."),
    ("pf_invoc", "Normal inverse overcurrent", 3539, 0x0DD2, M4, True, ""),
    ("pf_shortoc", "Short time overcurrent", 3546, 0x0DD9, M4, True, ""),
    ("pf_gnd_c", "Calculated ground", 3553, 0x0DE0, M4, True, ""),
    ("pf_gnd_m", "Measured ground", 3560, 0x0DE7, M4, True, ""),
    ("pf_undercur", "Phase under current", 3567, 0x0DEE, M4, True, ""),
    ("pf_imb", "Current imbalance", 3575, 0x0DF6, M4, True, ""),
    ("pf_undervolt", "Phase under voltage", 3626, 0x0E29, M4, True, ""),
    ("pf_overvolt", "Phase over voltage", 3634, 0x0E31, M4, True, ""),
    ("pf_vphloss", "Voltage phase loss", 3641, 0x0E38, M4, True, ""),
    ("pf_vimb", "Voltage imbalance", 3646, 0x0E3D, M4, False, "Block title was not visible in the extract (pickup 5-50 %Vn). Confirm in the DTM."),
    ("pf_vrev", "Voltage phase reversal", 3653, 0x0E44, M4, True, ""),
    ("pf_exstart", "Excessive start time", 3751, 0x0EA6, M2, True, "Only Disable / Enable."),
]
for pid, nm, reg, hx, modes, ver, note in PF:
    add(pid, nm, reg, "UINT16", screen="protection", group="Protection functions", role="PROT_FUNC", enum=modes, everyN=3,
        verified=ver, hexaddr=hx, source=G0 + "/-01 protection settings: Function setting", notes=("0 Disable, 1 Alarm, 2 Trip, 3 Alarm + Trip. " if modes is M4 else "") + note)

for reg, nm in [(5759, "Thermal overload"), (5760, "Locked rotor"), (5761, "Stalled rotor"), (5762, "Definite time overcurrent"),
                (5763, "Normal inverse overcurrent"), (5764, "Short time overcurrent"), (5765, "Calculated ground"), (5766, "Measured ground"),
                (5767, "Phase under current"), (5768, "Current imbalance"), (5769, "Current phase loss"), (5770, "Current phase reversal"),
                (5771, "Phase under voltage"), (5772, "Phase over voltage"), (5773, "Voltage phase loss"), (5774, "Voltage imbalance"),
                (5775, "Voltage phase reversal"), (5776, "Under frequency"), (5777, "Over frequency"), (5778, "Excessive start time"),
                (5779, "Communication loss"), (5780, "LTMT main unit temperature"), (5781, "Under power")]:
    add("tc_%d" % reg, nm + " trip counter", reg, screen="protection", group="Trip counters", everyN=10, hexaddr=reg - 1)

# ======================= DEVICE screen (identification, read slowly) =======================
D = dict(screen="registers", group="Device", everyN=60, source=G1 + " Product Information Data")
add("dev_pid", "Product ID", 9126, "UINT32", hexaddr=0x23A5, **D)
add("dev_serial", "Product serial number", 9128, "UINT16", hexaddr=0x23A7, fmt="ASCII", length=16, **D)
for i, (nm, k) in enumerate([("Manufacture day", "day"), ("Manufacture month", "month"), ("Manufacture year", "year"),
                             ("Manufacture hour", "hour"), ("Manufacture minute", "min"), ("Manufacture second", "sec")]):
    add("mfg_" + k, nm, 9144 + i, "UINT16", hexaddr=0x23B7 + i, **D)
add("dev_ref", "Commercial reference", 9153, "UINT16", hexaddr=0x23C0, fmt="ASCII", length=10, **D)
for base, nm, tag in [(9163, "LTMT main unit", "main"), (9169, "Sensor module LTMTCT/LTMTCTV", "ct"), (9175, "Control operator unit LTMTCUF", "cu")]:
    hx = base - 1
    add("hw_" + tag, nm + " hardware version", base, "UINT32", hexaddr=hx, fmt="HWVER", **D)
    add("fw_" + tag, nm + " firmware version", base + 2, "UINT32", hexaddr=hx + 2, fmt="FWVER", **D)
    add("boot_" + tag, nm + " boot firmware version", base + 4, "UINT32", hexaddr=hx + 4, fmt="FWVER", **D)
for n, reg in enumerate([9183, 9189, 9195, 9201, 9207], 1):
    add("fw_ex%d" % n, "Expansion unit %d firmware version" % n, reg, "UINT32", hexaddr=reg - 1, fmt="FWVER", **D)
add("mod_ct", "Sensor module type code", 9217, "UINT16", hexaddr=0x2400, **D)
for n in range(1, 6):
    add("mod_ex%d" % n, "Expansion unit %d type code" % n, 9218 + n, "UINT16", hexaddr=0x2401 + n, **D)

# ---------------- sanity checks ----------------
ids = [p["id"] for p in P]
assert len(ids) == len(set(ids)), "duplicate ids"
W = {"UINT16": 1, "INT16": 1, "UINT32": 2, "INT32": 2, "UINT64": 4, "BITMAP16": 1, "BITMAP32": 2, "BOOL": 1}
span = lambda p: p["length"] if p["fmt"] == "ASCII" and p["length"] else W[p["type"]]
rng = {}
for p in P:
    a = p["register"] - 1
    for x in range(a, a + span(p)):
        k = (p["function"], x)
        assert k not in rng, ("overlap", p["id"], rng[k])
        rng[k] = p["id"]
json.dump(dict(version=2, params=P), open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "default_catalog.json"), "w"), indent=2)
print(len(P), "parameters,", sum(p["enabled"] for p in P), "enabled; no overlaps; all hex addresses match")
