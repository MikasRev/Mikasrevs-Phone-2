"""Validate every phone predicate target, Forge material binding and OBJ face.
Uses only the Python standard library; also works with Blender's bundled Python.
"""
import json
import math
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets'
ITEM=ASSETS/'newphone/models/item'

def resource(location, prefix='', suffix=''):
    namespace,path=location.split(':',1)
    return ASSETS/namespace/(prefix+path+suffix)

def resolve(name, chain=()):
    assert name not in chain, ('parent cycle',chain,name)
    data=json.loads(resource(name,'models/','.json').read_text(encoding='utf-8-sig'))
    parent=resolve(data['parent'],chain+(name,)) if 'parent' in data else {}
    return {**parent,**data,'textures':{**parent.get('textures',{}),**data.get('textures',{})},
            'display':{**parent.get('display',{}),**data.get('display',{})}}

def texture(name,textures):
    seen=set()
    while name.startswith('#'):
        assert name not in seen,('texture cycle',name)
        seen.add(name)
        name=textures[name[1:]]
    assert resource(name,'textures/','.png').is_file(),name
    return name

def obj_info(path):
    verts=[]
    uvs=[]
    normals=[]
    faces=[]
    material=group=None
    mtllib=None
    for line in path.read_text().splitlines():
        fields=line.split()
        if not fields or fields[0].startswith('#'): continue
        tag=fields[0]
        if tag=='v': verts.append(tuple(map(float,fields[1:4])))
        elif tag=='vt': uvs.append(tuple(map(float,fields[1:3])))
        elif tag=='vn': normals.append(tuple(map(float,fields[1:4])))
        elif tag=='mtllib': mtllib=resource(fields[1])
        elif tag=='g': group=fields[1]
        elif tag=='usemtl': material=fields[1]
        elif tag=='f':
            indices=[tuple(int(i)-1 for i in field.split('/')) for field in fields[1:]]
            assert len(indices) in (3,4)
            for v,t,n in indices:
                assert 0<=v<len(verts) and 0<=t<len(uvs) and 0<=n<len(normals)
                assert all(-1e-6<=c<=1.000001 for c in uvs[t])
            a,b,c=(verts[indices[i][0]] for i in range(3))
            ab=tuple(b[i]-a[i] for i in range(3))
            ac=tuple(c[i]-a[i] for i in range(3))
            cross=(ab[1]*ac[2]-ab[2]*ac[1],ab[2]*ac[0]-ab[0]*ac[2],ab[0]*ac[1]-ab[1]*ac[0])
            assert sum(cross[i]*normals[indices[0][2]][i] for i in range(3))>1e-13,(path,group,'winding')
            faces.append((group,material,indices))
    materials={}
    for line in mtllib.read_text().splitlines():
        p=line.split()
        if not p: continue
        if p[0]=='newmtl': current=p[1]
        elif p[0]=='map_Kd': materials[current]=p[1]
    display=[f for f in faces if f[1]=='screen']
    assert len(display)==2 and all(f[0]=='phone_screen' for f in display)
    corners=set()
    for _,_,face in display:
        for v,t,n in face:
            assert abs(verts[v][2]-.681/16)<1e-7
            assert normals[n]==(0.,0.,-1.)
            corners.add(tuple(round(c,6) for c in uvs[t]))
    assert corners=={(0.,0.),(0.,1.),(1.,0.),(1.,1.)},corners
    return {'triangles':sum(len(f[2])-2 for f in faces),
            'bindings':{materials[f[1]] for f in faces},'screen_faces':len(display)}

dispatcher=json.loads((ITEM/'z_phone.json').read_text(encoding='utf-8-sig'))
assert len(dispatcher['overrides'])==41
names={'newphone:item/z_phone','newphone:item/z_phone_on','newphone:item/z_phone_base'}
names.update(o['model'] for o in dispatcher['overrides'])
models={}
for name in sorted(names):
    model=resolve(name)
    assert model['loader']=='forge:obj' and 'elements' not in model,name
    assert model['flip_v'] and not model['automatic_culling'],name
    path=resource(model['model'])
    if path not in models: models[path]=obj_info(path)
    for binding in models[path]['bindings']: texture(binding,model['textures'])
    assert 'firstperson_righthand' in model['display'] and 'firstperson_lefthand' in model['display']
    if '_boot_' in name:
        assert model['textures']['screen'].endswith('_'+name.rsplit('_',1)[1])
    if name.endswith('_call') or name.endswith('_on'):
        assert model['textures']['screen'].endswith('_5')
    if name.endswith('_call'):
        # Call placement is owned by the client renderers, never inventory JSON.
        call=json.loads(resource(name,'models/','.json').read_text(encoding='utf-8-sig'))
        assert 'display' not in call
        assert model['display']==resolve(call['parent'])['display']
assert len(models)==4
assert models[resource('newphone:models/item/mikasrevs_phone_2.obj')]['triangles']==2422
print('PHONE2_ASSETS_OK: %d models, 41 predicates, 4 OBJ meshes; material paths, winding, screen UVs and inherited item transforms verified.'%len(names))
