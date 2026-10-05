"""Build phone UI fonts from a pinned Noto Sans variable TTF (fontTools 4.66.1)."""
import hashlib
import json
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

root = Path(__file__).resolve().parent.parent
source = root / "build/font-tools/noto-sans.ttf"
output = root / "src/main/resources/assets/newphone/font"
commit = (root / "build/font-tools/noto-sans-commit.txt").read_text().strip()
manifest = {"project": "https://github.com/google/fonts", "commit": commit,
            "source": "ofl/notosans/NotoSans[wdth,wght].ttf",
            "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "fonts": {}}
for weight, suffix in ((600, "semibold"), (700, "bold")):
    font = instantiateVariableFont(TTFont(source), {"wght": weight, "wdth": 100}, inplace=True)
    family = "MPhone UI"
    style = "SemiBold" if weight == 600 else "Bold"
    for name_id, value in {1: family, 2: style, 3: family + "-" + style, 4: family + " " + style,
                           6: "MPhoneUI-" + style, 16: family, 17: style}.items():
        font["name"].setName(value, name_id, 3, 1, 0x409)
        font["name"].setName(value, name_id, 1, 0, 0)
    target = output / f"phone_ui_{suffix}.ttf"
    font.save(target)
    assert "fvar" not in font and "gvar" not in font
    manifest["fonts"][suffix] = {"weight": weight, "characters": len(font.getBestCmap()),
                                "sha256": hashlib.sha256(target.read_bytes()).hexdigest()}
    print(f"Built {target.name}: {target.stat().st_size} bytes, {len(font.getBestCmap())} characters", flush=True)
(output / "phone_ui_sources.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
