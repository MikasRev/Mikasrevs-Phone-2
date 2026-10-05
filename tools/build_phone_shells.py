"""Run in Blender 4.5: blender --background --factory-startup --python tools/build_phone_shells.py.

Actual Blender meshes, multi-object UV unwrap + baked 512 atlas, editable .blend,
GLB archive, Forge OBJ, and rendered preview/frame assets. Classic is read-only.
"""
import bpy, bmesh, json, math, shutil, sys
import numpy as np
from pathlib import Path
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'phone_models'
ASSETS = ROOT / 'src/main/resources/assets/newphone'
GAME = ASSETS / 'models'
CONFIGS = [
    ('model_01', 'Navy / Gold', (.025,.045,.082), (.49,.30,.085), .075),
    ('model_02', 'Mint', (.30,.57,.43), (.23,.43,.32), .10),
    ('model_03', 'Pearl / Silver', (.79,.81,.77), (.48,.52,.54), .065),
    ('model_04', 'Cream / Orange', (.84,.76,.59), (.80,.20,.035), .12),
    ('model_05', 'Forest / Olive', (.028,.105,.065), (.12,.17,.075), .085),
]

def activate(ob):
    bpy.ops.object.select_all(action='DESELECT')
    ob.select_set(True)
    bpy.context.view_layer.objects.active=ob

def material(name, color, metal=0, rough=.4):
    m=bpy.data.materials.new(name); m.diffuse_color=(*color,1); m.use_nodes=True
    bs=m.node_tree.nodes.get('Principled BSDF')
    bs.inputs['Base Color'].default_value=(*color,1)
    bs.inputs['Metallic'].default_value=metal; bs.inputs['Roughness'].default_value=rough
    return m

def mesh(name, verts, faces, mats, ids=None):
    me=bpy.data.meshes.new(name+'_Mesh'); me.from_pydata(verts,[],faces); me.update()
    ob=bpy.data.objects.new(name,me); asset.objects.link(ob)
    for m in mats: me.materials.append(m)
    for p,i in zip(me.polygons, ids or []): p.material_index=i
    bm=bmesh.new(); bm.from_mesh(me)
    bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces)); bm.to_mesh(me); bm.free()
    return ob

def outline(w,h,r,steps=3):
    points=[]
    for cx,cz,a in [(w/2-r,h/2-r,0),(-w/2+r,h/2-r,90),(-w/2+r,-h/2+r,180),(w/2-r,-h/2+r,270)]:
        for j in range(steps+1):
            ang=math.radians(a+j*90/steps); points.append((cx+r*math.cos(ang),cz+r*math.sin(ang)))
    return points

def shell(name,profiles,mats,center=(0,0),front=0,back=0,bands=None,steps=3):
    cx,cz=center; verts=[]
    for y,w,h,r in profiles: verts.extend((x+cx,y,z+cz) for x,z in outline(w,h,r,steps))
    n=len(verts)//len(profiles); faces=[tuple(range(n-1,-1,-1))]; ids=[front]
    for j in range(len(profiles)-1):
        for i in range(n):
            k=(i+1)%n; faces.append((j*n+i,j*n+k,(j+1)*n+k,(j+1)*n+i)); ids.append((bands or [0]*(len(profiles)-1))[j])
    faces.append(tuple((len(profiles)-1)*n+i for i in range(n))); ids.append(back)
    return mesh(name,verts,faces,mats,ids)

def cube(name,loc,dims,mat,bevel=0):
    bpy.ops.mesh.primitive_cube_add(size=1,location=loc)
    ob=bpy.context.object; ob.name=name
    for col in list(ob.users_collection): col.objects.unlink(ob)
    asset.objects.link(ob); ob.dimensions=dims; ob.data.materials.append(mat)
    bpy.ops.object.transform_apply(location=False,rotation=True,scale=True)
    if bevel:
        mod=ob.modifiers.new('Small edge bevel','BEVEL'); mod.width=bevel; mod.segments=1
        bpy.ops.object.modifier_apply(modifier=mod.name)
    return ob

