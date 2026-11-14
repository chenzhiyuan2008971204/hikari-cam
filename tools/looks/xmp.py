"""Write a look as an Adobe Lightroom / Camera Raw creative profile (.xmp with an RGBTable).

The table format is not published by Adobe. This follows the reverse-engineered layout used by
several independent tools (validated against shipped Adobe profiles):

  block   = header 4 x u32 {1, 1, 3, size}
            size^3 x 3 x u16 deltas from the identity ramp, R slowest / B fastest
            footer 3 x u32 {primaries, gamma, gamut} + 2 x f64 amount range {0, 2}
  payload = u32 len(block) ++ zlib(block)
  text    = Adobe base85 (4 bytes -> 5 chars, little endian, least significant digit first)
  id      = MD5 of the uncompressed block, used in crs:RGBTable and crs:Table_<id>

Our LUTs map display sRGB to display sRGB, so the footer says sRGB primaries + sRGB gamma.
Camera Raw caps the grid at 32, so the look is baked at 32^3 straight from the engine.
"""
import hashlib
import struct
import uuid
import zlib
from xml.sax.saxutils import escape

import numpy as np

B85 = b"0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-:+=^!/*?`'|()[]{}@%$#"
SIZE = 32
SRGB_PRIMARIES, SRGB_GAMMA, GAMUT_CLIP = 0, 1, 0


def b85_encode(data: bytes) -> str:
    out = bytearray()
    full = len(data) - len(data) % 4
    for i in range(0, full, 4):
        x = struct.unpack_from("<I", data, i)[0]
        for _ in range(5):
            out.append(B85[x % 85])
            x //= 85
    rest = len(data) - full
    if rest:
        x = int.from_bytes(data[full:] + b"\0" * (4 - rest), "little")
        for _ in range(rest + 1):
            out.append(B85[x % 85])
            x //= 85
    return out.decode("ascii")


def b85_decode(text: str) -> bytes:
    idx = {c: i for i, c in enumerate(B85.decode())}
    out = bytearray()
    for i in range(0, len(text), 5):
        chunk = text[i:i + 5]
        x = 0
        for j, ch in enumerate(chunk):
            x += idx[ch] * 85 ** j
        out += (x & 0xFFFFFFFF).to_bytes(4, "little")[: len(chunk) - 1 if len(chunk) < 5 else 4]
    return bytes(out)


def table_block(table: np.ndarray) -> bytes:
    """table[r][g][b] -> rgb in 0..1, shape (n, n, n, 3), R slowest / B fastest."""
    n = table.shape[0]
    nop = np.array([(i * 0xFFFF + (n >> 1)) // (n - 1) for i in range(n)], dtype=np.int64)
    v = np.round(np.clip(table, 0, 1) * 65535).astype(np.int64)
    d = np.empty_like(v)
    d[..., 0] = (v[..., 0] - nop.reshape(n, 1, 1)) & 0xFFFF
    d[..., 1] = (v[..., 1] - nop.reshape(1, n, 1)) & 0xFFFF
    d[..., 2] = (v[..., 2] - nop.reshape(1, 1, n)) & 0xFFFF
    return (struct.pack("<IIII", 1, 1, 3, n)
            + d.astype("<u2").tobytes()
            + struct.pack("<III", SRGB_PRIMARIES, SRGB_GAMMA, GAMUT_CLIP)
            + struct.pack("<dd", 0.0, 2.0))


def decode_block(block: bytes) -> np.ndarray:
    """Inverse of table_block, for self-checks."""
    _, _, dims, n = struct.unpack_from("<IIII", block, 0)
    assert dims == 3
    d = np.frombuffer(block, dtype="<u2", count=n ** 3 * 3, offset=16).astype(np.int64).reshape(n, n, n, 3)
    nop = np.array([(i * 0xFFFF + (n >> 1)) // (n - 1) for i in range(n)], dtype=np.int64)
    v = np.empty_like(d)
    v[..., 0] = (d[..., 0] + nop.reshape(n, 1, 1)) & 0xFFFF
    v[..., 1] = (d[..., 1] + nop.reshape(1, n, 1)) & 0xFFFF
    v[..., 2] = (d[..., 2] + nop.reshape(1, 1, n)) & 0xFFFF
    return v / 65535.0


def bake_rgb_slowest(apply, n=SIZE):
    g = np.linspace(0, 1, n)
    r, gg, b = np.meshgrid(g, g, g, indexing="ij")  # r slowest, b fastest
    return apply(np.stack([r, gg, b], -1).reshape(-1, 3)).reshape(n, n, n, 3)


def profile_xmp(table: np.ndarray, name: str, group: str, description: str) -> str:
    block = table_block(table)
    md5 = hashlib.md5(block).hexdigest().upper()
    payload = struct.pack("<I", len(block)) + zlib.compress(block, 9)
    enc = b85_encode(payload)
    uid = uuid.uuid5(uuid.NAMESPACE_URL, "hikari-cam/" + name + "/" + md5).hex.upper()
    lang = lambda s: f'<rdf:Alt><rdf:li xml:lang="x-default">{escape(s)}</rdf:li></rdf:Alt>'
    return f"""<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 7.0">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/"
   crs:PresetType="Look"
   crs:Cluster=""
   crs:UUID="{uid}"
   crs:SupportsAmount="True"
   crs:SupportsColor="True"
   crs:SupportsMonochrome="True"
   crs:SupportsHighDynamicRange="True"
   crs:SupportsNormalDynamicRange="True"
   crs:SupportsSceneReferred="True"
   crs:SupportsOutputReferred="True"
   crs:RequiresRGBTables="False"
   crs:CameraModelRestriction=""
   crs:Copyright=""
   crs:ContactInfo=""
   crs:Version="14.3"
   crs:ProcessVersion="11.0"
   crs:ConvertToGrayscale="False"
   crs:RGBTable="{md5}"
   crs:Table_{md5}="{enc}"
   crs:HasSettings="True">
   <crs:Name>
    {lang(name)}
   </crs:Name>
   <crs:Group>
    {lang(group)}
   </crs:Group>
   <crs:Description>
    {lang(description)}
   </crs:Description>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
"""


def read_table_from_xmp(text: str) -> np.ndarray:
    import re
    m = re.search(r'crs:Table_([0-9A-Fa-f]+)="([^"]+)"', text)
    ref = re.search(r'crs:RGBTable="([^"]+)"', text).group(1)
    assert m and m.group(1) == ref, "RGBTable must reference the Table_ id"
    payload = b85_decode(m.group(2))
    n_raw = struct.unpack_from("<I", payload, 0)[0]
    block = zlib.decompress(payload[4:])
    assert len(block) == n_raw, "length prefix mismatch"
    assert hashlib.md5(block).hexdigest().upper() == ref.upper(), "MD5 mismatch"
    return decode_block(block)
