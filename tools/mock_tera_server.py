#!/usr/bin/env python3
"""
Mock TeSys Tera Modbus TCP server for TeSys Tera Connect (no hardware needed).

  python3 mock_tera_server.py            # listens on 0.0.0.0:1502
In the app: Connect tab -> TCP / IP -> IP of this PC, port 1502, Unit ID 1 -> Connect.

Supports FC01/02 (bits), FC03/04 (registers) and FC06 (write one register).
Writes are accepted ONLY for the command registers (addresses 9875 and 704) and the protection
"Function setting" registers. Command bits behave like a DOL starter:
  0x0001 start   0x1000 stop (wins over start)   0x0008 trip reset
Registers are numbered as in the guide (register = address + 1); the server converts.
"""
import argparse, math, random, socketserver, struct, time

T0 = time.time()
regs = {}      # register number (1-based) -> 16-bit value
bits = {}      # discrete address (0-based) -> 0/1
st = {"run": 1, "tripped": 0, "cmd": 0}
FUNC_REGS = [3501, 3519, 3525, 3531, 3539, 3546, 3553, 3560, 3567, 3575, 3626, 3634, 3641, 3646, 3653, 3751]
CMD_ADDRS = (9875, 704)

def put32(reg, val):
    regs[reg] = (val >> 16) & 0xFFFF
    regs[reg + 1] = val & 0xFFFF

def put_ascii(reg, text, n):
    text = text.ljust(n * 2)[: n * 2]
    for i in range(n):
        regs[reg + i] = (ord(text[2 * i]) << 8) | ord(text[2 * i + 1])

