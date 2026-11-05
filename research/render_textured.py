#!/usr/bin/env python3
"""Orthographic, backface-culled OBJ/MTL preview with texture sampling and a z-buffer.

Dependencies: numpy and Pillow. Does not modify exported geometry or texture UVs.
"""
import argparse
from pathlib import Path
import numpy as np
from PIL import Image


def render(path,output,width=900,height=900):
    vertices=[];uv=[];normals=[];faces=[];material=None;textures={};mtl=None
    for line in path.read_text().splitlines():
        p=line.split()
        if not p:continue
        if p[0]=='mtllib':mtl=path.parent/p[1]
        elif p[0]=='v':vertices.append(list(map(float,p[1:4])))
        elif p[0]=='vt':uv.append(list(map(float,p[1:3])))
        elif p[0]=='vn':normals.append(list(map(float,p[1:4])))
        elif p[0]=='usemtl':material=p[1]
        elif p[0]=='f':faces.append((material,[[int(i)-1 for i in token.split('/')] for token in p[1:]]))
    if mtl:
        key=None
        for line in mtl.read_text().splitlines():
            p=line.split(maxsplit=1)
            if p and p[0]=='newmtl':key=p[1]
            elif p and p[0]=='map_Kd':textures[key]=np.array(Image.open(mtl.parent/p[1]).convert('RGBA'))/255.
    v=np.array(vertices);uv=np.array(uv);normals=np.array(normals)
    direction=np.array([-.42,.15,1.]);direction/=np.linalg.norm(direction)
    right=np.cross([0.,1.,0.],direction);right/=np.linalg.norm(right);up=np.cross(direction,right)
    center=(v.min(0)+v.max(0))/2;v=v-center
    view=np.stack((v@right,v@up,v@direction),axis=1)
    span=np.ptp(view[:,:2],axis=0);scale=min((width-70)/span[0],(height-70)/span[1]);view[:,0]=view[:,0]*scale+width/2;view[:,1]=-view[:,1]*scale+height/2
    pixels=np.zeros((height,width,3))+np.array([.10,.12,.15]);depth=np.full((height,width),-np.inf)
    light=direction+np.array([-.3,.7,.3]);light/=np.linalg.norm(light)
    for material,face in faces:
        ids=np.array(face);positions=v[ids[:,0]];n=np.cross(positions[1]-positions[0],positions[2]-positions[0])
        if n@direction<=0:continue
        tri=view[ids[:,0]];lo=np.maximum(0,np.floor(tri[:,:2].min(0)).astype(int));hi=np.minimum([width-1,height-1],np.ceil(tri[:,:2].max(0)).astype(int))
        if np.any(hi<lo):continue
        xx,yy=np.meshgrid(np.arange(lo[0],hi[0]+1)+.5,np.arange(lo[1],hi[1]+1)+.5)
        a,b,c=tri[:,:2];den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
        if abs(den)<1e-8:continue
        w0=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den;w1=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;w2=1-w0-w1
        bary=np.stack((w0,w1,w2),axis=-1);z=bary@tri[:,2];target=depth[lo[1]:hi[1]+1,lo[0]:hi[0]+1];mask=(bary.min(-1)>=-1e-6)&(z>target)
        if not mask.any():continue
        texture=textures.get(material)
        if texture is not None:
            coords=bary@uv[ids[:,1]];tx=np.clip((coords[:,:,0]*texture.shape[1]).astype(int),0,texture.shape[1]-1);ty=np.clip(((1-coords[:,:,1])*texture.shape[0]).astype(int),0,texture.shape[0]-1);color=texture[ty,tx];mask &=color[:,:,3]>.5
        else:color=np.ones((*mask.shape,4))*.75
        normal=bary@normals[ids[:,2]];normal/=np.maximum(np.linalg.norm(normal,axis=-1,keepdims=True),1e-8);lighting=.45+.55*np.maximum(0,normal@light)
        patch=pixels[lo[1]:hi[1]+1,lo[0]:hi[0]+1];patch[mask]=color[:,:,:3][mask]*lighting[mask,None];target[mask]=z[mask]
    output.parent.mkdir(parents=True,exist_ok=True);Image.fromarray((np.clip(pixels,0,1)*255).astype('uint8')).save(output)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('obj',type=Path);p.add_argument('--out',type=Path,required=True);a=p.parse_args();render(a.obj,a.out)
