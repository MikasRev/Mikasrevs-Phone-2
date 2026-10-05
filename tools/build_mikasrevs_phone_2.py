"""Build the finished MikasRevs Phone 2 asset inside Blender 4.5+.
Run: blender --background --factory-startup --python tools/build_mikasrevs_phone_2.py
"""
import bpy
import bmesh
import json
import math
import os
from mathutils import Vector
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / 'art' / 'mikasrevs_phone_2'
OUT.mkdir(parents=True, exist_ok=True)
PRE = OUT / 'previews'
PRE.mkdir(exist_ok=True)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
for col in list(bpy.data.collections):
    if col.name != 'Collection':
        bpy.data.collections.remove(col)
asset = bpy.data.collections.get('Collection')
asset.name = 'MikasRevs Phone 2 | GAME ASSET'
stage = bpy.data.collections.new('PREVIEW ONLY | excluded from exports')
bpy.context.scene.collection.children.link(stage)

def material(name, color, metallic=0, rough=.4):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*color, 1)
    m.use_nodes = True
    p = m.node_tree.nodes.get('Principled BSDF')
    p.inputs['Base Color'].default_value = (*color, 1)
    p.inputs['Metallic'].default_value = metallic
    p.inputs['Roughness'].default_value = rough
    return m

rail = material('Graphite | satin metal', (.048,.055,.064), .76, .29)
rear = material('Graphite | ceramic back', (.023,.027,.033), .35, .38)
edge = material('Machined graphite | edge bevel', (.105,.118,.135), .8, .27)
black = material('Obsidian | bezel and cavities', (.004,.006,.009), .12, .32)
glass = material('Display | blank replaceable GUI', (.006,.009,.013), .08, .19)
lensmat = material('Optical glass | midnight blue', (.003,.007,.014), .18, .20)
optic = material('Optical iris | muted blue', (.012,.027,.043), .25, .24)
lensmat.node_tree.nodes.get('Principled BSDF').inputs['Specular IOR Level'].default_value=.14
optic.node_tree.nodes.get('Principled BSDF').inputs['Specular IOR Level'].default_value=.18
flashmat = material('Flash | warm ceramic', (.65,.61,.43), .05, .36)
logomat = material('MIKASREVS | subdued pixel wordmark', (.023,.027,.033), .35, .38)

def mesh_object(name, verts, faces, mats, mids=None):
    me = bpy.data.meshes.new(name + '_Mesh')
    me.from_pydata(verts, [], faces)
    me.update()
    ob = bpy.data.objects.new(name, me)
    asset.objects.link(ob)
    for m in mats:
        me.materials.append(m)
    if mids:
        for f, mi in zip(me.polygons, mids):
            f.material_index = mi
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
    bm.to_mesh(me)
    bm.free()
    return ob

def outline(w,h,r,steps=3):
    pts=[]
    for cx,cz,a in [(w/2-r,h/2-r,0),(-w/2+r,h/2-r,90),(-w/2+r,-h/2+r,180),(w/2-r,-h/2+r,270)]:
        for j in range(steps+1):
            t=math.radians(a+j*90/steps)
            pts.append((cx+r*math.cos(t),cz+r*math.sin(t)))
    return pts

def shell(name, profiles, mats, bandmats=None, frontmat=0, backmat=0, center=(0,0,0), steps=3):
    verts=[]
    for y,w,h,r in profiles:
        verts += [(x+center[0],y+center[1],z+center[2]) for x,z in outline(w,h,r,steps)]
    n=len(verts)//len(profiles)
    faces=[tuple(range(n-1,-1,-1))]
    mids=[frontmat]
    for k in range(len(profiles)-1):
        for i in range(n):
            faces.append((k*n+i,k*n+(i+1)%n,(k+1)*n+(i+1)%n,(k+1)*n+i))
            mids.append(bandmats[k] if bandmats else 0)
    faces.append(tuple((len(profiles)-1)*n+i for i in range(n)))
    mids.append(backmat)
    return mesh_object(name,verts,faces,mats,mids)

def activate(ob):
    bpy.ops.object.select_all(action='DESELECT')
    ob.select_set(True)
    bpy.context.view_layer.objects.active=ob

