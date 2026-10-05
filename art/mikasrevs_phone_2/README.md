# MikasRevs Phone 2

Finished low-poly handheld asset, created and rendered in Blender 4.5.3 LTS.

## Files

- `mikasrevs_phone_2.blend` — editable source, packed texture, organized asset and preview collections.
- `mikasrevs_phone_2.glb` — primary self-contained game export.
- `mikasrevs_phone_2.gltf` + `.bin` + `mikasrevs_phone_2_atlas.png` — equivalent separate-file export; keep together.
- `mikasrevs_phone_2.obj` + `.mtl` + atlas — fallback export; keep together. OBJ uses basic material properties and cannot represent all glTF PBR settings.
- `previews/` — front/rear three-quarter renders and all six orthographic inspection views.
- `validation.json` — topology, dimensions, UVs, transforms and per-object triangle counts.
- `export_validation.json` — GLB reimport and surface-intersection results.

## Geometry and materials

2,422 triangles across 14 named mesh objects. No subdivision or unapplied modeling modifiers. The body is 0.970 wide, 2.000 tall and 0.090 deep; camera bumps and buttons extend beyond the body. All mesh origins share the phone's center. Rotation is zero and scale is one.

The model includes three separate optical assemblies, a flash, front camera, earpiece, three right-side buttons, a recessed USB-C opening and six recessed speaker holes. Rear lettering is in the texture atlas; it adds no letter geometry. The graphite finish uses simple Principled materials. Camera glass is opaque, avoiding transparency sorting requirements.

Each mesh is a closed manifold solid with outward normals. Deliberately faceted bevels preserve the Minecraft silhouette. Tiny clearances between fitted pieces prevent coincident surface artifacts. Broad surfaces remain flat.

## Screen integration

`phone_screen` is an independent 12-triangle closed mesh. Its front consists of two coplanar triangles forming one exact rectangle. The front uses the full 0–1 UV square, with no permanent UI. Its thin sides/back intentionally reuse the blank display material and projected UVs. All other objects use a non-overlapping 512×512 atlas, including a dedicated high-resolution strip for the small logo.

Blender coordinates:

- +X: right when looking at the display; +Z: up; display normal: −Y.
- Screen front: Y = −0.0475.
- Screen bounds: X = −0.446 to +0.446; Z = −0.932 to +0.910.
- Front UV origin is lower-left, with U increasing right and V increasing upward.
- An additional GUI overlay can sit at Y = −0.0477, or replace the screen material directly.

GLB/glTF coordinates after Blender's standard axis conversion:

- +X: screen right; +Y: up; display normal: +Z.
- Screen front: Z = +0.0475; vertical bounds Y = −0.932 to +0.910.
- Overlay plane: Z = +0.0477, facing +Z.
- glTF export converts texture V to the glTF convention. Respect your loader's UV convention to avoid vertical inversion.

The standalone OBJ is also exported Y-up with the front facing +Z. The mod now uses a separate Forge OBJ export of this same mesh under `src/main/resources/assets/newphone/models/item/`. GLB remains an interchange asset; the runtime uses Forge's built-in `forge:obj` loader without adding a dependency.

The Forge export preserves the original handset placement and all first/third-person, inventory, ground and call transforms. The scale is 4.4 Minecraft model pixels per Blender unit, centered at (5, 13.5, 0.89) model pixels. Its screen faces -Z, occupies X 3.0376–6.9624 and Y 9.3992–17.504, and lies at Z 0.681 model pixels. The export applies a proper rotation, preserving text orientation and winding.

All boot stages, call poses and five case styles reference the new handset. Three fitted case shells preserve the existing case textures, bunny ears and armor corners while allowing clearance for the vertical camera stack, side buttons and bottom connector. Only the screen material uses full-bright ambient lighting. OBJ diffuse colors are white so the atlas is not darkened a second time.

Validation: Gradle `build` and the project's regression checks passed. `tools/verify_phone_2_assets.py` verifies 44 resolved model definitions, 41 predicates, four OBJ resources, material paths, face winding, screen UVs and call transforms. The running Forge client loaded the handset as `SimpleBakedModel`, with 2,422 quads (one per source triangle) and `missing=false`. The first-person model was visually inspected in-game; see `previews/09_minecraft.png`. Not every case and camera perspective has been manually exercised in-game.

## Editing and rebuilding

Only `MikasRevs Phone 2 | GAME ASSET` belongs in game exports. The separate preview collection contains the camera and soft lights and is excluded from all supplied exports. The `.blend` opens with the front three-quarter preview configured.

Rebuild from the repository root with:

```text
blender --background --factory-startup --python tools/build_mikasrevs_phone_2.py
blender --background --factory-startup --python tools/validate_mikasrevs_phone_2.py
blender --background --factory-startup --python tools/export_phone_2_to_forge.py
powershell -ExecutionPolicy Bypass -File tools/generate_phone_case_models.ps1
python tools/verify_phone_2_assets.py
```

Rebuilding replaces the generated asset files. The build script contains all geometry, UV, material, atlas, lighting and export settings.