def lathe(name,cx,cz,profile,mat,n=16):
    verts=[(cx+r*math.cos(2*math.pi*i/n),y,cz+r*math.sin(2*math.pi*i/n)) for r,y in profile for i in range(n)]
    faces=[tuple(range(n-1,-1,-1))]
    for j in range(len(profile)-1):
        for i in range(n): faces.append((j*n+i,j*n+(i+1)%n,(j+1)*n+(i+1)%n,(j+1)*n+i))
    faces.append(tuple((len(profile)-1)*n+i for i in range(n)))
    return mesh(name,verts,faces,[mat])

def subtract(ob,cutter):
    activate(ob); mod=ob.modifiers.new('Machined recess','BOOLEAN'); mod.operation='DIFFERENCE'; mod.solver='EXACT'; mod.object=cutter
    bpy.ops.object.modifier_apply(modifier=mod.name); bpy.data.objects.remove(cutter,do_unlink=True)

def join(name,obs):
    bpy.ops.object.select_all(action='DESELECT')
    for ob in obs: ob.select_set(True)
    bpy.context.view_layer.objects.active=obs[0]; bpy.ops.object.join(); obs[0].name=name
    return obs[0]

def lens(i,cx,cz,r,y):
    # Annular metallic rim; the dark glass seals the aperture, no glass thickness effect.
    n=16
    verts=[(cx+rad*math.cos(2*math.pi*k/n),dep,cz+rad*math.sin(2*math.pi*k/n))
           for rad,dep in [(r*.91,y),(r,y+.006),(r,y+.024),(r*.82,y+.027),(r*.77,y+.024),(r*.77,y)] for k in range(n)]
    faces=[]
    for j in range(6):
        for k in range(n): faces.append((j*n+k,j*n+(k+1)%n,((j+1)%6)*n+(k+1)%n,((j+1)%6)*n+k))
    rims.append(mesh('Camera_Ring_%02d'%i,verts,faces,[ring]))
    lathe('Camera_Lens_%02d'%i,cx,cz,[(r*.77,y),(r*.77,y+.022)],glass,16)
    # A readable lens core, deliberately broad enough for the 512 atlas and Minecraft distances.
    lathe('Lens_Core_%02d'%i,cx,cz,[(r*.39,y+.02205),(r*.39,y+.02235)],core,12)

def flash(cx,cz,y,r=.025):
    lathe('Flash_Rim',cx,cz,[(r*1.14,y),(r*1.14,y+.007)],rail,12)
    lathe('Flash',cx,cz,[(r,y+.007),(r,y+.008)],led,12)

def bake_and_validate(out,objects,screen):
    for ob in objects:
        activate(ob); bpy.ops.object.transform_apply(location=False,rotation=True,scale=True)
        bpy.context.scene.cursor.location=(0,0,0); bpy.ops.object.origin_set(type='ORIGIN_CURSOR')
        bm=bmesh.new(); bm.from_mesh(ob.data)
        bmesh.ops.remove_doubles(bm,verts=list(bm.verts),dist=1e-7); bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces))
        bm.to_mesh(ob.data); bm.free()
        for p in ob.data.polygons: p.use_smooth=False
        ob.data.uv_layers.new(name='GUI_UV' if ob==screen else 'AtlasUV')
    for loop in screen.data.loops:
        co=screen.data.vertices[loop.vertex_index].co
        screen.data.uv_layers.active.data[loop.index].uv=((co.x+.446)/.892,(co.z+.932)/1.842)
    atlasobs=[ob for ob in objects if ob!=screen]
    bpy.ops.object.select_all(action='DESELECT')
    for ob in atlasobs: ob.select_set(True)
    bpy.context.view_layer.objects.active=atlasobs[0]
    bpy.ops.object.mode_set(mode='EDIT'); bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.smart_project(angle_limit=math.radians(66),island_margin=.009,area_weight=.6,correct_aspect=True,scale_to_bounds=True)
    bpy.ops.object.mode_set(mode='OBJECT')
    atlas=bpy.data.images.new(out.name+'_atlas',width=512,height=512,alpha=False)
    atlas.generated_color=(.05,.05,.05,1)
    mats=set(m for ob in atlasobs for m in ob.data.materials)
    for m in mats:
        node=m.node_tree.nodes.new('ShaderNodeTexImage'); node.name='BAKED_ATLAS'; node.image=atlas; m.node_tree.nodes.active=node
    scene=bpy.context.scene; scene.render.engine='CYCLES'; scene.cycles.samples=1
    scene.render.bake.use_pass_direct=False; scene.render.bake.use_pass_indirect=False; scene.render.bake.use_pass_color=True
    scene.render.bake.margin=2; scene.render.bake.use_clear=True
    bpy.ops.object.bake(type='DIFFUSE')
    atlas.filepath_raw=str(out/'textures/atlas.png'); atlas.file_format='PNG'; atlas.save(); atlas.pack()
    for m in mats:
        nt=m.node_tree; bs=nt.nodes.get('Principled BSDF'); node=nt.nodes.get('BAKED_ATLAS')
        for old in list(nt.nodes):
            if old not in [bs,node] and old.type!='OUTPUT_MATERIAL': nt.nodes.remove(old)
        nt.links.new(node.outputs['Color'],bs.inputs['Base Color']); node.interpolation='Linear'
    report={'id':out.name,'blender':bpy.app.version_string,'atlas':[512,512],
            'axes':{'up':'+Z','front':'-Y'},'screen_bounds':{'x':[-.446,.446],'y':-.0475,'z':[-.932,.910]},'objects':{}}
    for ob in objects:
        activate(ob); mod=ob.modifiers.new('Export triangles','TRIANGULATE'); bpy.ops.object.modifier_apply(modifier=mod.name)
        bm=bmesh.new(); bm.from_mesh(ob.data)
        report['objects'][ob.name]={'triangles':len(ob.data.polygons),'non_manifold_edges':sum(not e.is_manifold for e in bm.edges),
            'degenerate_faces':sum(f.calc_area()<1e-12 for f in bm.faces),'signed_volume':bm.calc_volume(signed=True),
            'origin':list(ob.location),'rotation':list(ob.rotation_euler),'scale':list(ob.scale),'uv_layers':[u.name for u in ob.data.uv_layers]}
        bm.free()
    report['triangles']=sum(o['triangles'] for o in report['objects'].values())
    assert 500<=report['triangles']<=2500,report
    assert all(o['non_manifold_edges']==0 and o['degenerate_faces']==0 and o['signed_volume']>0 for o in report['objects'].values()),report
    (out/'validation.json').write_text(json.dumps(report,indent=2))
    return report