def cube(name, loc, dims, mat, bevel=0):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
    ob=bpy.context.object
    ob.name=name
    ob.dimensions=dims
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    ob.data.materials.append(mat)
    if bevel:
        mod=ob.modifiers.new('Small game bevel','BEVEL')
        mod.width=bevel
        mod.segments=1
        bpy.ops.object.modifier_apply(modifier=mod.name)
    return ob

def lathe(name, cx,cz, profile,mats, bandmats=None,n=16,closed=False):
    # Profile is (radius, Y depth); axis points out of the rear.
    verts=[(cx+r*math.cos(i*2*math.pi/n),y,cz+r*math.sin(i*2*math.pi/n)) for r,y in profile for i in range(n)]
    faces=[]
    ids=[]
    count=len(profile) if closed else len(profile)-1
    for k in range(count):
        k2=(k+1)%len(profile)
        for i in range(n):
            faces.append((k*n+i,k*n+(i+1)%n,k2*n+(i+1)%n,k2*n+i))
            ids.append(bandmats[k] if bandmats else 0)
    if not closed:
        faces.extend([tuple(range(n-1,-1,-1)),tuple((len(profile)-1)*n+i for i in range(n))])
        ids.extend([0,len(mats)-1])
    return mesh_object(name,verts,faces,mats,ids)

def subtract(ob, cutter):
    activate(ob)
    mod=ob.modifiers.new('Applied precision recess','BOOLEAN')
    mod.operation='DIFFERENCE'
    mod.solver='EXACT'
    mod.object=cutter
    bpy.ops.object.modifier_apply(modifier=mod.name)
    bpy.data.objects.remove(cutter,do_unlink=True)

def join(name, obs):
    bpy.ops.object.select_all(action='DESELECT')
    for ob in obs: ob.select_set(True)
    bpy.context.view_layer.objects.active=obs[0]
    bpy.ops.object.join()
    obs[0].name=name
    return obs[0]

body=shell('Phone_Body', [(-.045,.942,1.972,.060),(-.038,.970,2,.066),(.030,.970,2,.066),(.040,.958,1.988,.064),(.045,.936,1.966,.060)], [rail,rear,edge,black], [2,0,2,1],3,1)

# The broad, perfectly planar GUI face is a true rectangle with full 0..1 UVs.
# It sits in a shallow recess; rounded outer glass forms the bezel around it.
screen=cube('phone_screen',(0,-.0455,-.011),(.892,.004,1.842),glass)
subtract(body,cube('Screen_recess',(0,-.05,-.011),(.8924,.018,1.8424),black))
screen['purpose']='Independent flat dynamic GUI surface. Front normal Blender -Y / glTF +Z.'
screen['gui_bounds_blender']='x=[-0.446,0.446], z=[-0.932,0.910], y=-0.0475'
screen['gui_safe_area_uv']='Keep top 0.985 clear of frame; no baked UI.'

# Front camera and speaker live in the narrow top glass bezel.
front=lathe('Front_Camera',0,.940,[(.014,-.046),(.014,-.0482),(.010,-.049),(.005,-.049)], [black,lensmat,optic],[0,1,1],n=12)
subtract(body,lathe('Front_camera_socket',0,.940,[(.0142,-.052),(.0142,-.044)], [black],n=12))
speaker=shell('Earpiece',[(-.0462,.105,.006,.002),(-.0452,.105,.006,.002)],[black],center=(0,0,.967),steps=1)

# Three floating camera rings, individually seated in precision sockets.
rings=[]
for idx,z in enumerate([.748,.499,.250],1):
    cx=.314
    subtract(body,lathe('Camera_socket',cx,z,[(.0873,.0415),(.0873,.082)],[black],n=16))
    rings.append(lathe('Ring_%02d'%idx,cx,z,[(.087,.0418),(.087,.068),(.081,.075),(.070,.075),(.070,.0418)],[rail,edge],[0,1,1,0,0],n=16,closed=True))
    lens=lathe('Camera_Lens_%02d'%idx,cx,z,[(.0697,.042),(.0697,.071),(.049,.071),(.030,.0715),(.020,.072)],[lensmat,black,optic],[0,0,1,2],n=16)
    lens['camera_number']=idx