def bcd(x): return ((x // 10) << 4) | (x % 10)

def datetime_words(y, mo, d, h, mi, s):
    return [bcd(s) << 8, (bcd(h) << 8) | bcd(mi), (bcd(mo) << 8) | bcd(d), (bcd(y // 100) << 8) | bcd(y % 100)]

def init_static():
    for r in range(6001, 6001 + 32 * 20): regs[r] = 0          # 20 trip logs x 32 registers
    for r in range(7001, 7001 + 8 * 100): regs[r] = 0         # 100 event logs x 8 registers
    for r in range(9126, 9226): regs[r] = 0                   # product information
    for r in FUNC_REGS: regs[r] = 2
    regs[3501] = 3; regs[3751] = 1
    regs[4467] = 450                                              # FLC1 45.0 A
    for a in CMD_ADDRS: regs[a + 1] = 0
    put_ascii(9128, "LS26071L0100131", 16)
    put32(9126, 0x0001)
    for i, v in enumerate([12, 3, 2026, 9, 15, 30]): regs[9144 + i] = v
    put_ascii(9153, "LTMTEFM", 10)
    for base, fw in [(9163, 0x00000100), (9169, 0x00000200), (9175, 0x00000100)]:
        put32(base, 0x0041 << 16 | 0x0000)                        # hardware version (letter A)
        put32(base + 2, fw); put32(base + 4, fw)
    for r in (9183, 9189, 9195, 9201, 9207): put32(r, 0)
    regs[9217] = 25
    for n in range(1, 6): regs[9218 + n] = 0
    # trip log: 5 records, newest first (32 registers each, from register 6001)
    for i, (code, y, mo, d, h, mi) in enumerate([(1, 2026, 10, 9, 8, 15), (10, 2026, 10, 8, 17, 2), (3, 2026, 10, 7, 11, 40),
                                                 (21, 2026, 10, 5, 9, 5), (2, 2026, 10, 2, 14, 22)]):
        base = 6001 + 32 * i
        for k, w in enumerate(datetime_words(y, mo, d, h, mi, 12)): regs[base + k] = w
        regs[base + 4] = code; regs[base + 5] = 96 - i * 7
        put32(base + 6, 62000 - i * 3000); put32(base + 8, 61000); put32(base + 10, 60500)
        regs[base + 16] = 150 + i * 20; regs[base + 18] = 4000; regs[base + 19] = 3990; regs[base + 20] = 4010; regs[base + 23] = 5001
    for i, code in enumerate([898, 1, 2, 641, 642]):
        base = 7001 + 8 * i
        for k, w in enumerate(datetime_words(2026, 10, 9 - i, 8, 15 + i, 0)): regs[base + k] = w
        regs[base + 4] = code

def refresh():
    t = time.time() - T0
    run = st["run"] and not st["tripped"]
    base = (36 + 16 * math.sin(t / 12.0)) if run else 0.0
    i = [max(0.0, base + random.uniform(-0.4, 0.4) + d) if run else 0.0 for d in (0.0, 0.6, -0.5)]
    for reg, a in zip((5501, 5503, 5505), i): put32(reg, int(a * 1000))
    put32(5507, 120); put32(5509, 150)
    iavg = sum(i) / 3
    put32(5511, int(iavg * 1000))
    regs[5513] = int(((max(i) - min(i)) / iavg) * 10000) if iavg else 0
    regs[5514] = 1
    for reg, v in zip((5515, 5517, 5519), (400.2, 399.1, 401.4)): put32(reg, int(v * 10))
    put32(5521, 4003); regs[5523] = 45; regs[5524] = 1
    regs[5525] = 5001; regs[5526] = 86
    put32(5527, int(iavg * 0.4 * 1.732 * 0.86 * 1000)); put32(5529, 9000); put32(5531, int(iavg * 0.4 * 1.732 * 1000))
    regs[5533] = regs[5534] = regs[5535] = 0; regs[5536] = 1234567 & 0xFFFF; regs[5535] = 1234567 >> 16
    for r in range(5537, 5545): regs[r] = 0
    for r in range(5545, 5551): regs[r] = 25
    regs[5558] = 4 if run else 1
    regs[5559] = min(100, int(40 + 35 * (base - 20) / 32)) if run else 35
    put32(5560, 900); put32(5562, 120)
    put32(5564 - 1 + 1, 0)
    put32(5567, 210000); put32(5569, 2500)
    put32(5571, 7260); put32(5573, 95)
    regs[5575] = 42; regs[5576] = 41; regs[5577] = 0; regs[5578] = 3
    put32(5646, 0x00000001 if st["tripped"] else 0)
    for r in range(5759, 5782): regs[r] = 2 if r == 5759 else 0
    # discrete bits
    bits[240] = 0 if run else 1; bits[241] = 0; bits[242] = 1 if run else 0; bits[243] = 1 if st["tripped"] else 0; bits[244] = 1
    bits[224] = 0; bits[225] = 0; bits[226] = 1 if st["tripped"] else 0
    for k in range(4): bits[k] = (1 if k == 0 and run else 0)
    for k in range(3): bits[32 + k] = 1 if k == 0 and run else 0
    for a in range(576, 603): bits[a] = 1 if (a == 578 and st["tripped"]) else 0

class H(socketserver.BaseRequestHandler):
    def handle(self):
        s = self.request
        while True:
            hdr = self._recv(s, 7)
            if not hdr: return
            tid, pid, ln, unit = struct.unpack(">HHHB", hdr)
            pdu = self._recv(s, ln - 1)
            if not pdu: return
            fc = pdu[0]
            refresh()
            resp = bytes([fc | 0x80, 1])
            if fc in (1, 2) and len(pdu) == 5:
                addr, qty = struct.unpack(">HH", pdu[1:5])
                if 1 <= qty <= 1900 and all(a in bits for a in range(addr, addr + qty)):
                    nb = (qty + 7) // 8
                    data = bytearray(nb)
                    for k in range(qty):
                        if bits[addr + k]: data[k // 8] |= 1 << (k % 8)
                    resp = bytes([fc, nb]) + bytes(data)
                else:
                    resp = bytes([fc | 0x80, 2])
            elif fc in (3, 4) and len(pdu) == 5:
                addr, qty = struct.unpack(">HH", pdu[1:5])
                regno = addr + 1
                if not (1 <= qty <= 125) or any(r not in regs for r in range(regno, regno + qty)):
                    resp = bytes([fc | 0x80, 2])
                else:
                    data = b"".join(struct.pack(">H", regs[r]) for r in range(regno, regno + qty))
                    resp = bytes([fc, len(data)]) + data
            elif fc == 6 and len(pdu) == 5:
                addr, val = struct.unpack(">HH", pdu[1:5])
                if addr in CMD_ADDRS:
                    if val & 0x1000: st["run"] = 0
                    elif val & 0x0001 and not st["tripped"]: st["run"] = 1
                    if val & 0x0008: st["tripped"] = 0
                    regs[addr + 1] = val
                    print("WRITE cmd addr %d (0x%04X) = 0x%04X  run=%d tripped=%d" % (addr, addr, val, st["run"], st["tripped"]))
                    resp = pdu
                elif (addr + 1) in FUNC_REGS:
                    regs[addr + 1] = val & 3
                    print("WRITE protection reg %d = %d" % (addr + 1, val))
                    resp = pdu
                else:
                    resp = bytes([fc | 0x80, 2])
            s.sendall(struct.pack(">HHHB", tid, 0, len(resp) + 1, unit) + resp)

    @staticmethod
    def _recv(s, n):
        b = b""
        while len(b) < n:
            c = s.recv(n - len(b))
            if not c: return None
            b += c
        return b

class S(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

if __name__ == "__main__":
    ap = argparse.ArgumentParser(); ap.add_argument("--port", type=int, default=1502)
    a = ap.parse_args(); init_static(); refresh()
    print(f"Mock TeSys Tera on 0.0.0.0:{a.port}  (Ctrl+C to stop)")
    S(("0.0.0.0", a.port), H).serve_forever()
