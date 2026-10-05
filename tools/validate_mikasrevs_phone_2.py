"""Independent mesh / GLB round-trip inspection, run in Blender."""
import bpy, json, itertools
from pathlib import Path
from mathutils.bvhtree import BVHTree
OUT=Path(__file__).resolve().parents[1]/'art'/'mikasrevs_phone_2'
bpy.ops.wm.open_mainfile(filepath=str(OUT/'mikasrevs_phone_2.blend'))
obs=[o for o in bpy.data.collections['MikasRevs Phone 2 | GAME ASSET'].objects if o.type=='MESH']
trees={o.name:BVHTree.FromPolygons([o.matrix_world@v.co for v in o.data.vertices],[list(p.vertices) for p in o.data.polygons],all_triangles=True,epsilon=0) for o in obs}
cross=[]
for a,b in itertools.combinations(obs,2):
    pairs=trees[a.name].overlap(trees[b.name])
    if pairs:
        cross.append({'a':a.name,'b':b.name,'intersecting_triangle_pairs':len(pairs)})
        print('INTERSECTION_LOCATIONS',b.name,[list(b.data.polygons[j].center) for i,j in pairs[:12]])
print('CROSS_OBJECT_INTERSECTIONS',json.dumps(cross))
names=sorted(o.name for o in obs)
tris=sum(len(o.data.polygons) for o in obs)
def uv_signature(ob):
    uv=ob.data.uv_layers.active
    return sorted(tuple(round(c,5) for c in (tuple(ob.matrix_world@ob.data.vertices[loop.vertex_index].co)+tuple(uv.data[loop.index].uv))) for loop in ob.data.loops)
source_uv={o.name:uv_signature(o) for o in obs}
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.gltf(filepath=str(OUT/'mikasrevs_phone_2.glb'))
imported=[o for o in bpy.context.scene.objects if o.type=='MESH']
result={'cross_object_surface_intersections':cross,'source_triangle_count':tris,
        'roundtrip_triangle_count':sum(len(o.data.polygons) for o in imported),
        'object_names_preserved':names==sorted(o.name for o in imported),
        'screen_separate':any(o.name=='phone_screen' for o in imported),
        'uvs_preserved':all(len(o.data.uv_layers)==1 for o in imported),
        'position_and_uv_values_preserved':all(source_uv[o.name]==uv_signature(o) for o in imported),
        'materials_preserved':all(len(o.data.materials)>0 for o in imported),
        'preview_excluded':not any(o.type in {'CAMERA','LIGHT'} for o in bpy.context.scene.objects)}
print('ROUNDTRIP_DETAILS',json.dumps(result), 'SOURCE_NAMES',names,'IMPORTED_NAMES',[o.name for o in imported])
assert result['roundtrip_triangle_count']==tris
assert not cross
assert all(result[k] for k in ['object_names_preserved','screen_separate','uvs_preserved','position_and_uv_values_preserved','materials_preserved','preview_excluded'])
(OUT/'export_validation.json').write_text(json.dumps(result,indent=2))
print('EXPORT_VALIDATION',json.dumps(result))