join('Camera_Rings',rings)

flash=lathe('Flash',.126,.744,[(.023,.0454),(.023,.048),(.018,.051),(.018,.0515)],[rail,edge,flashmat],[0,1,2],n=12)
# Minimal identifying feature: a short, offset double-chamfered rear inlay.
line=shell('Rear_Accent',[(.0453,.355,.005,.002),(.0458,.355,.005,.002)],[rail],center=(-.095,0,-.480),steps=1)

buttons=[]
for name,z,h in [('Power_Button',.065,.155),('Volume_Up',.511,.124),('Volume_Down',.340,.124)]:
    b=cube(name,(.488,0,z),(.022,.048,h),rail,.005)
    subtract(body,cube('Button_socket',(.4878,0,z),(.0228,.0485,h+.0005),black,.005))
    buttons.append(b)
join('Volume_Buttons',buttons[1:])

# Bottom ports are actual shallow recesses, with dark inner walls.
port=cube('USB-C_socket',(0,0,-.997),(.137,.037,.026),black,.012)
subtract(body,port)
bottom=[]
bottom.append(cube('USB-C_interior',(0,0,-.9857),(.103,.010,.001),black,.0004))
bottom.append(cube('USB-C_tongue',(0,0,-.992),(.079,.008,.010),rail,.001))
for x in [-.335,-.281,-.227,.227,.281,.335]:
    bpy.ops.mesh.primitive_cylinder_add(vertices=8, radius=.012, depth=.024, location=(x,0,-.998))
    hole=bpy.context.object
    hole.data.materials.append(black)
    subtract(body,hole)
    bpy.ops.mesh.primitive_cylinder_add(vertices=8,radius=.0117,depth=.001,location=(x,0,-.9869))
    plug=bpy.context.object
    plug.data.materials.append(black)
    bottom.append(plug)
join('Bottom_Details',bottom)

# Pixel lettering is texture work, not a dense converted text mesh.
font={
 'M':['10001','11011','10101','10001','10001','10001','10001'],
 'I':['111','010','010','010','010','010','111'],
 'K':['10001','10010','10100','11000','10100','10010','10001'],
 'A':['01110','10001','10001','11111','10001','10001','10001'],
 'S':['01111','10000','10000','01110','00001','00001','11110'],
 'R':['11110','10001','10001','11110','10100','10010','10001'],
 'E':['11111','10000','10000','11110','10000','10000','11111'],
 'V':['10001','10001','10001','10001','10001','01010','00100']}
logoimg=bpy.data.images.new('MIKASREVS_pixel_source',width=256,height=64,alpha=False)
bg=(.164,.179,.199,1)
fg=(.325,.347,.380,1)
px=list(bg)*(256*64)
cursor=21
for char in 'MIKASREVS':
    bitmap=font[char]
    for row,values in enumerate(bitmap):
        for col,bit in enumerate(values):
            if bit=='1':
                for dy in range(3):
                    for dx in range(4):
                        x=cursor+col*4+dx
                        y=41-row*3+dy
                        pos=4*(y*256+x)
                        px[pos:pos+4]=fg
    cursor+=(len(bitmap[0])+1)*4
logoimg.pixels[:]=px
logoimg.pack()
logo=cube('Logo',(0,.0449,-.678),(.366,.0018,.081),logomat)
subtract(body,cube('Logo_recess',(0,.049,-.678),(.3664,.0105,.0814),rear))
logo.data.uv_layers.new(name='SourceUV')
for p in logo.data.polygons:
    for li in p.loop_indices:
        co=logo.matrix_world @ logo.data.vertices[logo.data.loops[li].vertex_index].co
        logo.data.uv_layers.active.data[li].uv=(.5-co.x/.366,(co.z+.678)/.081+.5)
tex=logomat.node_tree.nodes.new('ShaderNodeTexImage')
tex.image=logoimg
tex.interpolation='Closest'
logomat.node_tree.links.new(tex.outputs['Color'],logomat.node_tree.nodes.get('Principled BSDF').inputs['Base Color'])
uvnode=logomat.node_tree.nodes.new('ShaderNodeUVMap')
uvnode.uv_map='SourceUV'
logomat.node_tree.links.new(uvnode.outputs['UV'],tex.inputs['Vector'])