def forge_export(objects,folder,caseobjects=(),suffix=''):
    lines=['# Blender mesh export. MC center (5,13.5,.89), front -Z; GUI UV remains 0..1.','mtllib phone.mtl']
    vi=ti=ni=0
    for ob in list(objects)+list(caseobjects):
        lines.append('g '+ob.name)
        for v in ob.data.vertices:
            co=ob.matrix_world@v.co; lines.append('v %.8f %.8f %.8f'%((5-4.4*co.x)/16,(13.5+4.4*co.z)/16,(.89+4.4*co.y)/16))
        for uv in ob.data.uv_layers.active.data: lines.append('vt %.8f %.8f'%tuple(uv.uv))
        for p in ob.data.polygons:
            n=ob.matrix_world.to_3x3()@p.normal; n.normalize(); lines.append('vn %.8f %.8f %.8f'%(-n.x,n.z,n.y))
        active=None
        for p in ob.data.polygons:
            mat='screen' if ob.name=='phone_screen' and p.normal.y<-.99 else 'black' if ob.name=='phone_screen' else 'atlas'
            if ob in caseobjects: mat=ob.get('forge_material','case')
            if mat!=active: lines.append('usemtl '+mat); active=mat
            lines.append('f '+' '.join('%d/%d/%d'%(vi+ob.data.loops[li].vertex_index+1,ti+li+1,ni+p.index+1) for li in p.loop_indices))
        vi+=len(ob.data.vertices); ti+=len(ob.data.loops); ni+=len(ob.data.polygons)
    (folder/('phone'+suffix+'.obj')).write_text('\n'.join(lines)+'\n',encoding='utf-8')

