"""Export the Blender handset into Forge 1.20.1's native OBJ item pipeline.

Run with Blender --background --factory-startup --python this_file.py, then run
generate_phone_case_models.ps1. No extra runtime model-loading dependency needed.
"""
import bpy
import json
import shutil
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'art/mikasrevs_phone_2'
ASSETS = ROOT / 'src/main/resources/assets/newphone'
MODELS = ASSETS / 'models/item'
bpy.ops.wm.open_mainfile(filepath=str(ART / 'mikasrevs_phone_2.blend'))
objects = sorted((o for o in bpy.data.collections['MikasRevs Phone 2 | GAME ASSET'].objects if o.type == 'MESH'), key=lambda o: o.name)

def write_json(path, data):
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')

def game_point(co):
    # Proper rotation (not reflection), matching the old item model's position.
    # MC pixels: center (5,13.5,.89), height 8.8; screen faces north/-Z.
    return ((5 - 4.4*co.x)/16, (13.5 + 4.4*co.z)/16, (.89 + 4.4*co.y)/16)

def game_normal(n):
    return (-n.x, n.z, n.y)

def case_boxes(kind):
    boxes = []
    def box(name, low, high, mat='case'):
        boxes.append((name, low, high, mat))
    # Camera, controls and bottom connector remain accessible through the case.
    box('Case_ControlSideLower', (2.64,8.94,.62), (2.89,13.38,1.20))
    box('Case_ControlSideUpper', (2.64,16.10,.62), (2.89,18.06,1.20))
    box('Case_ControlSideBack', (2.64,13.38,1.11), (2.83,16.10,1.20))
    box('Case_CleanSide', (7.11,8.94,.62), (7.36,18.06,1.20))
    box('Case_Top', (2.64,17.89,.62), (7.36,18.06,1.20))
    box('Case_BottomFrontLip', (2.64,8.94,.62), (7.36,9.11,.68))
    box('Case_BottomBackLip', (2.64,8.94,1.10), (7.36,9.11,1.20))
    box('Case_RearLower', (2.83,9.12,1.096), (7.17,14.06,1.155))
    box('Case_RearCameraLeft', (2.83,14.06,1.096), (3.10,17.89,1.155))
    box('Case_RearCameraRight', (4.63,14.06,1.096), (7.17,17.89,1.155))
    box('Case_RearCameraTop', (3.10,17.40,1.096), (4.63,17.89,1.155))
    box('Case_Logo', (4.34,12.02,1.156), (5.66,13.28,1.19), 'case_logo')
    if kind == 'armor':
        for name,low,high in [
            ('BL',(2.56,8.82,.56),(3.34,9.58,1.26)),
            ('BR',(6.66,8.82,.56),(7.44,9.58,1.26)),
            ('TL',(2.56,17.42,.56),(3.10,18.18,1.26)),
            ('TR',(6.66,17.42,.56),(7.44,18.18,1.26))]:
            box('Case_Guard'+name,low,high,'case_accent')
    if kind == 'bunny':
        box('Case_EarLeft',(3.18,18.061,.70),(4.04,19.62,1.17))
        box('Case_EarRight',(5.96,18.061,.70),(6.82,19.62,1.17))
        box('Case_EarLeftInsert',(3.38,18.18,.64),(3.84,19.30,.699),'case_accent')
        box('Case_EarRightInsert',(6.16,18.18,.64),(6.62,19.30,.699),'case_accent')
    return boxes

