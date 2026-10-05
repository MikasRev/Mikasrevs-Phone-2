import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Editable procedural accessory designs. Run with Java 17+: java tools/PhoneCaseDesigner.java */
public class PhoneCaseDesigner {
    static final Path ROOT=Path.of("").toAbsolutePath(), ASSET=ROOT.resolve("src/main/resources/assets/newphone");
    static final Path ART=ROOT.resolve("art/phone_cases/v2");
    record Style(String id,String title,int base,int edge,int accent,boolean bunny,boolean armor) {}
    static final Style[] STYLES={
        new Style("bunny_blush","BUNNY / ROSE",0xE9ABBE,0xAF667D,0xFFF0DA,true,false),
        new Style("carbon_guard","CARBON / GUARD",0x242B36,0x10151E,0xE56A4D,false,true),
        new Style("forest_leather","FOREST / ATELIER",0x234D40,0x112E28,0xD8B67A,false,false),
        new Style("midnight_cyber","MIDNIGHT / CIRCUIT",0x142C44,0x0B172A,0x56E1D8,false,true),
        new Style("bunny_block","BUNNY / PIXEL",0xADCCE1,0x526D9C,0xDCACE8,true,false)
    };
    static final String[] MODELS={"classic","model_01","model_02","model_03","model_04","model_05"};
    static final double[] RADII={.33,.33,.44,.286,.528,.374};
    static final int[] METALS={0x687484,0xBE9657,0x89B69F,0xCED4D8,0xD98556,0x8F9C70};
    static final Map<String,BufferedImage> textures=new HashMap<>();
    static final List<String> report=new ArrayList<>();
    public static void main(String[] args)throws Exception {
        Files.createDirectories(ART);
        for(Style s:STYLES)material(s);
        for(int mi=0;mi<MODELS.length;mi++) {
            String model=MODELS[mi];
            Path folder=ASSET.resolve(model.equals("classic")?"models/item":"models/phone/"+model);
            String stem=model.equals("classic")?"mikasrevs_phone_2":"phone";
            String base=Files.readString(folder.resolve(stem+".obj"));
            Map<String,Bounds> bounds=bounds(base);
            for(Style s:STYLES){
                String name=stem+"_case_"+s.id;
                Obj obj=new Obj(base); geometry(obj,bounds,s,RADII[mi],mi);
                Files.writeString(folder.resolve(name+".obj"),obj.out);
                Path json=folder.resolve((model.equals("classic")?"z_phone":"phone")+"_case_"+s.id+".json");
                String location="newphone:"+(model.equals("classic")?"models/item/":"models/phone/"+model+"/")+name+".obj";
                String content=Files.readString(json).replaceAll("\"model\"\\s*:\\s*\"[^\"]+\\.obj\"","\"model\": \""+location+"\"");
                Files.writeString(json,content);
                gui(s,model,mi);
                renderObj(obj.out.toString(),s,model,ART.resolve(model+"_"+s.id+"_3d.png"));
                report.add(model+" / "+s.id+": "+obj.added+" accessory quads; camera="+camera(bounds));
            }
        }
        contactSheet();
        Files.writeString(ART.resolve("generation.txt"),String.join("\n",report)+"\n");
        System.out.println("Generated 30 fitted OBJ accessories, 30 GUI frames, 5 material/icon families, and preview sheet.");
    }
    static Color color(int rgb){return new Color(rgb);}
    static Color alpha(int rgb,int a){return new Color((rgb>>16)&255,(rgb>>8)&255,rgb&255,a);}
    static Graphics2D graphics(BufferedImage im){var g=im.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);return g;}
    static BufferedImage image(int w,int h){return new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);}
    static void save(BufferedImage im,Path p)throws IOException{Files.createDirectories(p.getParent());ImageIO.write(im,"png",p.toFile());}
    static Shape rounded(double x,double y,double w,double h,double r){return new RoundRectangle2D.Double(x,y,w,h,r*2,r*2);}
    static void stroke(Graphics2D g,Shape p,Color c,float width){g.setColor(c);g.setStroke(new BasicStroke(width,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(p);}
    static void material(Style s)throws Exception {
        var im=image(256,256);var g=graphics(im);g.setColor(color(s.base));g.fillRect(0,0,256,256);
        Random random=new Random(s.id.hashCode());
        for(int i=0;i<9000;i++){int v=random.nextBoolean()?0xFFFFFF:0;g.setColor(alpha(v,s.id.equals("forest_leather")?15:6));g.fillRect(random.nextInt(256),random.nextInt(256),1,1);}
        if(s.id.equals("carbon_guard")){
            for(int y=0;y<256;y+=8)for(int x=0;x<256;x+=8){g.setColor(alpha(((x+y)/8)%2==0?0xFFFFFF:0,24));g.fillRoundRect(x,y,7,4,2,2);}
        }else if(s.id.equals("bunny_block")){
            for(int y=0;y<256;y+=32)for(int x=0;x<256;x+=32){g.setColor(alpha(((x+y)/32)%2==0?0xFFFFFF:0x526D9C,20));g.fillRect(x,y,32,32);}
        }else if(s.id.equals("midnight_cyber")){
            g.setStroke(new BasicStroke(1));g.setColor(alpha(s.accent,24));
            for(int x=-256;x<512;x+=32)g.drawLine(x,0,x+256,256);
        }
        g.dispose();textures.put(s.id,im);save(im,ART.resolve(s.id+"_material.png"));save(im,ASSET.resolve("textures/item/z_phone_case_"+s.id+".png"));
        var accent=image(32,32);g=graphics(accent);g.setPaint(new GradientPaint(0,0,color(s.accent),32,32,color(s.accent).darker()));g.fillRect(0,0,32,32);g.dispose();save(accent,ASSET.resolve("textures/item/z_phone_case_"+s.id+"_accent.png"));
        var badge=image(128,128);g=graphics(badge);g.setColor(color(s.accent));
        if(s.bunny){g.fill(rounded(30,7,23,58,s.id.equals("bunny_block")?3:11));g.fill(rounded(75,7,23,58,s.id.equals("bunny_block")?3:11));g.fill(rounded(22,43,84,69,s.id.equals("bunny_block")?10:32));g.setColor(color(s.edge));g.fillOval(42,69,7,9);g.fillOval(79,69,7,9);g.fillOval(60,81,8,6);}
        else if(s.id.equals("forest_leather")){Path2D leaf=new Path2D.Double();leaf.moveTo(28,99);leaf.curveTo(25,43,69,16,102,20);leaf.curveTo(106,64,77,102,28,99);g.fill(leaf);stroke(g,new Line2D.Double(39,90,90,34),color(s.base),5);}
        else {Path2D crest=new Path2D.Double();crest.moveTo(64,12);crest.lineTo(108,35);crest.lineTo(98,88);crest.lineTo(64,115);crest.lineTo(30,88);crest.lineTo(20,35);crest.closePath();stroke(g,crest,color(s.accent),7);stroke(g,new Line2D.Double(45,82,68,42),color(s.accent),7);stroke(g,new Line2D.Double(68,42,83,66),color(s.accent),7);}
        g.dispose();save(badge,ASSET.resolve("textures/item/z_phone_case_"+s.id+"_logo.png"));textures.put(s.id+"_logo",badge);
        var icon=image(128,128);g=graphics(icon);g.translate(31,14);g.scale(.08,.063);drawBack(g,s,0);g.dispose();save(icon,ASSET.resolve("textures/item/"+s.id+"_phone_case.png"));
    }
    static Shape ear(double x,boolean block){
        if(!block)return rounded(x,28,110,197,53);
        Path2D p=new Path2D.Double();p.moveTo(x,225);p.lineTo(x,65);p.lineTo(x+22,65);p.lineTo(x+22,25);p.lineTo(x+88,25);p.lineTo(x+88,65);p.lineTo(x+110,65);p.lineTo(x+110,225);p.closePath();return p;
    }
    static void gui(Style s,String model,int index)throws Exception{
        var im=image(900,1760);var g=graphics(im);double r=70+RADII[index]*88;
        if(s.bunny)for(int x:new int[]{240,550}){Shape ear=ear(x,s.id.equals("bunny_block"));g.setPaint(new GradientPaint(x,0,color(s.base).brighter(),x+110,0,color(s.base)));g.fill(ear);stroke(g,ear,color(s.edge),6);g.setColor(color(s.accent));g.fill(rounded(x+32,57,46,119,22));}
        Shape outer=rounded(43,172,814,1579,r),inner=rounded(85,225,730,1465,Math.max(30,r-23));
        Area ring=new Area(outer);ring.subtract(new Area(inner));g.setClip(ring);
        g.setPaint(new TexturePaint(textures.get(s.id),new Rectangle(0,0,256,256)));g.fillRect(0,160,900,1600);
        g.setPaint(new GradientPaint(40,0,alpha(0xFFFFFF,90),110,0,alpha(0xFFFFFF,0)));g.fillRect(40,160,70,1600);
        g.setPaint(new GradientPaint(790,0,alpha(0,0),865,0,alpha(0,85)));g.fillRect(790,160,75,1600);
        g.setClip(null);stroke(g,outer,color(s.edge),6);stroke(g,rounded(50,179,800,1565,r-5),alpha(0xFFFFFF,110),2);
        stroke(g,inner,color(s.edge).darker(),6);
        if(s.id.equals("forest_leather")){g.setClip(ring);g.setStroke(new BasicStroke(3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,10,new float[]{7,8},0));g.setColor(color(s.accent));g.draw(rounded(62,192,776,1538,r-16));g.setClip(null);}
        if(s.armor){
            for(int x:new int[]{49,807})for(int y:new int[]{198,1635}){
                g.setColor(color(s.edge));g.fill(rounded(x,y,44,70,12));stroke(g,new Line2D.Double(x+13,y+15,x+13,y+53),color(s.accent),5);
                g.setColor(color(METALS[index]));g.fillOval(x+23,y+26,7,7);
            }
            for(int x:new int[]{67,833}){stroke(g,new Line2D.Double(x,390,x,640),color(s.accent),4);stroke(g,new Line2D.Double(x,1310,x,1500),color(s.accent),4);}
        }
        if(s.id.equals("bunny_block")){g.setColor(color(s.accent));for(int x:new int[]{58,824})for(int y:new int[]{1330,1360,1420})g.fillRect(x,y,17,17);}
        if(s.id.equals("bunny_blush")){g.setColor(color(s.accent));for(int x:new int[]{69,831})for(int y:new int[]{1460,1490,1520})g.fillOval(x-4,y,8,8);}
        // Keep camera/earpiece visible and give each chassis its original metal highlights.
        // The rounded inner aperture is already transparent. Clearing the rectangular
        // app bounds here would erase its four corner masks and expose square wallpaper.
        g.setComposite(AlphaComposite.Clear);g.fill(rounded(414,191,72,37,18));g.setComposite(AlphaComposite.SrcOver);
        for(int y:(index==0?new int[]{505,625,870}:new int[]{422,549,715})){
            g.setColor(color(s.edge));g.fill(rounded(839,y,20,83,8));g.setColor(color(METALS[index]));g.fill(rounded(852,y+12,5,58,2));
        }
        g.dispose();save(im,ASSET.resolve("textures/gui/phone/cases/"+model+"/"+s.id+".png"));
        if(index==0)save(im,ASSET.resolve("textures/gui/phone/cases/"+s.id+".png"));
    }
    static void drawBack(Graphics2D g,Style s,int model){
        if(s.bunny)for(int x:new int[]{240,550}){g.setColor(color(s.base));g.fill(ear(x,s.id.equals("bunny_block")));stroke(g,ear(x,s.id.equals("bunny_block")),color(s.edge),7);g.setColor(color(s.accent));g.fill(rounded(x+32,57,46,119,22));}
        Shape body=rounded(43,172,814,1579,105);g.setPaint(new TexturePaint(textures.get(s.id),new Rectangle(0,0,256,256)));g.fill(body);stroke(g,body,color(s.edge),12);stroke(g,rounded(58,187,784,1549,90),alpha(0xFFFFFF,100),4);
        if(s.id.equals("forest_leather")){g.setStroke(new BasicStroke(5,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,10,new float[]{13,12},0));g.setColor(color(s.accent));g.draw(rounded(82,211,736,1501,66));}
        if(s.armor){for(int x:new int[]{78,801}){stroke(g,new Line2D.Double(x,1100,x,1580),color(s.accent),9);}for(int i=0;i<5;i++)stroke(g,new Line2D.Double(125,1360+i*45,295,1360+i*45),color(s.edge),10);}
        g.drawImage(textures.get(s.id+"_logo"),345,1070,210,210,null);
        g.setColor(color(s.edge));g.fill(rounded(108,237,285,418,62));stroke(g,rounded(108,237,285,418,62),color(s.accent),7);
        for(int y:new int[]{330,475,580}){g.setColor(new Color(0x09111D));g.fillOval(145,y-55,110,110);stroke(g,new Ellipse2D.Double(145,y-55,110,110),color(METALS[model]),9);g.setColor(new Color(0x344B67));g.fillOval(174,y-26,53,53);g.setColor(new Color(0x90BACC));g.fillOval(179,y-20,15,15);}
    }
    static void contactSheet()throws Exception{
        var sheet=image(1600,1100);var g=graphics(sheet);g.setColor(new Color(0x101820));g.fillRect(0,0,1600,1100);g.setColor(new Color(0xF0ECE4));g.setFont(new Font("SansSerif",Font.BOLD,30));g.drawString("MRPHONE  /  CASE COLLECTION 02",55,65);g.setFont(new Font("SansSerif",Font.PLAIN,15));g.setColor(new Color(0x96A9B7));g.drawString("Five original accessories  /  Six fitted chassis  /  Matching in-game frames",56,95);
        for(int i=0;i<5;i++){Style s=STYLES[i];int x=48+i*310;g.setColor(new Color(0x19252F));g.fillRoundRect(x,127,287,879,20,20);
            var actual=ImageIO.read(ART.resolve("model_0"+(i+1)+"_"+s.id+"_3d.png").toFile());g.drawImage(actual,x+9,129,269,493,null);
            var front=ImageIO.read(ASSET.resolve("textures/gui/phone/cases/model_0"+(i+1)+"/"+s.id+".png").toFile());g.drawImage(front,x+74,630,139,272,null);g.setColor(new Color(0xEEF1F2));g.setFont(new Font("SansSerif",Font.BOLD,17));g.drawString(s.title,x+20,945);g.setFont(new Font("SansSerif",Font.PLAIN,13));g.setColor(new Color(0x9BAFBD));g.drawString("BACK DESIGN   +   GUI FRAME",x+20,974);
        }
        g.setFont(new Font("SansSerif",Font.PLAIN,14));g.setColor(new Color(0x8298AA));g.drawString("Exported OBJ geometry + matching GUI overlays. Offline asset renders; not an in-game screenshot.",55,1053);g.dispose();save(sheet,ART.resolve("collection-preview.png"));
    }
    static class Bounds {
        double x=1e9,y=1e9,z=1e9,X=-1e9,Y=-1e9,Z=-1e9;
        void add(double a,double b,double c){x=Math.min(x,a);y=Math.min(y,b);z=Math.min(z,c);X=Math.max(X,a);Y=Math.max(Y,b);Z=Math.max(Z,c);}
        void add(Bounds b){add(b.x,b.y,b.z);add(b.X,b.Y,b.Z);}
        public String toString(){return String.format(Locale.ROOT,"[%.3f,%.3f]-[%.3f,%.3f]",x,y,X,Y);}
    }
    static Map<String,Bounds> bounds(String obj){Map<String,Bounds> map=new HashMap<>();String group="";for(String line:obj.split("\n")){if(line.startsWith("g "))group=line.substring(2).trim();else if(line.startsWith("v ")){String[] p=line.trim().split("\\s+");map.computeIfAbsent(group,k->new Bounds()).add(Double.parseDouble(p[1])*16,Double.parseDouble(p[2])*16,Double.parseDouble(p[3])*16);}}return map;}
    static Bounds camera(Map<String,Bounds> b){var c=new Bounds();b.forEach((name,box)->{if(name.startsWith("Camera_")||name.startsWith("Flash")||name.equals("Focus_Sensor"))c.add(box);});c.x-=.105;c.X+=.105;c.y-=.105;c.Y+=.105;return c;}
    static void geometry(Obj m,Map<String,Bounds> map,Style s,double radius,int model){
        Bounds b=map.get("Phone_Body"),c=camera(map);
        List<Bounds> buttons=List.of(map.get("Power_Button"),map.get("Volume_Buttons"));
        double left=b.x-.105,right=b.X+.105,low=b.y-.105,high=b.Y+.105,back=b.Z+.135,front=b.z-.035;
        m.group("Case_V2_FittedRail","case");
        double[][] outside=outline(left,low,right,high,radius+.105,8),inside=outline(b.x-.006,b.y-.006,b.X+.006,b.Y+.006,radius,8);
        for(int i=0;i<outside.length;i++){
            int j=(i+1)%outside.length;double cx=(outside[i][0]+outside[j][0])/2,cy=(outside[i][1]+outside[j][1])/2;
            double[][] poly={outside[i],outside[j],inside[j],inside[i]};
            // The straight side is one long segment. Cut local button windows
            // through its depth, preserving the front and rear edges of the rail.
            if(cx<b.x+.08&&Math.abs(outside[i][0]-outside[j][0])<1e-7){
                double y0=Math.min(outside[i][1],outside[j][1]),y1=Math.max(outside[i][1],outside[j][1]);
                TreeSet<Double> cuts=new TreeSet<>(List.of(y0,y1));
                for(Bounds button:buttons)for(double y:new double[]{button.y-.11,button.Y+.11})if(y>y0&&y<y1)cuts.add(y);
                var levels=new ArrayList<>(cuts);
                for(int k=0;k<levels.size()-1;k++){
                    double start=levels.get(k),end=levels.get(k+1),middle=(start+end)/2;
                    double[][] strip={{outside[i][0],start},{inside[i][0],start},{inside[i][0],end},{outside[i][0],end}};
                    Bounds opening=null;
                    for(Bounds button:buttons)if(middle>button.y-.11&&middle<button.Y+.11)opening=button;
                    if(opening==null)m.prism(strip,front,back);
                    else {m.prism(strip,front,opening.z-.025);m.prism(strip,opening.Z+.025,back);}
                }
                continue;
            }
            // Split lower rail in depth, leaving the charging connector and speakers open.
            if(cy<b.y+.06&&cx>b.x+.45&&cx<b.X-.45){m.prism(poly,front,front+.065);m.prism(poly,back-.07,back);}
            else m.prism(poly,front,back);
        }
        m.group("Case_V2_CameraCutoutBack","case");
        double cameraRadius=Math.min(new double[]{.35,.382,.611,.567,.30,(c.X-c.x)/2}[model],Math.min(c.X-c.x,c.Y-c.y)/2);
        double[][] aperture=outline(c.x,c.y,c.X,c.Y,cameraRadius,8);
        TreeSet<Double> rows=new TreeSet<>();rows.add(low);rows.add(high);for(int i=1;i<36;i++)rows.add(low+(high-low)*i/36);
        // Quantize row boundaries to avoid near-zero polygons at symmetric arc samples.
        for(double[] p:aperture)if(p[1]>low&&p[1]<high)rows.add(Math.rint(p[1]*1e8)/1e8);
        var yy=new ArrayList<>(rows);
        for(int i=0;i<yy.size()-1;i++){
            double y0=yy.get(i),y1=yy.get(i+1),mid=(y0+y1)/2;
            double x0=edge(left,low,high,radius+.105,y0),x1=edge(left,low,high,radius+.105,y1),X0=left+right-x0,X1=left+right-x1;
            if(mid>c.y&&mid<c.Y){
                double[] cut0=span(aperture,y0),cut1=span(aperture,y1);
                if(Math.min(cut0[0],cut1[0])>Math.max(x0,x1))m.prism(new double[][]{{x0,y0},{cut0[0],y0},{cut1[0],y1},{x1,y1}},b.Z+.04,back);
                if(Math.max(cut0[1],cut1[1])<Math.min(X0,X1))m.prism(new double[][]{{cut0[1],y0},{X0,y0},{X1,y1},{cut1[1],y1}},b.Z+.04,back);
            }else m.prism(new double[][]{{x0,y0},{X0,y0},{X1,y1},{x1,y1}},b.Z+.04,back);
        }
        // Raised camera guard follows this chassis' actual camera envelope.
        m.group("Case_V2_CameraGuard","case_accent");double lip=.035,guardZ=Math.max(back+.015,c.Z+.02);
        double[][] guard=outline(c.x-lip,c.y-lip,c.X+lip,c.Y+lip,cameraRadius+lip,8);
        for(int i=0;i<aperture.length;i++){int j=(i+1)%aperture.length;m.prism(new double[][]{guard[i],guard[j],aperture[j],aperture[i]},back,guardZ);}
        // Signature badge is below every supported camera layout.
        m.group("Case_V2_Signature","case_logo");m.badge(4.42,11.65,5.58,12.81,back+.007);
        if(s.bunny){
            for(double x:new double[]{3.65,6.35}){
                m.group("Case_V2_"+s.id+"_Ear","case");double top=high+.88;
                double[][] ear=s.id.equals("bunny_block")?new double[][]{{x-.31,high-.04},{x+.31,high-.04},{x+.31,top-.19},{x+.18,top-.19},{x+.18,top},{x-.18,top},{x-.18,top-.19},{x-.31,top-.19}}:outline(x-.31,high-.04,x+.31,top,.28,6);
                m.prism(ear,front+.035,back-.015);
                m.group("Case_V2_EarInsert","case_accent");m.prism(outline(x-.12,high+.12,x+.12,top-.13,.115,4),front+.024,front+.034);
            }
        }
        if(s.armor){
            for(double x:new double[]{left+.17,right-.17})for(double y:new double[]{low+.17,high-.17}){
                m.group("Case_V2_ImpactCorner","case");m.prism(outline(x-.19,y-.215,x+.19,y+.215,.11,3),front-.018,back+.045);
                m.group("Case_V2_CornerInlay","case_accent");m.box(x-.035,y-.16,back+.046,x+.035,y+.16,back+.058);
            }
            for(int i=0;i<5;i++){m.group("Case_V2_GripRib","case");m.box(left+.25,10.1+i*.20,back+.005,left+1.05,10.16+i*.20,back+.045);}
            m.group("Case_V2_TechnicalInlay","case_accent");m.box(right-.26,10.2,back+.006,right-.22,13.7,back+.02);
        }
        if(s.id.equals("forest_leather")){
            m.group("Case_V2_SaddleStitch","case_accent");
            for(double y=9.55;y<13.9;y+=.23)for(double x:new double[]{left+.18,right-.18})m.box(x-.017,y,back+.002,x+.017,y+.105,back+.012);
            for(double x=3.3;x<6.8;x+=.23)m.box(x,low+.17,back+.002,x+.10,low+.202,back+.012);
        }
        if(s.id.equals("bunny_block")){
            m.group("Case_V2_PixelConfetti","case_accent");for(int i=0;i<4;i++){double x=right-.43-(i%2)*.19,y=10.1+i*.24;m.box(x,y,back+.003,x+.14,y+.14,back+.022);}
        }
    }
    static double[] span(double[][] p,double y){
        double min=1e9,max=-1e9;
        for(int i=0;i<p.length;i++){double[] a=p[i],b=p[(i+1)%p.length];if(Math.abs(b[1]-a[1])<1e-9)continue;
            if(y<Math.min(a[1],b[1])-1e-7||y>Math.max(a[1],b[1])+1e-7)continue;
            double t=Math.max(0,Math.min(1,(y-a[1])/(b[1]-a[1]))),x=a[0]+t*(b[0]-a[0]);min=Math.min(min,x);max=Math.max(max,x);
        }
        if(min>max)throw new IllegalArgumentException("Missing camera scanline at "+y);
        return new double[]{min,max};
    }
    static double edge(double left,double low,double high,double r,double y){double d=y<low+r?low+r-y:y>high-r?y-(high-r):0;return left+r-Math.sqrt(Math.max(0,r*r-d*d));}
    static double[][] outline(double x,double y,double X,double Y,double r,int steps){
        List<double[]> p=new ArrayList<>();double[][] corners={{X-r,Y-r,0},{x+r,Y-r,90},{x+r,y+r,180},{X-r,y+r,270}};
        for(double[] c:corners)for(int i=0;i<=steps;i++){double a=Math.toRadians(c[2]+90.0*i/steps);p.add(new double[]{c[0]+r*Math.cos(a),c[1]+r*Math.sin(a)});}return p.toArray(double[][]::new);
    }
    static class Obj {
        StringBuilder out;int vi,ti,ni,added,groups;
        Obj(String base){out=new StringBuilder(base);if(!base.endsWith("\n"))out.append('\n');for(String l:base.split("\n")){if(l.startsWith("v "))vi++;if(l.startsWith("vt "))ti++;if(l.startsWith("vn "))ni++;}}
        void group(String name,String mat){out.append("g ").append(name).append('_').append(++groups).append("\nusemtl ").append(mat).append('\n');}
        void quad(double[]... v){
            double ax=v[1][0]-v[0][0],ay=v[1][1]-v[0][1],az=v[1][2]-v[0][2],bx=v[2][0]-v[0][0],by=v[2][1]-v[0][1],bz=v[2][2]-v[0][2];
            double nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx,len=Math.sqrt(nx*nx+ny*ny+nz*nz);if(len<1e-10)return;
            for(double[] p:v){out.append(String.format(Locale.ROOT,"v %.8f %.8f %.8f\n",p[0]/16,p[1]/16,p[2]/16));out.append(String.format(Locale.ROOT,"vt %.8f %.8f\n",Math.max(.001,Math.min(.999,(p[0]-2.7)/4.6)),Math.max(.001,Math.min(.999,(p[1]-9)/9.2))));}
            out.append(String.format(Locale.ROOT,"vn %.8f %.8f %.8f\n",nx/len,ny/len,nz/len)).append("f");
            for(int i=0;i<v.length;i++)out.append(' ').append(vi+i+1).append('/').append(ti+i+1).append('/').append(ni+1);
            out.append('\n');vi+=v.length;ti+=v.length;ni++;added++;
        }
        void badge(double x,double y,double X,double Y,double z){
            // Full 0..1 decal UVs, outward +Z.
            int first=out.length();quad(new double[]{x,y,z},new double[]{X,y,z},new double[]{X,Y,z},new double[]{x,Y,z});
            String[] uv={"vt 0 0","vt 1 0","vt 1 1","vt 0 1"};int i=0;StringBuilder section=new StringBuilder();for(String line:out.substring(first).split("\n"))section.append(line.startsWith("vt ")?uv[i++]:line).append('\n');out.setLength(first);out.append(section);
        }
        void box(double x,double y,double z,double X,double Y,double Z){prism(new double[][]{{x,y},{X,y},{X,Y},{x,Y}},z,Z);}
        void prism(double[][] p,double z,double Z){
            var clean=new ArrayList<double[]>();
            for(double[] point:p)if(clean.isEmpty()||Math.hypot(point[0]-clean.get(clean.size()-1)[0],point[1]-clean.get(clean.size()-1)[1])>1e-7)clean.add(point);
            if(clean.size()>1&&Math.hypot(clean.get(0)[0]-clean.get(clean.size()-1)[0],clean.get(0)[1]-clean.get(clean.size()-1)[1])<1e-7)clean.remove(clean.size()-1);
            if(clean.size()<3)return;
            p=clean.toArray(double[][]::new);
            // Ear clipping keeps the concave voxel ears inside their actual silhouette.
            for(int[] tri:triangulate(p)){
                quad(new double[]{p[tri[0]][0],p[tri[0]][1],Z},new double[]{p[tri[1]][0],p[tri[1]][1],Z},new double[]{p[tri[2]][0],p[tri[2]][1],Z});
                quad(new double[]{p[tri[0]][0],p[tri[0]][1],z},new double[]{p[tri[2]][0],p[tri[2]][1],z},new double[]{p[tri[1]][0],p[tri[1]][1],z});
            }
            for(int i=0;i<p.length;i++){int j=(i+1)%p.length;quad(new double[]{p[i][0],p[i][1],z},new double[]{p[j][0],p[j][1],z},new double[]{p[j][0],p[j][1],Z},new double[]{p[i][0],p[i][1],Z});}
        }
    }

    static double cross(double[] a,double[] b,double[] c){return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);}
    static List<int[]> triangulate(double[][] p){
        List<Integer> pending=new ArrayList<>();for(int i=0;i<p.length;i++)pending.add(i);
        List<int[]> result=new ArrayList<>();
        while(pending.size()>3){boolean found=false;
            for(int i=0;i<pending.size();i++){
                int a=pending.get((i+pending.size()-1)%pending.size()),b=pending.get(i),c=pending.get((i+1)%pending.size());
                if(cross(p[a],p[b],p[c])<=1e-12)continue;
                boolean contains=false;
                for(int t:pending)if(t!=a&&t!=b&&t!=c&&cross(p[a],p[b],p[t])>=-1e-12&&cross(p[b],p[c],p[t])>=-1e-12&&cross(p[c],p[a],p[t])>=-1e-12){contains=true;break;}
                if(!contains){result.add(new int[]{a,b,c});pending.remove(i);found=true;break;}
            }
            if(!found)throw new IllegalArgumentException("Cannot triangulate accessory profile: "+Arrays.deepToString(p));
        }
        result.add(new int[]{pending.get(0),pending.get(1),pending.get(2)});return result;
    }

    /** Orthographic textured OBJ proof, using the exported geometry, normals and UVs. */
    static void renderObj(String obj,Style style,String model,Path path)throws Exception {
        int width=360,height=660;var im=image(width,height);double[] depth=new double[width*height];Arrays.fill(depth,-1e10);
        var atlas=ImageIO.read(ASSET.resolve(model.equals("classic")?"textures/item/mikasrevs_phone_2_atlas.png":"textures/phone/"+model+"/atlas.png").toFile());
        var accent=ImageIO.read(ASSET.resolve("textures/item/z_phone_case_"+style.id+"_accent.png").toFile());
        List<double[]> vertices=new ArrayList<>(),uvs=new ArrayList<>(),normals=new ArrayList<>();String mat="atlas";
        double angle=.32,co=Math.cos(angle),si=Math.sin(angle);
        for(String line:obj.split("\n")){
            String[] a=line.trim().split("\\s+");
            if(a[0].equals("v")){double x=Double.parseDouble(a[1])*16-5,y=Double.parseDouble(a[2])*16-14.2,z=Double.parseDouble(a[3])*16-.89;vertices.add(new double[]{180+(x*co+z*si)*56,335-y*56,(z*co-x*si)});}
            else if(a[0].equals("vt"))uvs.add(new double[]{Double.parseDouble(a[1]),Double.parseDouble(a[2])});
            else if(a[0].equals("vn"))normals.add(new double[]{Double.parseDouble(a[1]),Double.parseDouble(a[2]),Double.parseDouble(a[3])});
            else if(a[0].equals("usemtl"))mat=a[1];
            else if(a[0].equals("f")){
                int[][] f=new int[a.length-1][3];for(int i=1;i<a.length;i++){String[] ids=a[i].split("/");for(int j=0;j<3;j++)f[i-1][j]=Integer.parseInt(ids[j])-1;}
                double[] n=normals.get(f[0][2]);if(n[2]*co-n[0]*si<-.001)continue;
                double shade=.65+.35*Math.max(0,(-.25*n[0]+.45*n[1]+.86*n[2]));
                BufferedImage tex=switch(mat){case "case"->textures.get(style.id);case "case_logo"->textures.get(style.id+"_logo");case "case_accent"->accent;default->atlas;};
                for(int k=1;k<f.length-1;k++){
                    double[] p=vertices.get(f[0][0]),q=vertices.get(f[k][0]),r=vertices.get(f[k+1][0]);double[] u=uvs.get(f[0][1]),v=uvs.get(f[k][1]),w=uvs.get(f[k+1][1]);
                    double area=cross(p,q,r);if(Math.abs(area)<1e-8)continue;
                    int x0=Math.max(0,(int)Math.floor(Math.min(p[0],Math.min(q[0],r[0])))),x1=Math.min(width-1,(int)Math.ceil(Math.max(p[0],Math.max(q[0],r[0]))));
                    int y0=Math.max(0,(int)Math.floor(Math.min(p[1],Math.min(q[1],r[1])))),y1=Math.min(height-1,(int)Math.ceil(Math.max(p[1],Math.max(q[1],r[1]))));
                    for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){
                        double[] at={x+.5,y+.5};double b0=cross(q,r,at)/area,b1=cross(r,p,at)/area,b2=1-b0-b1;if(b0<0||b1<0||b2<0)continue;
                        double z=b0*p[2]+b1*q[2]+b2*r[2];int pixel=y*width+x;if(z<depth[pixel])continue;
                        double U=b0*u[0]+b1*v[0]+b2*w[0],V=1-(b0*u[1]+b1*v[1]+b2*w[1]);
                        int rgb=tex.getRGB(Math.max(0,Math.min(tex.getWidth()-1,(int)(U*tex.getWidth()))),Math.max(0,Math.min(tex.getHeight()-1,(int)(V*tex.getHeight()))));
                        if((rgb>>>24)<128)continue;
                        im.setRGB(x,y,0xFF000000|((int)(((rgb>>16)&255)*shade)<<16)|((int)(((rgb>>8)&255)*shade)<<8)|(int)((rgb&255)*shade));depth[pixel]=z;
                    }
                }
            }
        }
        save(im,path);
    }
}
