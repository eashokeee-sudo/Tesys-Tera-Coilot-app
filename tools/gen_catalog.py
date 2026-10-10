"""Regenerates app/src/main/assets/default_catalog.json. Every register is asserted against its hex address from DOCA0355EN."""
import json, os
G = "DOCA0355EN Modbus RTU guide"
P = []
def add(id, name, reg, type="UINT16", scale=1.0, unit="", screen="registers", group="", role="", tile=False,
        fc=3, enum=None, bits=None, bitStyle="STATE", everyN=1, enabled=True, verified=True, source=G, notes="", dec=-1, hexaddr=None):
    if hexaddr is not None:
        assert reg - 1 == hexaddr, (id, reg, hex(reg-1), hex(hexaddr))
    P.append(dict(id=id, name=name, register=reg, type=type, function=fc, scale=scale, unit=unit, decimals=dec,
                  screen=screen, group=group, role=role, tile=tile, bitStyle=bitStyle, everyN=everyN, enabled=enabled,
                  verified=verified, source=source, notes=notes,
                  enum={str(k): v for k, v in (enum or {}).items()},
                  bits=[dict(bit=b, name=n) for b, n in (bits or [])]))

add("motor_status", "Motor status", 5558, enum={1:"STOP",2:"START",4:"RUN"}, screen="motor", group="Motor state", role="MOTOR_STATE", hexaddr=0x15B5)
add("stop_cause", "Motor stop cause", 5577, screen="motor", group="Motor state", hexaddr=0x15C8, everyN=2,
    enum={0:"None",1:"HMI",2:"Local DI",3:"Remote DI",4:"Communication",5:"Auto restart",6:"Trip",7:"Auto",8:"Forced stop",
          9:"Direction change",10:"No feedback",11:"Speed change",12:"Custom stop",13:"Mode transfer",14:"Device internal error",15:"No voltage"})
add("n_starts", "Number of starts", 5575, screen="motor", group="Counters", hexaddr=0x15C6, everyN=5)
add("n_stops", "Number of stops", 5576, screen="motor", group="Counters", hexaddr=0x15C7, everyN=5)
add("trip_counter", "Trip counter", 5578, screen="motor", group="Counters", hexaddr=0x15C9, everyN=5)
add("run_total", "Total run hours", 5571, "UINT32", 1/60, "h", "motor", "Counters", hexaddr=0x15C2, everyN=10, dec=1,
    notes="Guide unit is minutes (scale 1). Shown in hours here: raw / 60.")
add("start_peak", "Motor starting peak current", 5567, "UINT32", 0.001, "A", "motor", "Last start", hexaddr=0x15BE, everyN=5)
add("start_time", "Motor starting time", 5569, "UINT32", 0.001, "s", "motor", "Last start", hexaddr=0x15C0, everyN=5)

add("i_l1", "L1", 5501, "UINT32", 0.001, "A", "electrical", "Phase currents", tile=True, hexaddr=0x157C)
add("i_l2", "L2", 5503, "UINT32", 0.001, "A", "electrical", "Phase currents", tile=True, hexaddr=0x157E)
add("i_l3", "L3", 5505, "UINT32", 0.001, "A", "electrical", "Phase currents", tile=True, hexaddr=0x1580)
add("i_avg", "Average current (IAVG)", 5511, "UINT32", 0.001, "A", "electrical", "Current summary", role="IAVG", hexaddr=0x1586)
add("i_imb", "Current imbalance", 5513, "UINT16", 0.01, "%", "electrical", "Current summary", hexaddr=0x1588)
add("i_seq", "Current phase sequence", 5514, enum={0:"-",1:"L123",2:"L132",3:"CT wiring error"}, screen="electrical", group="Current summary", hexaddr=0x1589, everyN=5)
add("i_gnd_m", "Measured ground current", 5507, "UINT32", 0.001, "A", "electrical", "Ground current", hexaddr=0x1582, everyN=2)
add("i_gnd_c", "Calculated ground current", 5509, "UINT32", 0.001, "A", "electrical", "Ground current", hexaddr=0x1584, everyN=2)
add("v_l12", "L1-L2", 5515, "UINT32", 0.1, "V", "electrical", "Line-to-line voltages", tile=True, hexaddr=0x158A, notes="Single phase: this register is L1-N.")
add("v_l23", "L2-L3", 5517, "UINT32", 0.1, "V", "electrical", "Line-to-line voltages", tile=True, hexaddr=0x158C)
add("v_l31", "L3-L1", 5519, "UINT32", 0.1, "V", "electrical", "Line-to-line voltages", tile=True, hexaddr=0x158E)
add("v_avg", "Average voltage", 5521, "UINT32", 0.1, "V", "electrical", "Voltage summary", hexaddr=0x1590)
add("v_imb", "Voltage imbalance", 5523, "UINT16", 0.01, "%", "electrical", "Voltage summary", hexaddr=0x1592)
add("v_seq", "Voltage phase sequence", 5524, enum={0:"-",1:"L123",2:"L132"}, screen="electrical", group="Voltage summary", hexaddr=0x1593, everyN=5)
add("freq", "System frequency", 5525, "UINT16", 0.01, "Hz", "electrical", "Power", hexaddr=0x1594)
add("pf", "Power factor", 5526, "UINT16", 0.01, "", "electrical", "Power", hexaddr=0x1595)
add("p_act", "Active power", 5527, "UINT32", 0.001, "kW", "electrical", "Power", hexaddr=0x1596)
add("p_rea", "Reactive power", 5529, "UINT32", 0.001, "kvar", "electrical", "Power", hexaddr=0x1598)
add("p_app", "Apparent power", 5531, "UINT32", 0.001, "kVA", "electrical", "Power", hexaddr=0x159A)