def export_obj(kind):
    lines = ['# MikasRevs Phone 2 - Blender source, Forge item coordinates',
             'mtllib newphone:models/item/mikasrevs_phone_2.mtl']
    vi = ti = ni = 0
    tris = 0
    for ob in objects:
        lines.append('g ' + ob.name)
        for vert in ob.data.vertices:
            lines.append('v %.8f %.8f %.8f' % game_point(ob.matrix_world @ vert.co))
        uv = ob.data.uv_layers.active
        for loop in ob.data.loops:
            lines.append('vt %.8f %.8f' % tuple(uv.data[loop.index].uv))
        for poly in ob.data.polygons:
            lines.append('vn %.8f %.8f %.8f' % game_normal(poly.normal))
        active = None
        for poly in ob.data.polygons:
            mat = 'screen' if ob.name == 'phone_screen' and poly.normal.y < -.99 else ('black' if ob.name == 'phone_screen' else 'atlas')
            if mat != active:
                lines.append('usemtl ' + mat)
                active = mat
            lines.append('f ' + ' '.join('%d/%d/%d' % (vi + ob.data.loops[li].vertex_index + 1, ti+li+1, ni+poly.index+1) for li in poly.loop_indices))
            tris += len(poly.vertices)-2
        vi += len(ob.data.vertices)
        ti += len(ob.data.loops)
        ni += len(ob.data.polygons)
    for name,low,high,mat in ([] if kind == 'base' else case_boxes(kind)):
        x,y,z = low
        X,Y,Z = high
        verts=[(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)]
        faces=[(0,3,2,1),(4,5,6,7),(1,2,6,5),(0,4,7,3),(3,7,6,2),(0,1,5,4)]
        lines.extend(['g '+name,'usemtl '+mat])
        for v in verts: lines.append('v %.8f %.8f %.8f' % tuple(c/16 for c in v))
        for u,v in [(0,0),(1,0),(1,1),(0,1)]: lines.append('vt %d %d' % (u,v))
        for face in faces:
            n=(Vector(verts[face[1]])-Vector(verts[face[0]])).cross(Vector(verts[face[2]])-Vector(verts[face[0]])).normalized()
            lines.append('vn %.1f %.1f %.1f' % tuple(n))
        for j,face in enumerate(faces):
            lines.append('f '+' '.join('%d/%d/%d'%(vi+v+1,ti+k+1,ni+j+1) for k,v in enumerate(face)))
        vi+=8
        ti+=4
        ni+=6
        tris+=12
    suffix='' if kind=='base' else '_'+kind
    path=MODELS/('mikasrevs_phone_2'+suffix+'.obj')
    path.write_text('\n'.join(lines)+'\n',encoding='utf-8')
    return {'file':path.name,'triangles':tris}

mtl=[]
for mat in ['atlas','screen','black','case','case_accent','case_logo']:
    # White diffuse avoids multiplying the already-dark atlas by the PBR color.
    # Screen alone receives full light, retaining the previous lit-screen behavior.
    ambient='1 1 1' if mat=='screen' else '0 0 0'
    mtl.extend(['newmtl '+mat,'Ka '+ambient,'Kd 1 1 1','d 1','forge_TintIndex -1','map_Kd #'+mat,''])
(MODELS/'mikasrevs_phone_2.mtl').write_text('\n'.join(mtl),encoding='utf-8')
reports=[export_obj(kind) for kind in ['base','plain','armor','bunny']]
shutil.copyfile(ART/'mikasrevs_phone_2_atlas.png',ASSETS/'textures/item/mikasrevs_phone_2_atlas.png')

base_path=MODELS/'z_phone_base.json'
old=json.loads(base_path.read_text(encoding='utf-8-sig'))
call_path=MODELS/'z_phone_call.json'
base={
    'credit':'MikasRevs Phone 2 | original Blender low-poly mesh',
    'loader':'forge:obj',
    'model':'newphone:models/item/mikasrevs_phone_2.obj',
    'automatic_culling':False,
    'shade_quads':True,
    'flip_v':True,
    'emissive_ambient':True,
    'ambientocclusion':False,
    'gui_light':'front',
    'render_type':'minecraft:cutout',
    'textures':{
        'atlas':'newphone:item/mikasrevs_phone_2_atlas',
        'screen':'newphone:item/z_phone_screen_0',
        'black':'newphone:item/z_phone_black',
        'particle':'newphone:item/z_phone_body'
    },
    'display':old['display']
}
write_json(base_path,base)
for frame in range(1,6):
    write_json(MODELS/('z_phone_boot_%d.json'%frame),{
        'parent':'newphone:item/z_phone_base',
        'textures':{'screen':'newphone:item/z_phone_screen_%d'%frame}
    })
write_json(call_path,{'parent':'newphone:item/z_phone_base','textures':{'screen':'newphone:item/z_phone_screen_5'}})
write_json(MODELS/'z_phone_on.json',{'parent':'newphone:item/z_phone_base','textures':{'screen':'newphone:item/z_phone_screen_5'}})
write_json(ART/'forge_export.json',{'models':reports,'scale':4.4/16,'center_pixels':[5,13.5,.89],
    'screen_pixels':{'x':[3.0376,6.9624],'y':[9.3992,17.504],'z':.681},
    'screen_normal':'-Z','textures':'512px atlas + existing per-state display/case sprites'})
print('FORGE_EXPORT_COMPLETE '+json.dumps(reports))
