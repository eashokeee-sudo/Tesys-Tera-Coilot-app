#!/usr/bin/env python3
"""
Mock TeSys Tera Modbus TCP server for testing the TeSys Tera Lab app without hardware.
  python3 mock_tera_server.py            # listens on 0.0.0.0:1502
In the app: Modbus tab -> TCP / IP -> IP of this PC, port 1502, Unit ID 1 -> Connect.
Read-only: write function codes are answered with exception 01.
"""
import argparse, math, random, socketserver, struct, time

T0 = time.time()
regs = {}

def put32(reg, val):
    regs[reg] = (val >> 16) & 0xFFFF
    regs[reg + 1] = val & 0xFFFF

def refresh():
    t = time.time() - T0
    base = 36 + 16 * math.sin(t / 12.0)
    i = [base + random.uniform(-0.4, 0.4) + d for d in (0.0, 0.6, -0.5)]
    for reg, a in zip((5501, 5503, 5505), i):
        put32(reg, int(a * 1000))
    put32(5507, 120); put32(5509, 150)
    iavg = sum(i) / 3
    put32(5511, int(iavg * 1000))
    regs[5513] = int(((max(i) - min(i)) / iavg) * 10000)
    regs[5514] = 1
    for reg, v in zip((5515, 5517, 5519), (400.2, 399.1, 401.4)):
        put32(reg, int(v * 10))
    put32(5521, 4003); regs[5523] = 45; regs[5524] = 1
    regs[5525] = 5001; regs[5526] = 86
    put32(5527, int(iavg * 0.4 * 1.732 * 0.86 * 1000)); put32(5529, 9000); put32(5531, int(iavg * 0.4 * 1.732 * 1000))
    regs[5558] = 4
    regs[5559] = min(100, int(40 + 35 * (base - 20) / 32))
    put32(5560, 900); put32(5562, 120)
    put32(5567, 210000); put32(5569, 2500)
    put32(5571, 7260); put32(5573, 95)
    regs[5575] = 42; regs[5576] = 41; regs[5577] = 0; regs[5578] = 3
    put32(5646, 0x00000008 if (int(t) // 20) % 2 else 0)
    for r in range(5759, 5782):
        regs[r] = (r - 5759) % 4
    regs[4467] = 450                                             # FLC1 = 45.0 A (UINT16, 0.1 A)

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
            if fc in (3, 4) and len(pdu) == 5:
                addr, qty = struct.unpack(">HH", pdu[1:5])
                regno = addr + 1
                if not (1 <= qty <= 125) or any(r not in regs for r in range(regno, regno + qty)):
                    resp = bytes([fc | 0x80, 2])
                else:
                    data = b"".join(struct.pack(">H", regs[r]) for r in range(regno, regno + qty))
                    resp = bytes([fc, len(data)]) + data
            else:
                resp = bytes([fc | 0x80, 1])
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
    a = ap.parse_args(); refresh()
    print(f"Mock TeSys Tera on 0.0.0.0:{a.port}  (Ctrl+C to stop)")
    S(("0.0.0.0", a.port), H).serve_forever()
