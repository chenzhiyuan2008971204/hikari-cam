"""Compact binary 3D LUT ("HLUT") for the app's assets, lossless against the 4-decimal .cube text it replaces.

    python3 lutbin.py --selftest          encode/decode round trip on random and extreme tables

Layout (little endian):
    0   4  magic "HLUT"
    4   2  version (1)
    6   2  size n (points per axis, 2..128)
    8   4  scale (10000: a value v is stored as round(v * 10000), exactly the 4 decimals of the .cube text)
    12  4  reserved (0)
    16     n*n*n*3 int16: R, G, B of every grid point, red fastest (.cube order), each row of n red values
           stored as differences from the previous red value (the first of a row from 0)

The data is smooth along red, so the differences deflate far better than the raw values (the APK
compresses assets). Values are in 0..1, so every stored number fits int16 with room to spare.
"""
import struct, sys
import numpy as np

MAGIC = b"HLUT"
VERSION = 1
SCALE = 10000
HEADER = 16


def encode(table):
    """table: (n, n, n, 3) floats indexed [b][g][r], values 0..1 -> bytes"""
    n = table.shape[0]
    assert table.shape == (n, n, n, 3) and 2 <= n <= 128
    q = np.round(np.asarray(table, float) * SCALE).astype(np.int64)
    assert q.min() >= 0 and q.max() <= SCALE, "LUT values must be in 0..1"
    d = np.diff(q, axis=2, prepend=0)          # along red
    return MAGIC + struct.pack("<HHII", VERSION, n, SCALE, 0) + d.astype("<i2").tobytes()


def decode(data):
    """bytes -> (n, table (n, n, n, 3) float, indexed [b][g][r]); values are the exact 4-decimal numbers"""
    if data[:4] != MAGIC:
        raise ValueError("not an HLUT file")
    version, n, scale, _ = struct.unpack("<HHII", data[4:HEADER])
    if version != VERSION or scale != SCALE or not 2 <= n <= 128:
        raise ValueError(f"unsupported HLUT (version {version}, size {n}, scale {scale})")
    if len(data) != HEADER + n * n * n * 3 * 2:
        raise ValueError("HLUT length does not match its size")
    d = np.frombuffer(data, dtype="<i2", offset=HEADER).astype(np.int64).reshape(n, n, n, 3)
    return n, np.cumsum(d, axis=2) / SCALE


def write(path, table):
    with open(path, "wb") as f:
        f.write(encode(table))


def read(path):
    with open(path, "rb") as f:
        return decode(f.read())[1]


def selftest():
    rng = np.random.default_rng(1)
    for n in (2, 5, 33):
        t = np.round(rng.random((n, n, n, 3)), 4)
        t[0, 0, 0] = 0; t[-1, -1, -1] = 1
        assert np.array_equal(decode(encode(t))[1], t), n
    ext = np.zeros((33, 33, 33, 3)); ext[:, :, ::2] = 1     # worst case for the differences: 0 <-> 1 every point
    assert np.array_equal(decode(encode(ext))[1], ext)
    for bad in (b"XXXX" + encode(t)[4:], encode(t)[:-2], encode(t)[:4] + struct.pack("<HHII", 2, 33, SCALE, 0) + encode(t)[16:]):
        try:
            decode(bad); raise SystemExit("bad data accepted")
        except ValueError:
            pass
    print("lutbin selftest ok")


if __name__ == "__main__":
    if "--selftest" in sys.argv: selftest()