def cases(objects,folder):
    # Accessory geometry is authored in Blender too. An open upper back leaves
    # all five camera layouts clear, while retaining existing case colors/ears.
    casecol=bpy.data.collections.new('Optional cases | excluded from base export'); bpy.context.scene.collection.children.link(casecol)
    caseset=[]
    def box(name,loc,dims,mat='case'):
        ob=cube(name,loc,dims,rail,.003); ob['forge_material']=mat
        for col in list(ob.users_collection): col.objects.unlink(ob)
        casecol.objects.link(ob); ob.hide_render=True
        uv=ob.data.uv_layers.active
        if uv is None: ob.data.uv_layers.new(name='CaseUV')
        caseset.append(ob); return ob
    box('Case_LowerBack',(0,.057,-.54),(.965,.025,.86))
    box('Case_CleanSide',(-.496,0,0),(.028,.12,1.88))
    box('Case_ButtonSideBottom',(.496,0,-.49),(.028,.12,.90))
    box('Case_ButtonSideTop',(.496,0,.86),(.028,.12,.16))
    box('Case_Top',(0,0,.994),(.82,.12,.026))
    box('Case_BottomFront',(0,-.053,-.994),(.82,.014,.026))
    box('Case_BottomBack',(0,.053,-.994),(.82,.014,.026))
    box('Case_Logo',(0,.071,-.58),(.25,.004,.25),'case_logo')
    base=list(caseset); forge_export(objects,folder,base,'_plain')
    for x in [-.461,.461]:
        for z in [-.954,.954]: box('Case_Bumper', (x,0,z),(.09,.15,.10),'case_accent')
    forge_export(objects,folder,caseset,'_armor')
    caseset=list(base)
    for x in [-.30,.30]:
        box('Case_Ear',(x,0,1.16),(.17,.10,.33))
        box('Case_EarAccent',(x,-.055,1.18),(.083,.013,.20),'case_accent')
    forge_export(objects,folder,caseset,'_bunny')
    casecol.hide_viewport=True; casecol.hide_render=True

def game_jsons(id,folder):
    # Clone the current transform/predicate graph, substituting only physical
    # geometry/atlas references. Never edit Classic or its screen resources.
    oldprefix='newphone:item/z_phone'; newprefix='newphone:phone/'+id+'/phone'
    visited=set()
    def emit(old):
        if old in visited: return
        visited.add(old)
        path=GAME/'item'/(old.split('/')[-1]+'.json')
        data=json.loads(path.read_text(encoding='utf-8-sig'))
        if data.get('parent','').startswith(oldprefix): emit(data['parent'])
        for override in data.get('overrides',[]): emit(override['model'])
        # Model and texture identifiers can have identical names. Only remap
        # model links; existing case/screen texture links must stay unchanged.
        if data.get('parent','').startswith(oldprefix): data['parent']=data['parent'].replace(oldprefix,newprefix,1)
        for override in data.get('overrides',[]): override['model']=override['model'].replace(oldprefix,newprefix,1)
        if data.get('loader')=='forge:obj':
            oldmodel=data['model']
            suffix=('_case_'+oldmodel.split('_case_',1)[1][:-4]) if '_case_' in oldmodel else next(('_'+s for s in ['plain','armor','bunny'] if oldmodel.endswith('_'+s+'.obj')),'')
            data['model']='newphone:models/phone/'+id+'/phone'+suffix+'.obj'
            data['textures']['atlas']='newphone:phone/'+id+'/atlas'
            data['credit']='Blender cosmetic shell '+id+'; shared MPhone screen/poses'
        name=old.split('/')[-1].replace('z_phone','phone',1)
        (folder/(name+'.json')).write_text(json.dumps(data,indent=2)+'\n')
    emit(oldprefix)

def setup_stage():
    stage=bpy.data.collections.new('Preview only'); bpy.context.scene.collection.children.link(stage)
    def light(name,loc,power,size,color):
        data=bpy.data.lights.new(name,'AREA'); data.energy=power; data.shape='DISK'; data.size=size; data.color=color
        ob=bpy.data.objects.new(name,data); stage.objects.link(ob); ob.location=loc
        ob.rotation_euler=(-ob.location).to_track_quat('-Z','Y').to_euler()
    light('Soft key',(1.5,-3.8,4.2),430,4,(.92,.95,1))
    light('Soft fill',(-3,-1.5,.8),210,3,(.8,.88,1))
    light('Rear key',(-3,2,4),400,3,(1,.94,.84))
    light('Rear fill',(3,3,-.1),160,3,(.87,.94,1))
    world=bpy.data.worlds.new('Neutral studio'); world.use_nodes=True
    world.node_tree.nodes['Background'].inputs[0].default_value=(.065,.07,.085,1)
    world.node_tree.nodes['Background'].inputs[1].default_value=.5
    scene=bpy.context.scene; scene.world=world
    data=bpy.data.cameras.new('Preview_Camera'); cam=bpy.data.objects.new('Preview_Camera',data); stage.objects.link(cam)
    cam.data.type='ORTHO'; scene.camera=cam; scene.render.engine='CYCLES'; scene.cycles.samples=24; scene.cycles.use_denoising=True
    scene.render.image_settings.file_format='PNG'; scene.render.image_settings.color_mode='RGBA'; scene.view_settings.view_transform='AgX'
    return scene,cam

