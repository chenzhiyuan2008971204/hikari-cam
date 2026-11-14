"""Export the 12 Hikari looks for Adobe Lightroom Classic / Camera Raw.

    python3 export_lrc.py OUT_DIR

Writes, per look:
  profiles/Hikari <name>.xmp   creative profile: the look's colour recipe as a 32^3 RGBTable
                               (same OKLab recipe that is baked into the app's LUT)
  presets/Hikari <name>.xmp    develop preset: grain amount / size / roughness and vignette
                               approximating the look's film grain and lens character
Halation, bloom, light leaks and the other GPU effects have no Lightroom equivalent and are not
exported. Each profile is decoded again and compared with the recipe before it is written.
"""
import os
import sys
import uuid

import numpy as np

import look as engine
import xmp
from films import FILMS, SHIP

PRESET = """<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 7.0">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/"
   crs:PresetType="Normal"
   crs:Cluster=""
   crs:UUID="{uid}"
   crs:SupportsAmount="False"
   crs:SupportsColor="True"
   crs:SupportsMonochrome="True"
   crs:SupportsHighDynamicRange="True"
   crs:SupportsNormalDynamicRange="True"
   crs:SupportsSceneReferred="True"
   crs:SupportsOutputReferred="True"
   crs:CameraModelRestriction=""
   crs:Copyright=""
   crs:ContactInfo=""
   crs:Version="14.3"
   crs:ProcessVersion="11.0"
   crs:GrainAmount="{grain}"
   crs:GrainSize="{gsize}"
   crs:GrainFrequency="{grough}"
   crs:PostCropVignetteAmount="{vig}"
   crs:PostCropVignetteMidpoint="50"
   crs:PostCropVignetteFeather="{feather}"
   crs:PostCropVignetteRoundness="0"
   crs:PostCropVignetteStyle="1"
   crs:PostCropVignetteHighlightContrast="0"
   crs:HasSettings="True">
   <crs:Name>
    <rdf:Alt><rdf:li xml:lang="x-default">{name}</rdf:li></rdf:Alt>
   </crs:Name>
   <crs:Group>
    <rdf:Alt><rdf:li xml:lang="x-default">Hikari 光</rdf:li></rdf:Alt>
   </crs:Group>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
"""


def main(out):
    os.makedirs(os.path.join(out, "profiles"), exist_ok=True)
    os.makedirs(os.path.join(out, "presets"), exist_ok=True)
    for key in SHIP:
        f = FILMS[key]
        lk = f["colour"]
        table = xmp.bake_rgb_slowest(lambda c, lk=lk: engine.apply(lk, c))
        text = xmp.profile_xmp(table, f"Hikari {f['name']}", "Hikari 光", f"{f['zh']}：{f['note']}")
        back = xmp.read_table_from_xmp(text)
        err = np.abs(back - table).max()
        assert err < 1 / 4000, f"{key}: profile round trip error {err}"
        with open(os.path.join(out, "profiles", f"Hikari {f['name']}.xmp"), "w", encoding="utf-8") as fh:
            fh.write(text)
        fx = f["fx"]
        g, lens = fx["grain"], fx["lens"]
        preset = PRESET.format(
            uid=uuid.uuid5(uuid.NAMESPACE_URL, "hikari-cam/preset/" + key).hex.upper(),
            name=f"Hikari {f['name']} 顆粒與暗角",
            grain=round(min(100, g["amount"] * 60)),
            gsize=round(min(100, 15 + g["size"] * 1000 * 15)),
            grough=round(min(100, 30 + g["rough"] * 60)),
            vig=-round(min(100, lens["vignette"] * 60)),
            feather=round(50 + lens["vignetteSoft"] * 40),
        )
        with open(os.path.join(out, "presets", f"Hikari {f['name']} 顆粒與暗角.xmp"), "w", encoding="utf-8") as fh:
            fh.write(preset)
        print(f"{key:<15} profile ok (round trip {err:.1e})  grain {g['amount']:.2f} vignette {lens['vignette']:.2f}")


if __name__ == "__main__":
    main(sys.argv[1])