objects=[o for o in asset.objects if o.type=='MESH']
# Apply all transforms, move each origin to the same handheld pivot, and clean normals.
for ob in objects:
    activate(ob)
    bpy.ops.object.transform_apply(location=False,rotation=True,scale=True)
    bpy.context.scene.cursor.location=(0,0,0)
    bpy.ops.object.origin_set(type='ORIGIN_CURSOR')
    bm=bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=.0000001)
    bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces))
    bm.to_mesh(ob.data)
    bm.free()
    # Crisp broad faces and deliberately faceted Minecraft-friendly bevels.
    for poly in ob.data.polygons: poly.use_smooth=False

# Screen front = a single simple rectangular island; thin edge/back UVs also
# fit in 0..1 and intentionally reuse the untextured display material.
for layer in list(screen.data.uv_layers):
    screen.data.uv_layers.remove(layer)
uv=screen.data.uv_layers.new(name='GUI_UV')
screen.data.uv_layers.active=uv
uv.active_render=True
for p in screen.data.polygons:
    for li in p.loop_indices:
        co=screen.data.vertices[screen.data.loops[li].vertex_index].co
        uv.data[li].uv=((co.x+.446)/.892,(co.z+.932)/1.842)

atlasobs=[o for o in objects if o != screen]
for ob in atlasobs:
    layer=ob.data.uv_layers.new(name='AtlasUV')
    ob.data.uv_layers.active=layer
    layer.active_render=True
bpy.ops.object.select_all(action='DESELECT')
for ob in atlasobs: ob.select_set(True)
bpy.context.view_layer.objects.active=body
bpy.ops.object.mode_set(mode='EDIT')
bpy.ops.mesh.select_all(action='SELECT')
bpy.ops.uv.smart_project(angle_limit=math.radians(68),island_margin=.012,area_weight=.5,correct_aspect=True,scale_to_bounds=True)
bpy.ops.object.mode_set(mode='OBJECT')

# Reserve a high-resolution strip for the tiny wordmark instead of allowing
# area-based packing to reduce each letter to a fraction of a texel.
for ob in atlasobs:
    for loop in ob.data.uv_layers.active.data:
        loop.uv.y *= .89
for poly in logo.data.polygons:
    if abs(poly.normal.y)>.99:
        for li in poly.loop_indices:
            co=logo.data.vertices[logo.data.loops[li].vertex_index].co
            u=.5-co.x/.366
            v=(co.z+.678)/.081+.5
            # Front and back islands remain separate, despite identical pixels.
            base=.015 if poly.normal.y>0 else .515
            logo.data.uv_layers.active.data[li].uv=(base+.47*u,.907+.086*v)

scene=bpy.context.scene
scene.render.engine='CYCLES'
scene.cycles.samples=1
atlas=bpy.data.images.new('mikasrevs_phone_2_atlas',width=512,height=512,alpha=False)
atlas.generated_color=(.023,.027,.033,1)
usedmats=set(m for ob in atlasobs for m in ob.data.materials)
for m in usedmats:
    node=m.node_tree.nodes.new('ShaderNodeTexImage')
    node.name='EXPORT_ATLAS'
    node.image=atlas
    m.node_tree.nodes.active=node
scene.render.bake.use_pass_direct=False
scene.render.bake.use_pass_indirect=False
scene.render.bake.use_pass_color=True
scene.render.bake.margin=3
scene.render.bake.use_clear=True
bpy.ops.object.bake(type='DIFFUSE')
# Rasterize the pixel wordmark directly into its reserved atlas regions. This
# avoids resampling a small secondary texture through the bake UV mapping.
atlaspx=list(atlas.pixels[:])
for base in [.015,.515]:
    x0,x1=round(base*512),round((base+.47)*512)
    y0,y1=round(.907*512),round(.993*512)
    for y in range(y0,y1):
        for x in range(x0,x1):
            at=4*(y*512+x)
            atlaspx[at:at+4]=bg
    cursor=x0+(x1-x0-208)//2
    for char in 'MIKASREVS':
        for row,values in enumerate(font[char]):
            for col,bit in enumerate(values):
                if bit=='1':
                    for dy in range(3):
                        for dx in range(4):
                            x=cursor+col*4+dx
                            y=y0+11+(6-row)*3+dy
                            at=4*(y*512+x)
                            atlaspx[at:at+4]=fg
        cursor+=(len(font[char][0])+1)*4