def render(scene,cam,path,loc,res=(512,640),scale=2.50,transparent=False):
    cam.location=loc; cam.rotation_euler=(-cam.location).to_track_quat('-Z','Y').to_euler(); cam.data.ortho_scale=scale
    scene.render.resolution_x,scene.render.resolution_y=res; scene.render.resolution_percentage=100
    scene.render.film_transparent=transparent; scene.render.filepath=str(path); bpy.context.view_layer.update()
    bpy.ops.render.render(write_still=True)

def frame(scene,cam,out):
    temp=out/'previews/front_frame_source.png'
    render(scene,cam,temp,(0,-6,0),(600,1000),2.2,True)
    im=bpy.data.images.load(str(temp)); w,h=im.size[:]
    px=np.array(im.pixels[:],dtype=np.float32).reshape(h,w,4)
    a=world_to_camera_view(scene,cam,Vector((-.446,-.0475,-.932)))
    b=world_to_camera_view(scene,cam,Vector((.446,-.0475,.910)))
    # Preserve the existing GUI's exact 708 x 1440 content rectangle inside 900 x 1600.
    xx=np.rint((a.x+(np.arange(900)-96)/708*(b.x-a.x))*w).astype(int)
    yy=np.rint((a.y+(np.arange(1600)-82)/1440*(b.y-a.y))*h).astype(int)
    result=px[np.clip(yy,0,h-1)[:,None],np.clip(xx,0,w-1)[None,:]].copy()
    result[82:1522,96:804,3]=0
    image=bpy.data.images.new(out.name+'_frame',width=900,height=1600,alpha=True)
    image.pixels[:]=result.ravel(); image.filepath_raw=str(out/'textures/frame.png'); image.file_format='PNG'; image.save()
    bpy.data.images.remove(im); bpy.data.images.remove(image)

