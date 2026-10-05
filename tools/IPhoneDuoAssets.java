import java.nio.file.*;
import java.net.URLClassLoader;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.tools.ToolProvider;

/** Exports the actual runtime geometry as editable Blockbench and open/closed OBJ assets. */
public class IPhoneDuoAssets {
    record Box(String name,String group,float x,float y,float z,float w,float h,float d,int color){}
    public static void main(String[] args)throws Exception{
        Path root=Path.of("").toAbsolutePath(),classes=root.resolve("build/duo-assets/classes"),out=root.resolve("art/iphone_duo");Files.createDirectories(classes);Files.createDirectories(out);
        Path source=root.resolve("src/main/java/com/mikasrevs/newphone/client/duo/DuoModelGeometry.java");
        if(ToolProvider.getSystemJavaCompiler().run(null,null,null,"-encoding","UTF-8","-d",classes.toString(),source.toString())!=0)throw new IllegalStateException("Geometry compilation failed");
        List<Box> boxes=new ArrayList<>();
        try(var loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            var type=loader.loadClass("com.mikasrevs.newphone.client.duo.DuoModelGeometry");
            for(Object b:(List<?>)type.getField("BOXES").get(null)){
                Class<?> t=b.getClass();boxes.add(new Box((String)t.getMethod("name").invoke(b),t.getMethod("group").invoke(b).toString(),f(t,b,"x"),f(t,b,"y"),f(t,b,"z"),f(t,b,"width"),f(t,b,"height"),f(t,b,"depth"),(Integer)t.getMethod("color").invoke(b)));
            }
        }
        Path textures=root.resolve("src/main/resources/assets/newphone/textures/item");Files.createDirectories(textures);
        var white=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<16;y++)for(int x=0;x<16;x++)white.setRGB(x,y,0xFFFFFFFF);ImageIO.write(white,"png",textures.resolve("iphone_duo_material.png").toFile());
        List<Integer> colors=boxes.stream().map(Box::color).distinct().toList();var palette=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
        for(int i=0;i<colors.size();i++)for(int yy=0;yy<8;yy++)for(int xx=0;xx<8;xx++)palette.setRGB((i%8)*8+xx,(i/8)*8+yy,colors.get(i));ImageIO.write(palette,"png",out.resolve("duo_palette.png").toFile());
        List<Object> elements=new ArrayList<>(),children=new ArrayList<>();
        for(String group:List.of("BODY_LEFT","BODY_RIGHT","HINGE")){
            List<String> members=new ArrayList<>();
            for(var b:boxes)if(b.group.equals(group)){
                String id=UUID.nameUUIDFromBytes((b.group+":"+b.name).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();members.add(id);int index=colors.indexOf(b.color);Map<String,Object> faces=new LinkedHashMap<>();
                for(String face:List.of("north","south","east","west","up","down"))faces.put(face,Map.of("uv",List.of((index%8)*8+1,(index/8)*8+1,(index%8)*8+7,(index/8)*8+7),"texture",0));
                elements.add(Map.of("name",b.name,"uuid",id,"type","cube","from",List.of(b.x*.025,-(b.y+b.h)*.025,b.z*.025),"to",List.of((b.x+b.w)*.025,-b.y*.025,(b.z+b.d)*.025),"origin",List.of(0,0,0),"faces",faces));
            }
            children.add(Map.of("name",group.toLowerCase(Locale.ROOT),"uuid",UUID.nameUUIDFromBytes(group.getBytes()).toString(),"origin",List.of(0,0,0),"children",members));
        }
        String right=UUID.nameUUIDFromBytes("BODY_RIGHT".getBytes()).toString();List<Object> animations=new ArrayList<>();
        for(boolean opening:new boolean[]{true,false}){
            List<Object> keys=new ArrayList<>();for(int frame=0;frame<2;frame++){boolean folded=(frame==0)==opening;keys.add(Map.of("channel","rotation","time",frame*.32,"data_points",List.of(Map.of("x","0","y",folded?"-180":"0","z","0")),"interpolation","linear"));keys.add(Map.of("channel","position","time",frame*.32,"data_points",List.of(Map.of("x","0","y","0","z",folded?"0.3":"0")),"interpolation","linear"));}
            animations.add(Map.of("name",opening?"unfold":"fold","loop","hold","length",.32,"animators",Map.of(right,Map.of("name","body_right","type","bone","keyframes",keys))));
        }
        var texture=Map.of("name","duo_palette.png","id","0","uuid",UUID.randomUUID().toString(),"source","data:image/png;base64,"+Base64.getEncoder().encodeToString(Files.readAllBytes(out.resolve("duo_palette.png"))));
        Files.writeString(out.resolve("iphone_duo.bbmodel"),json(Map.of("meta",Map.of("format_version","4.10","model_format","free","box_uv",false),"name","iPhone Duo","resolution",Map.of("width",64,"height",64),"elements",elements,"outliner",List.of(Map.of("name","iphone_duo","uuid",UUID.randomUUID().toString(),"origin",List.of(0,0,0),"children",children)),"textures",List.of(texture),"animations",animations)));
        for(boolean closed:new boolean[]{false,true}){
            StringBuilder obj=new StringBuilder("mtllib iphone_duo.mtl\n");int first=1;
            for(var b:boxes){obj.append("g ").append(b.group.toLowerCase(Locale.ROOT)).append('\n').append("o ").append(b.name).append('\n').append("usemtl material_").append(colors.indexOf(b.color)).append('\n');
                for(float[] v:vertices(b)){float x=v[0],z=v[2];if(closed&&b.group.equals("BODY_RIGHT")){x=-x;z=12-z;}obj.append(String.format(Locale.ROOT,"v %.6f %.6f %.6f%n",x*.025,-v[1]*.025,z*.025));}
                for(int[] face:new int[][]{{0,3,2,1},{4,5,6,7},{0,1,5,4},{3,7,6,2},{0,4,7,3},{1,2,6,5}}){obj.append("f");for(int v:face)obj.append(' ').append(first+v);obj.append('\n');}first+=8;
            }Files.writeString(out.resolve(closed?"iphone_duo_closed.obj":"iphone_duo_open.obj"),obj);
        }
        StringBuilder mtl=new StringBuilder();for(int i=0;i<colors.size();i++){int c=colors.get(i);mtl.append(String.format(Locale.ROOT,"newmtl material_%d%nKd %.6f %.6f %.6f%n",i,((c>>16)&255)/255.0,((c>>8)&255)/255.0,(c&255)/255.0));}Files.writeString(out.resolve("iphone_duo.mtl"),mtl);
        Path models=root.resolve("src/main/resources/assets/newphone/models/item");Files.createDirectories(models);
        Map<String,Object> displays=new LinkedHashMap<>();
        displays.put("gui",transform(List.of(0,-20,0),List.of(0,0,0),1.15));
        for(String hand:List.of("firstperson_righthand","firstperson_lefthand"))displays.put(hand,transform(List.of(0,0,0),List.of(0,0,0),1));
        for(String hand:List.of("thirdperson_righthand","thirdperson_lefthand"))displays.put(hand,transform(List.of(10,0,0),List.of(0,2,0),.6));
        displays.put("ground",transform(List.of(0,0,0),List.of(0,2,0),.65));displays.put("fixed",transform(List.of(0,0,0),List.of(0,0,0),1));
        Files.writeString(models.resolve("iphone_duo.json"),json(Map.of("parent","builtin/entity","textures",Map.of("particle","newphone:item/iphone_duo_material"),"display",displays)));
        System.out.println("Exported "+boxes.size()+" mesh pieces; Blockbench hinge animations, open/closed OBJ, materials and Forge item assets to "+out);
    }
    static float f(Class<?> type,Object b,String name)throws Exception{return (Float)type.getMethod(name).invoke(b);}
    static Map<String,Object> transform(List<Integer> rotation,List<Integer> translation,double scale){return Map.of("rotation",rotation,"translation",translation,"scale",List.of(scale,scale,scale));}
    static float[][] vertices(Box b){float x=b.x,y=b.y,z=b.z,r=x+b.w,t=y+b.h,q=z+b.d;return new float[][]{{x,y,z},{r,y,z},{r,t,z},{x,t,z},{x,y,q},{r,y,q},{r,t,q},{x,t,q}};}
    static String json(Object value){if(value instanceof Map<?,?> map)return "{"+map.entrySet().stream().map(e->json(e.getKey().toString())+":"+json(e.getValue())).reduce((a,b)->a+","+b).orElse("")+"}";if(value instanceof Collection<?> list)return "["+list.stream().map(IPhoneDuoAssets::json).reduce((a,b)->a+","+b).orElse("")+"]";if(value instanceof String text)return "\""+text.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\"";return String.valueOf(value);}
}