add("flc1", "Full load current (FLC1)", 4467, "UINT16", 0.1, "A", "protection", "Load", role="FLC", hexaddr=0x1172,
    source="DOCA0355EN System Settings p.105: 0x1172 / reg 4467, UINT16, 0.1 A, range 1-10000",
    notes="Setting register (read-only here). Register 4466 (0x1171) is Reserved in the guide.")
add("flc2", "Speed 2 full load current (FLC2)", 4468, "UINT16", 0.1, "A", "protection", "Load", hexaddr=0x1173, enabled=False,
    source="DOCA0355EN System Settings p.105: 0x1173 / reg 4468, UINT16, 0.1 A",
    notes="Only meaningful for two-speed starters. Enable if needed.")
add("thermal", "Thermal capacity (thermal memory)", 5559, "UINT16", 1, "%", "protection", "Thermal", role="THERMAL", hexaddr=0x15B6)
add("t_trip", "Thermal time to trip", 5560, "UINT32", 1, "s", "protection", "Thermal", hexaddr=0x15B7, everyN=2)
add("t_cool", "Thermal time to cool", 5562, "UINT32", 1, "s", "protection", "Thermal", hexaddr=0x15B9, everyN=5)
add("trip_status", "Trip status (32-bit)", 5646, "BITMAP32", screen="protection", group="Trip status", bitStyle="TRIP",
    source="DOCA0355EN user-map example: 0x160D = Trip status MSW, 0x160E = LSW", hexaddr=0x160D,
    notes="Register confirmed from the guide example. Bit names are NOT filled in yet: open this parameter and add them as 'bit=name' lines once you have the protection function status table. Unnamed set bits are listed as 'bit N'.")

trip_counters = [
 (5759,"Thermal overload"),(5760,"Locked rotor"),(5761,"Stalled rotor"),(5762,"Definite time overcurrent"),
 (5763,"Normal inverse overcurrent"),(5764,"Short time overcurrent"),(5765,"Calculated ground"),(5766,"Measured ground"),
 (5767,"Phase under current"),(5768,"Current imbalance"),(5769,"Current phase loss"),(5770,"Current phase reversal"),
 (5771,"Phase under voltage"),(5772,"Phase over voltage"),(5773,"Voltage phase loss"),(5774,"Voltage imbalance"),
 (5775,"Voltage phase reversal"),(5776,"Under frequency"),(5777,"Over frequency"),(5778,"Excessive start time"),
 (5779,"Communication loss"),(5780,"LTMT main unit temperature"),(5781,"Under power")]
for reg, nm in trip_counters:
    add("tc_%d" % reg, nm + " trip counter", reg, screen="protection", group="Trip counters", everyN=10, hexaddr=reg-1)

note = ("Bit-parameter no. %d from the DOCA0355EN user-defined status word example (registers 10001-10704 area). "
        "FC02 addressing is NOT confirmed. Enable, then check with Registers > Quick read against the DTM.")
for n, nm, scr, grp in [(1,"Digital input 1","motor","Digital inputs"),(2,"Digital input 2","motor","Digital inputs"),
                        (226,"Alarm status","protection","Active status"),(227,"Trip status (any)","protection","Active status"),
                        (243,"Motor run","motor","Motor state"),(244,"Motor inhibit","motor","Motor state"),(241,"Motor stop","motor","Motor state")]:
    add("bit_%d" % n, nm, 10000 + n, "BOOL", fc=2, screen=scr, group=grp, enabled=False, verified=False, source=G + " p.48", notes=note % n)

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "default_catalog.json")
json.dump(dict(version=1, params=P), open(out, "w"), indent=2)
print(len(P), "parameters written")