def build(config):
    global asset,rail,ring,glass,core,led,rims
    id,finish,color,railcolor,radius=config
    out=ART/id; folder=GAME/'phone'/id; tex=ASSETS/'textures/phone'/id
    for path in [out/'textures',out/'previews',out/'exports',folder,tex]: path.mkdir(parents=True,exist_ok=True)
    bpy.ops.wm.read_factory_settings(use_empty=True)
    asset=bpy.data.collections.new('PHONE | GAME ASSET'); bpy.context.scene.collection.children.link(asset)
    rear=material('Rear | '+finish,color,.12 if id!='model_03' else .18,.42)
    rail=material('Frame',railcolor,.78,.28)
    ring=material('Camera rings',railcolor,.82,.25)
    edge=material('Edge glint',tuple(min(1,c*1.2+.015) for c in railcolor),.75,.25)
    black=material('Front bezel',(.008,.011,.015),.15,.32)
    glass=material('Lens glass',(.009,.017,.029),.35,.19)
    core=material('Lens core',(.016,.038,.052),.38,.2)
    led=material('LED',(.79,.77,.58),.1,.26)
    screenmat=material('Display | dynamic GUI replacement',(.005,.009,.012),.15,.20)
    if id=='model_01':
        # This source shader is baked out; no procedural nodes ship to Minecraft.
        nt=rear.node_tree; noise=nt.nodes.new('ShaderNodeTexNoise'); noise.inputs['Scale'].default_value=165
        ramp=nt.nodes.new('ShaderNodeValToRGB')
        ramp.color_ramp.elements[0].color=(*[c*.78 for c in color],1); ramp.color_ramp.elements[1].color=(*[c*1.17 for c in color],1)
        nt.links.new(noise.outputs['Fac'],ramp.inputs['Fac']); nt.links.new(ramp.outputs['Color'],nt.nodes.get('Principled BSDF').inputs['Base Color'])
    body=shell('Phone_Body',[(-.045,.948,1.978,radius),(-.038,.97,2,radius+.007),(.032,.97,2,radius+.007),(.045,.948,1.978,radius)],
            [rail,rear,edge,black],front=3,back=1,bands=[2,0,2])
    subtract(body,cube('Screen seat',(0,-.047,-.011),(.8922,.020,1.8422),black))
    screen=cube('phone_screen',(0,-.0455,-.011),(.892,.004,1.842),screenmat)
    lathe('Front_Camera',0,.943,[(.014,-.045),(.014,-.048)],glass,12)
    cube('Earpiece',(0,-.046,.973),(.09,.002,.005),black,.001)
    cube('Power_Button',(.490,0,.24),(.022,.036,.17),rail,.004)
    join('Volume_Buttons',[cube('Volume_Up',(.490,0,.65),(.022,.032,.11),rail,.004),cube('Volume_Down',(.490,0,.49),(.022,.032,.11),rail,.004)])
    subtract(body,cube('USB recess',(0,0,-.997),(.16,.038,.025),black,.006))
    details=[cube('USB_socket',(0,0,-.987),(.147,.029,.004),black,.002),cube('USB_tongue',(0,-.001,-.992),(.09,.009,.003),rail,.001)]
    for x in [-.29,-.24,-.19,.19,.24,.29]: details.append(cube('Speaker',(x,0,-1.0004),(.017,.022,.001),black,.002))
    join('Bottom_Details',details)
    rims=[]
    if id=='model_01':
        island=material('Camera platform',(.033,.041,.049),.4,.29)
        shell('Camera_Bump',[(.045,.478,.568,.063),(.070,.478,.568,.063),(.079,.458,.548,.058)],[island,rail],center=(.202,.662),bands=[1,0])
        for i,(x,z) in enumerate([(.319,.806),(.086,.64),(.319,.492)],1): lens(i,x,z,.093,.079)
        flash(.070,.829,.079,.025)
        lathe('Focus_Sensor',.084,.467,[(.018,.079),(.018,.081)],glass,12)
    elif id=='model_02':
        shell('Camera_Bump',[(.045,.236,.69,.115),(.063,.236,.69,.115)],[rail,rear],center=(.308,.612),back=1)
        lens(1,.308,.798,.091,.063); lens(2,.308,.550,.091,.063); flash(.308,.334,.063,.027)
    elif id=='model_03':
        shell('Camera_Bump',[(.045,.225,.916,.105),(.064,.225,.916,.105)],[rail,rear],center=(.306,.507),back=1)
        for i,z in enumerate([.827,.587,.347],1): lens(i,.306,z,.085,.064)
        flash(.306,.141,.064,.024)
    elif id=='model_04':
        orange=material('Orange lower back',(.86,.235,.040),.06,.46)
        # A second rear panel seated directly against the body with a shallow curved seam.
        pts=outline(.948,1.978,radius)
        lower=[(x,z) for x,z in pts if z<-.5]
        poly=[(.474,.0),(.24,-.014),(0,-.03),(-.24,-.014),(-.474,.0)]+lower
        n=len(poly); verts=[(x,y,z) for y in [.045,.0465] for x,z in poly]
        faces=[tuple(range(n-1,-1,-1)),tuple(n+i for i in range(n))]+[(i,(i+1)%n,n+(i+1)%n,n+i) for i in range(n)]
        mesh('Two_Tone_Rear',verts,faces,[orange])
        lens(1,.300,.797,.106,.045); lens(2,.300,.521,.106,.045); flash(.064,.755,.045,.029)
    elif id=='model_05':
        island=material('Circular camera platform',(.014,.026,.021),.38,.28)
        lathe('Camera_Bump',0,.598,[(.343,.045),(.367,.065),(.367,.079),(.345,.090)],ring,32)
        lathe('Camera_Plate',0,.598,[(.337,.090),(.337,.091)],island,32)
        for i,(x,z) in enumerate([(0,.778),(-.163,.466),(.163,.466)],1): lens(i,x,z,.088,.091)
        flash(-.211,.741,.091,.027)
    join('Camera_Rings',rims)
    # Restrained maker's plate without high-poly text or fictional UI artwork.
    logo=material('Subtle rear mark',tuple(c*.73 for c in color),.35,.45)
    cube('Logo',(0,.048,-.695),(.15,.002,.009),logo,.001)
    objects=[o for o in asset.objects if o.type=='MESH']
    report=bake_and_validate(out,objects,screen)
    bpy.ops.object.select_all(action='DESELECT')
    for ob in objects: ob.select_set(True)
    bpy.context.view_layer.objects.active=body
    bpy.ops.export_scene.gltf(filepath=str(out/'exports'/(id+'.glb')),export_format='GLB',use_selection=True,export_yup=True,export_extras=True,export_cameras=False,export_lights=False)
    forge_export(objects,folder)
    mats=[]
    for mat in ['atlas','screen','black','case','case_accent','case_logo']:
        mats.extend(['newmtl '+mat,'Ka '+('1 1 1' if mat=='screen' else '0 0 0'),'Kd 1 1 1','d 1','forge_TintIndex -1','map_Kd #'+mat,''])
    (folder/'phone.mtl').write_text('\n'.join(mats))
    cases(objects,folder); game_jsons(id,folder)
    shutil.copyfile(folder/'phone.obj',out/'exports'/(id+'.obj')); shutil.copyfile(folder/'phone.mtl',out/'exports/phone.mtl')
    scene,cam=setup_stage()
    render(scene,cam,out/'previews/front.png',(2.7,-6,1.35))
    render(scene,cam,out/'previews/rear.png',(-2.2,6,1.25))
    render(scene,cam,out/'textures/preview.png',(-1.75,6,.65),(256,288),2.32,True)
    frame(scene,cam,out)
    for view,loc,res,scale in [('back',(0,6,0),(400,600),2.25),('left',(-6,0,0),(220,600),2.25),('right',(6,0,0),(220,600),2.25),('top',(0,-.001,6),(600,200),1.17),('bottom',(0,-.001,-6),(600,200),1.17)]:
        scene.cycles.samples=8; render(scene,cam,out/'previews'/(view+'.png'),loc,res,scale)
    for filename in ['atlas.png','preview.png','frame.png']: shutil.copyfile(out/'textures'/filename,tex/filename)
    scene['model_id']=id; scene['finish']=finish; scene['triangles']=report['triangles']; scene['screen_surface']='-Y; X[-.446,.446], Z[-.932,.910], Y=-.0475; rectangular UV 0..1'
    cam.location=(-2.2,6,1.25); cam.rotation_euler=(-cam.location).to_track_quat('-Z','Y').to_euler(); cam.data.ortho_scale=2.5
    scene.render.resolution_x=512; scene.render.resolution_y=640; scene.render.film_transparent=False; scene.cycles.samples=32
    activate(body)
    for block in [bpy.data.materials,bpy.data.meshes,bpy.data.images]:
        for item in list(block):
            if not item.users and item.name!='Render Result': block.remove(item)
    bpy.ops.wm.save_as_mainfile(filepath=str(out/(id+'.blend')))
    print('SHELL_COMPLETE '+id+' '+str(report['triangles']),flush=True)