atlas.pixels[:]=atlaspx
atlas.filepath_raw=str(OUT/'mikasrevs_phone_2_atlas.png')
atlas.file_format='PNG'
atlas.save()
atlas.pack()
for m in usedmats:
    nt=m.node_tree
    bs=nt.nodes.get('Principled BSDF')
    im=nt.nodes.get('EXPORT_ATLAS')
    for node in list(nt.nodes):
        if node not in [bs,im] and node.type!='OUTPUT_MATERIAL': nt.nodes.remove(node)
    im.interpolation='Linear'
    nt.links.new(im.outputs['Color'],bs.inputs['Base Color'])
for ob in atlasobs:
    for layername in [layer.name for layer in ob.data.uv_layers if layer.name!='AtlasUV']:
        ob.data.uv_layers.remove(ob.data.uv_layers[layername])
    ob.data.uv_layers.active=ob.data.uv_layers['AtlasUV']
    ob.data.uv_layers.active.active_render=True

# Triangulate final game mesh explicitly for identical topology in every export.
for ob in objects:
    activate(ob)
    ob.data.name=ob.name+'_Mesh'
    mod=ob.modifiers.new('Final game triangles','TRIANGULATE')
    bpy.ops.object.modifier_apply(modifier=mod.name)

report={'asset':'MikasRevs Phone 2','blender_version':bpy.app.version_string,'body_dimensions':[.97,.09,2.0],
        'blender_axes':{'width':'X','up':'+Z','screen_normal':'-Y'},
        'gltf_axes':{'width':'X','up':'+Y','screen_normal':'+Z'},
        'screen_bounds':{'x':[-.446,.446],'y':-.0475,'z':[-.932,.910]},
        'atlas_size':[512,512], 'objects':{}}
for ob in objects:
    bm=bmesh.new()
    bm.from_mesh(ob.data)
    report['objects'][ob.name]={'triangles':len(ob.data.polygons),'vertices':len(ob.data.vertices),
       'non_manifold_edges':sum(not e.is_manifold for e in bm.edges),
       'degenerate_faces':sum(f.calc_area()<1e-12 for f in bm.faces),
       'signed_volume':bm.calc_volume(signed=True),
       'uv_layers':[u.name for u in ob.data.uv_layers],
       'origin':list(ob.location),'rotation':list(ob.rotation_euler),'scale':list(ob.scale)}
    bm.free()
report['total_triangles']=sum(x['triangles'] for x in report['objects'].values())
assert 500<=report['total_triangles']<=2500,report['total_triangles']
assert all(x['non_manifold_edges']==0 and x['degenerate_faces']==0 and x['signed_volume']>0 for x in report['objects'].values()),report
(OUT/'validation.json').write_text(json.dumps(report,indent=2))

bpy.ops.object.select_all(action='DESELECT')
for ob in objects: ob.select_set(True)
bpy.context.view_layer.objects.active=body
scene['asset_notes']='MikasRevs Phone 2. Export GAME ASSET collection only. Blender Z up / front -Y. All mesh origins at world center.'
scene['triangle_count']=report['total_triangles']
bpy.ops.export_scene.gltf(filepath=str(OUT/'mikasrevs_phone_2.glb'),export_format='GLB',use_selection=True,export_yup=True,export_extras=True,export_cameras=False,export_lights=False)
bpy.ops.export_scene.gltf(filepath=str(OUT/'mikasrevs_phone_2.gltf'),export_format='GLTF_SEPARATE',use_selection=True,export_yup=True,export_extras=True,export_cameras=False,export_lights=False)
bpy.ops.wm.obj_export(filepath=str(OUT/'mikasrevs_phone_2.obj'),export_selected_objects=True,forward_axis='NEGATIVE_Z',up_axis='Y',export_materials=True,export_uv=True,export_normals=True,path_mode='COPY')

