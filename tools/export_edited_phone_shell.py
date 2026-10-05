"""Export an artist-edited saved shell without rebuilding its Blender geometry.

blender --background phone_models/model_01/model_01.blend --python tools/export_edited_phone_shell.py
Keep the PHONE | GAME ASSET collection, phone_screen, AtlasUV and GUI_UV names.
Bake any material edits into the packed 512 atlas in Blender before running.
"""
import bpy, bmesh, importlib.util, json, shutil
from pathlib import Path

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('shell_export',HERE/'build_phone_shells.py')
export=importlib.util.module_from_spec(spec); spec.loader.exec_module(export)
scene=bpy.context.scene
id=scene.get('model_id','')
assert id and (export.ART/id/(id+'.blend')).exists(), 'Open a phone shell .blend first'
objects=[ob for ob in bpy.data.collections['PHONE | GAME ASSET'].objects if ob.type=='MESH']
assert any(ob.name=='phone_screen' for ob in objects), 'The separate display mesh is required'
report={'id':id,'blender':bpy.app.version_string,'atlas':[512,512],
        'axes':{'up':'+Z','front':'-Y'},'screen_bounds':{'x':[-.446,.446],'y':-.0475,'z':[-.932,.910]},'objects':{}}
for ob in objects:
    assert all(abs(v)<1e-6 for v in ob.location) and all(abs(v)<1e-6 for v in ob.rotation_euler), 'Apply transforms and keep a centered pivot: '+ob.name
    assert all(abs(v-1)<1e-6 for v in ob.scale), 'Apply scale: '+ob.name
    assert ob.data.uv_layers.active, 'Missing UVs: '+ob.name
    export.activate(ob)
    mod=ob.modifiers.new('Export triangles','TRIANGULATE'); bpy.ops.object.modifier_apply(modifier=mod.name)
    bm=bmesh.new(); bm.from_mesh(ob.data)
    assert all(e.is_manifold for e in bm.edges) and all(f.calc_area()>1e-12 for f in bm.faces) and bm.calc_volume(signed=True)>0, 'Invalid topology/normals: '+ob.name
    report['objects'][ob.name]={'triangles':len(ob.data.polygons),'non_manifold_edges':0,'degenerate_faces':0,'signed_volume':bm.calc_volume(signed=True),
        'origin':list(ob.location),'rotation':list(ob.rotation_euler),'scale':list(ob.scale),'uv_layers':[u.name for u in ob.data.uv_layers]}
    bm.free()
screen=next(o for o in objects if o.name=='phone_screen')
front=[p for p in screen.data.polygons if p.normal.y<-.99]
assert front and all(abs(screen.data.vertices[i].co.y+.0475)<1e-6 for p in front for i in p.vertices), 'Display depth/orientation changed'
for p in front:
    for li in p.loop_indices:
        co=screen.data.vertices[screen.data.loops[li].vertex_index].co
        uv=screen.data.uv_layers.active.data[li].uv
        assert abs(uv.x-(co.x+.446)/.892)<1e-6 and abs(uv.y-(co.z+.932)/1.842)<1e-6, 'Display UV contract changed'
report['triangles']=sum(o['triangles'] for o in report['objects'].values())
assert 500<=report['triangles']<=2500, 'Keep the base shell within the triangle budget'
out=export.ART/id; folder=export.GAME/'phone'/id; tex=export.ASSETS/'textures/phone'/id
export.forge_export(objects,folder)
case=[o for o in bpy.data.collections['Optional cases | excluded from base export'].objects if o.type=='MESH']
plain=[o for o in case if not o.name.startswith(('Case_Ear','Case_Bumper'))]
export.forge_export(objects,folder,plain,'_plain')
export.forge_export(objects,folder,[o for o in case if not o.name.startswith('Case_Ear')],'_armor')
export.forge_export(objects,folder,[o for o in case if not o.name.startswith('Case_Bumper')],'_bunny')
export.game_jsons(id,folder)
atlas=bpy.data.images.get(id+'_atlas'); assert atlas and tuple(atlas.size)==(512,512), 'Expected a packed 512 atlas'
atlas.filepath_raw=str(out/'textures/atlas.png'); atlas.file_format='PNG'; atlas.save(); atlas.pack()
shutil.copyfile(out/'textures/atlas.png',tex/'atlas.png')
shutil.copyfile(folder/'phone.obj',out/'exports'/(id+'.obj'))
bpy.ops.object.select_all(action='DESELECT')
for ob in objects: ob.select_set(True)
bpy.context.view_layer.objects.active=objects[0]
bpy.ops.export_scene.gltf(filepath=str(out/'exports'/(id+'.glb')),export_format='GLB',use_selection=True,export_yup=True,export_cameras=False,export_lights=False)
cam=scene.camera
export.render(scene,cam,out/'previews/front.png',(2.7,-6,1.35))
export.render(scene,cam,out/'previews/rear.png',(-2.2,6,1.25))
export.render(scene,cam,out/'textures/preview.png',(-1.75,6,.65),(256,288),2.32,True)
export.frame(scene,cam,out)
for filename in ['preview.png','frame.png']: shutil.copyfile(out/'textures'/filename,tex/filename)
(out/'validation.json').write_text(json.dumps(report,indent=2))
scene['triangles']=report['triangles']
bpy.ops.wm.save_as_mainfile(filepath=str(out/(id+'.blend')))
print('EDITED_PHONE_EXPORTED '+id,flush=True)