if __name__=='__main__':
    requested=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
    atlas_file=ASSETS.parent/'minecraft/atlases/blocks.json'
    atlas_file.parent.mkdir(parents=True,exist_ok=True)
    atlas_file.write_text(json.dumps({'sources':[{'type':'minecraft:single','resource':'newphone:phone/'+c[0]+'/atlas'} for c in CONFIGS]},indent=2)+'\n')
    if '--resources-only' in requested:
        for config in CONFIGS: game_jsons(config[0],GAME/'phone'/config[0])
        print('PHONE_SHELL_JSON_COMPLETE',flush=True)
        sys.exit(0)
    for config in CONFIGS:
        if not requested or config[0] in requested: build(config)
    # The original source and runtime files remain byte-for-byte unchanged.
    classic=ART/'classic'; classic.mkdir(parents=True,exist_ok=True)
    (classic/'README.md').write_text('Classic remains the original asset. Editable Blender source: ../../art/mikasrevs_phone_2/mikasrevs_phone_2.blend\nRuntime: assets/newphone/models/item/z_phone.json\n')
    tex=ASSETS/'textures/phone/classic'; tex.mkdir(parents=True,exist_ok=True)
    bpy.ops.wm.open_mainfile(filepath=str(ROOT/'art/mikasrevs_phone_2/mikasrevs_phone_2.blend'))
    scene=bpy.context.scene; cam=scene.camera; scene.cycles.samples=12
    render(scene,cam,tex/'preview.png',(-1.75,6,.65),(256,288),2.32,True)
    print('ALL_PHONE_SHELLS_COMPLETE',flush=True)