# The preview collection never enters the game exports.
def to_stage(ob):
    for col in list(ob.users_collection): col.objects.unlink(ob)
    stage.objects.link(ob)

def light(name,loc,power,size,color):
    data=bpy.data.lights.new(name,'AREA')
    data.energy=power
    data.shape='DISK'
    data.size=size
    data.color=color
    ob=bpy.data.objects.new(name,data)
    stage.objects.link(ob)
    ob.location=loc
    ob.rotation_euler=(Vector((0,0,0))-ob.location).to_track_quat('-Z','Y').to_euler()

light('Soft key',(1.5,-3.8,4.2),420,4,(.89,.94,1))
light('Soft fill',(-3,-1.5,.8),190,3,(.73,.84,1))
light('Rear key',(-3,2,4),350,2,(1,.92,.8))
light('Edge fill',(2,2,-.1),140,2,(.75,.86,1))
light('Bottom detail fill',(.3,-1.8,-3),100,2,(.88,.92,1))
world=bpy.data.worlds.new('Neutral charcoal studio')
world.use_nodes=True
world.node_tree.nodes['Background'].inputs[0].default_value=(.048,.052,.062,1)
world.node_tree.nodes['Background'].inputs[1].default_value=.45
scene.world=world
bpy.ops.object.camera_add()
cam=bpy.context.object
cam.name='Preview_Camera'
to_stage(cam)
cam.data.type='ORTHO'
cam.data.ortho_scale=2.58
scene.camera=cam
scene.render.engine='CYCLES'
scene.cycles.samples=40
scene.cycles.use_denoising=True
scene.render.resolution_x=840
scene.render.resolution_y=1000
scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'
scene.render.film_transparent=False
scene.view_settings.view_transform='AgX'

def view(name,location,scale=2.58,res=(840,1000)):
    cam.location=location
    cam.rotation_euler=(Vector((0,0,0))-cam.location).to_track_quat('-Z','Y').to_euler()
    cam.data.ortho_scale=scale
    scene.render.resolution_x,scene.render.resolution_y=res
    scene.render.filepath=str(PRE/(name+'.png'))
    bpy.ops.render.render(write_still=True)

view('01_front_three_quarter',(2.7,-6,2.0))
view('02_rear_three_quarter',(-2.6,6,1.7))
scene.cycles.samples=16
for name,loc,scale,res in [
    ('03_front',(0,-6,0),2.32,(650,1000)),
    ('04_back',(0,6,0),2.32,(650,1000)),
    ('05_right',(6,0,0),2.25,(400,1000)),
    ('06_left',(-6,0,0),2.25,(400,1000)),
    ('07_top',(0,-.00001,6),1.15,(1000,300)),
    ('08_bottom',(0,-.00001,-6),1.15,(1000,300))]:
    view(name,loc,scale,res)

cam.location=(2.7,-6,2)
cam.rotation_euler=(-cam.location).to_track_quat('-Z','Y').to_euler()
cam.data.ortho_scale=2.58
scene.render.resolution_x=840
scene.render.resolution_y=1000
scene.cycles.samples=40
scene.render.filepath=str(PRE/'01_front_three_quarter.png')
bpy.ops.object.select_all(action='DESELECT')
body.select_set(True)
bpy.context.view_layer.objects.active=body
for area in bpy.context.screen.areas:
    if area.type=='VIEW_3D':
        area.spaces.active.region_3d.view_distance=3.4
        area.spaces.active.region_3d.view_location=(0,0,0)
        area.spaces.active.region_3d.view_rotation=cam.rotation_euler.to_quaternion()
        area.spaces.active.clip_start=.005
        area.spaces.active.shading.type='MATERIAL'
for m in list(bpy.data.materials):
    if not m.users: bpy.data.materials.remove(m)
for me in list(bpy.data.meshes):
    if not me.users: bpy.data.meshes.remove(me)
for im in list(bpy.data.images):
    if not im.users and im.name!='Render Result': bpy.data.images.remove(im)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'mikasrevs_phone_2.blend'))
print('MIKASREVS_BUILD_COMPLETE '+json.dumps({'triangles':report['total_triangles'],'objects':len(objects),'output':str(OUT)}))
