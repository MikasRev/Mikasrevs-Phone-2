"""Build static phone-only CJK fonts; requires fontTools and the pinned sources in build/font-tools."""
import hashlib
import json
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools import subset

root = Path(__file__).resolve().parent.parent
sources = root / "build/font-tools"
output = root / "src/main/resources/assets/newphone/font"
commit = (sources / "noto-commit.txt").read_text().strip()
manifest = {"project": "https://github.com/notofonts/noto-cjk", "commit": commit, "weight": 600, "fonts": {}}

for region in ("sc", "tc"):
    source = sources / f"noto-{region}.ttf"
    font = TTFont(source)
    # Keep all BMP characters from the original, including players' own Chinese text.
    # Remove only the layout tables Minecraft's simple font renderer cannot use.
    options = subset.Options()
    options.layout_features = []
    options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14, 16, 17]
    options.name_legacy = True
    options.name_languages = [0x409]
    options.drop_tables += ["DSIG", "BASE", "vhea", "vmtx", "VORG"]
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=[c for c in font.getBestCmap() if c <= 0xFFFF])
    subsetter.subset(font)
    print(f"Instancing {region}: {len(font.getBestCmap())} characters", flush=True)
    font = instantiateVariableFont(font, {"wght": 600}, inplace=True, optimize=False)
    family = f"MPhone CJK {region.upper()}"
    for name_id, value in {1: family, 2: "SemiBold", 3: family + "-SemiBold", 4: family + " SemiBold",
                           6: "MPhoneCJK" + region.upper() + "-SemiBold", 16: family, 17: "SemiBold"}.items():
        font["name"].setName(value, name_id, 3, 1, 0x409)
        font["name"].setName(value, name_id, 1, 0, 0)
    target = output / f"phone_cjk_{region}.ttf"
    font.save(target)
    assert "fvar" not in font and "gvar" not in font
    manifest["fonts"][region] = {"source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                                "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
                                "characters": len(font.getBestCmap()), "bytes": target.stat().st_size}
    print(f"Built {target.name}: {target.stat().st_size} bytes", flush=True)

(output / "phone_cjk_sources.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
