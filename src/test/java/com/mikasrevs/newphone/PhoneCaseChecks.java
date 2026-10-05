package com.mikasrevs.newphone;

import com.google.gson.JsonParser;
import com.mikasrevs.newphone.client.zphone.PhoneSearchMotion;
import com.mikasrevs.newphone.phone.model.PhoneCaseTextures;
import com.mikasrevs.newphone.phone.model.PhoneModelRegistry;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Verify the complete case/model matrix and reversible search motion without a graphical client. */
final class PhoneCaseChecks {
    static void run() throws Exception {
        searchMotion();
        Path root=Path.of("src/main/resources/assets/newphone");
        Set<String> modelPaths=new HashSet<>(),framePaths=new HashSet<>();
        for(var model:PhoneModelRegistry.all()) {
            boolean classic=model.id().equals("classic");
            Path folder=root.resolve(classic?"models/item":"models/phone/"+model.id());
            String base=Files.readString(folder.resolve(classic?"mikasrevs_phone_2.obj":"phone.obj"));
            for(var style:PhoneCaseStyle.values()) {
                if(style==PhoneCaseStyle.NONE){require(PhoneCaseTextures.frame(model.id(),style)==null,"bare phone has a case overlay");continue;}
                var frame=PhoneCaseTextures.frame(model.id(),style);
                require(frame!=null&&framePaths.add(frame.toString()),"case frame reused across chassis");
                var png=ImageIO.read(root.resolve(frame.getPath()).toFile());
                require(png.getWidth()==900&&png.getHeight()==1760,"case GUI changes physical screen coordinates");
                // Content remains visible inside the rounded aperture. The corners of
                // the rectangular wallpaper must instead be covered by opaque case pixels.
                var visibleContent = new java.awt.geom.RoundRectangle2D.Double(100,242,700,1432,192,192);
                for(int y=238;y<1678;y+=4)for(int x=96;x<804;x+=4)
                    if(visibleContent.contains(x+.5,y+.5))
                        require((png.getRGB(x,y)>>>24)==0,"case hides application content: "+frame);
                for(int cornerX:new int[]{96,795})for(int cornerY:new int[]{238,1669})
                    for(int y=cornerY;y<cornerY+9;y++)for(int x=cornerX;x<cornerX+9;x++)
                        require((png.getRGB(x,y)>>>24)==255,"square wallpaper leaks through case corner: "+frame);
                boolean bunny=style==PhoneCaseStyle.BUNNY_BLUSH||style==PhoneCaseStyle.BUNNY_BLOCK;
                require(((png.getRGB(290,110)>>>24)>0)==bunny,"wrong ear silhouette: "+frame);
                var json=JsonParser.parseString(Files.readString(folder.resolve((classic?"z_phone":"phone")+"_case_"+style.id()+".json"))).getAsJsonObject();
                String location=json.get("model").getAsString();
                require(modelPaths.add(location)&&location.endsWith("_case_"+style.id()+".obj"),"case uses a generic or mismatched model");
                String obj=Files.readString(root.resolve(location.substring("newphone:".length())));
                if(bunny)inventoryBounds(obj,json.getAsJsonObject("display").getAsJsonObject("gui"));
                require(obj.startsWith(base),"case export modified phone body, camera or live display UVs");
                require(obj.contains("Case_V2_CameraGuard")&&obj.contains("Case_V2_Signature"),"fitted case components missing");
                require(obj.contains("Case_V2_EarInsert")==bunny,"wrong 3D ear variant");
                validateObj(obj,base.length(),model.id()+"/"+style.id());
                railCoverage(obj,model.id()+"/"+style.id());
            }
        }
        require(modelPaths.size()==30&&framePaths.size()==30,"incomplete 6 x 5 accessory matrix");
        require(PhoneCaseTextures.frame("unknown",PhoneCaseStyle.BUNNY_BLUSH)
                .equals(PhoneCaseTextures.frame("classic",PhoneCaseStyle.BUNNY_BLUSH)),"unknown chassis has no case fallback");
        System.out.println("Phone case checks passed (30 fitted meshes/frames, display clearance, OBJ/UV validity, reversible search).");
    }
    private static void searchMotion() {
        var m=new PhoneSearchMotion();long start=1_000_000_000L;
        require(m.sample(false,true,start)==0,"search starts expanded");
        require(m.sample(true,true,start)==0,"search jumps on focus");
        float half=m.sample(true,true,start+140_000_000L);
        require(half>0&&half<1,"search did not animate");
        require(PhoneSearchMotion.left(half)+PhoneSearchMotion.right(half)==300,"search is not centered during expansion");
        require(m.sample(false,true,start+140_000_000L)==half,"reversing animation causes a position jump");
        require(m.sample(false,true,start+420_000_000L)==0,"search did not collapse");
        require(m.sample(true,false,start+500_000_000L)==1,"disabled animations still animate");
        require(PhoneSearchMotion.left(1)==18&&PhoneSearchMotion.right(1)==282,"expanded click bounds wrong");
        require(PhoneSearchMotion.left(0)==100&&PhoneSearchMotion.right(0)==200,"collapsed click bounds wrong");
    }
    private static void validateObj(String obj,int baseLength,String id) {
        List<double[]> vertices=new ArrayList<>(),normals=new ArrayList<>();int uvCount=0,offset=0,caseFaces=0;
        Set<String> groups=new HashSet<>();
        for(String line:obj.split("\n")) {
            boolean accessory=offset>=baseLength;offset+=line.length()+1;
            String[] p=line.trim().split("\\s+");
            if(p[0].equals("g"))require(groups.add(p[1]),"Forge would replace an earlier OBJ group: "+id+"/"+p[1]);
            else if(p[0].equals("v")) {
                double[] v={Double.parseDouble(p[1]),Double.parseDouble(p[2]),Double.parseDouble(p[3])};vertices.add(v);
                require(Arrays.stream(v).allMatch(Double::isFinite),"non-finite geometry: "+id);
                if(accessory&&v[2]*16<.681-1e-5)require(v[0]*16<=3.0376||v[0]*16>=6.9624||v[1]*16<=9.3992||v[1]*16>=17.504,"case intersects live screen: "+id);
            }else if(p[0].equals("vt")) {
                uvCount++;if(accessory){double u=Double.parseDouble(p[1]),v=Double.parseDouble(p[2]);require(u>=0&&u<=1&&v>=0&&v<=1,"case UV bleeds into another atlas sprite: "+id);}
            }else if(p[0].equals("vn"))normals.add(new double[]{Double.parseDouble(p[1]),Double.parseDouble(p[2]),Double.parseDouble(p[3])});
            else if(p[0].equals("f")&&accessory) {
                caseFaces++;double[][] triangle=new double[3][];double[] normal=null;
                for(int i=1;i<p.length;i++) {
                    String[] indices=p[i].split("/");int vi=Integer.parseInt(indices[0]),ti=Integer.parseInt(indices[1]),ni=Integer.parseInt(indices[2]);
                    require(vi>0&&vi<=vertices.size()&&ti>0&&ti<=uvCount&&ni>0&&ni<=normals.size(),"invalid OBJ index: "+id);
                    if(i<=3)triangle[i-1]=vertices.get(vi-1);normal=normals.get(ni-1);
                }
                double[] a=triangle[0],b=triangle[1],c=triangle[2];
                double nx=(b[1]-a[1])*(c[2]-a[2])-(b[2]-a[2])*(c[1]-a[1]);
                double ny=(b[2]-a[2])*(c[0]-a[0])-(b[0]-a[0])*(c[2]-a[2]);
                double nz=(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
                require(nx*normal[0]+ny*normal[1]+nz*normal[2]>0,"degenerate or reversed case face: "+id);
            }
        }
        require(caseFaces>500&&caseFaces<2000,"accessory geometry budget: "+id+"="+caseFaces);
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}

    private static void inventoryBounds(String obj,com.google.gson.JsonObject gui){
        var r=gui.getAsJsonArray("rotation");var t=gui.getAsJsonArray("translation");var s=gui.getAsJsonArray("scale");
        var matrix=new org.joml.Matrix4f().translate(t.get(0).getAsFloat()/16,t.get(1).getAsFloat()/16,t.get(2).getAsFloat()/16)
                .rotateXYZ((float)Math.toRadians(r.get(0).getAsFloat()),(float)Math.toRadians(r.get(1).getAsFloat()),(float)Math.toRadians(r.get(2).getAsFloat()))
                .scale(s.get(0).getAsFloat(),s.get(1).getAsFloat(),s.get(2).getAsFloat()).translate(-.5F,-.5F,-.5F);
        for(String line:obj.split("\n"))if(line.startsWith("v ")){
            var p=line.trim().split("\\s+");var point=matrix.transformPosition(new org.joml.Vector3f(Float.parseFloat(p[1]),Float.parseFloat(p[2]),Float.parseFloat(p[3])));
            require(Math.abs(point.x)<.46F&&Math.abs(point.y)<.46F,"Bunny phone protrudes outside inventory slot");
        }
    }

    /** Probe the exported outer side walls, including the long segment beside the buttons. */
    private static void railCoverage(String obj,String id) {
        List<double[]> vertices=new ArrayList<>();
        List<double[][]> walls=new ArrayList<>();
        Map<String,double[]> bounds=new HashMap<>();
        String group="";
        for(String line:obj.split("\n")) {
            String[] p=line.trim().split("\\s+");
            if(p[0].equals("g"))group=p[1];
            else if(p[0].equals("v")) {
                double[] v={Double.parseDouble(p[1])*16,Double.parseDouble(p[2])*16,Double.parseDouble(p[3])*16};
                vertices.add(v);
                if(Set.of("Phone_Body","Power_Button","Volume_Buttons").contains(group)) {
                    double[] b=bounds.computeIfAbsent(group,k->new double[]{1e9,1e9,1e9,-1e9,-1e9,-1e9});
                    for(int i=0;i<3;i++){b[i]=Math.min(b[i],v[i]);b[i+3]=Math.max(b[i+3],v[i]);}
                }
            }else if(p[0].equals("f")&&group.startsWith("Case_V2_FittedRail")) {
                double[][] face=new double[p.length-1][];
                for(int i=1;i<p.length;i++)face[i-1]=vertices.get(Integer.parseInt(p[i].split("/")[0])-1);
                walls.add(face);
            }
        }
        double[] body=bounds.get("Phone_Body");
        for(double y=body[1]+1;y<body[4]-1;y+=.097) {
            double middleZ=(body[2]+body[5])/2;
            boolean buttonWindow=false;
            for(String name:List.of("Power_Button","Volume_Buttons")) {
                double[] button=bounds.get(name);
                buttonWindow|=y>button[1]-.11&&y<button[4]+.11;
            }
            require(wallAt(walls,body[0]-.105,y,body[2]-.02),"missing front edge beside buttons: "+id+" at "+y);
            require(wallAt(walls,body[0]-.105,y,body[5]+.10),"missing rear edge beside buttons: "+id+" at "+y);
            require(wallAt(walls,body[0]-.105,y,middleZ)!=buttonWindow,"incorrect button opening or missing side wall: "+id+" at "+y);
            require(wallAt(walls,body[3]+.105,y,middleZ),"missing opposite side wall: "+id+" at "+y);
        }
    }
    private static boolean wallAt(List<double[][]> walls,double x,double y,double z) {
        for(double[][] face:walls) {
            if(Arrays.stream(face).anyMatch(v->Math.abs(v[0]-x)>1e-5))continue;
            for(int i=1;i<face.length-1;i++) {
                double[] a=face[0],b=face[i],c=face[i+1];
                double ab=(b[1]-a[1])*(z-a[2])-(b[2]-a[2])*(y-a[1]);
                double bc=(c[1]-b[1])*(z-b[2])-(c[2]-b[2])*(y-b[1]);
                double ca=(a[1]-c[1])*(z-c[2])-(a[2]-c[2])*(y-c[1]);
                if((ab>=-1e-8&&bc>=-1e-8&&ca>=-1e-8)||(ab<=1e-8&&bc<=1e-8&&ca<=1e-8))return true;
            }
        }
        return false;
    }
}
