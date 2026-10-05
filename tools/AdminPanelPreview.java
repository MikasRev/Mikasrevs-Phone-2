import java.nio.file.*;
import java.net.URLClassLoader;
import java.util.*;
import javax.tools.ToolProvider;

/**
 * Captures the real Admin screen render/input code using a small Java2D GUI adapter.
 * Run with Java 17: java tools/AdminPanelPreview.java
 * This is a layout/input preview, not a Minecraft or multiplayer integration test.
 */
public final class AdminPanelPreview {
    public static void main(String[] args) throws Exception {
        Path root=Path.of("").toAbsolutePath(), out=root.resolve("build/admin-panel-preview");
        Path sources=out.resolve("sources"), classes=out.resolve("classes");
        Files.createDirectories(classes);
        Map<String,String> stubs=new LinkedHashMap<>();
        stubs.put("com.google.gson.Gson", """
            package com.google.gson;
            public class Gson { public String toJson(Object object){return "{}";} }
            """);
        stubs.put("net.minecraft.network.chat.Component", """
            package net.minecraft.network.chat;
            public record Component(String value) { public static Component literal(String text){return new Component(text);} }
            """);
        stubs.put("org.lwjgl.glfw.GLFW", """
            package org.lwjgl.glfw;
            public class GLFW { public static final int GLFW_KEY_ESCAPE=256, GLFW_KEY_ENTER=257, GLFW_KEY_KP_ENTER=335, GLFW_KEY_TAB=258; }
            """);
        stubs.put("net.minecraft.client.gui.GuiGraphics", """
            package net.minecraft.client.gui;
            import java.awt.*;
            import java.awt.geom.*;
            import java.awt.image.BufferedImage;
            import java.util.ArrayDeque;
            import java.nio.file.Path;
            import javax.imageio.ImageIO;
            public class GuiGraphics {
                public final BufferedImage image;
                public final java.util.List<String> drawnTexts=new java.util.ArrayList<>();
                private final Graphics2D g;
                private final Pose pose=new Pose();
                public GuiGraphics(int width,int height) {
                    image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
                    g=image.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                }
                public void fill(int l,int t,int r,int b,int c){g.setColor(new Color(c,true));g.fillRect(l,t,r-l,b-t);}
                public void fillGradient(int l,int t,int r,int b,int a,int c){
                    g.setPaint(new GradientPaint(l,t,new Color(a,true),l,b,new Color(c,true)));g.fillRect(l,t,r-l,b-t);
                }
                public void round(int l,int t,int r,int b,int radius,int c){g.setColor(new Color(c,true));g.fillRoundRect(l,t,r-l,b-t,2*radius,2*radius);}
                public void circle(int x,int y,int radius,int c){g.setColor(new Color(c,true));g.fillOval(x-radius,y-radius,2*radius,2*radius);}
                public void text(String text,int x,int y,int height,int width,int color,boolean center){
                    drawnTexts.add(text);
                    String safe=text==null?"":text;
                    Font font=new Font("Consolas",Font.PLAIN,Math.max(1,height));
                    g.setFont(font);
                    int measured=g.getFontMetrics().stringWidth(safe);
                    if(measured>width){font=font.deriveFont(Math.max(1f,height*width/(float)measured));g.setFont(font);}
                    measured=g.getFontMetrics().stringWidth(safe);
                    g.setColor(new Color(color,true));
                    g.drawString(safe,center?x-measured/2:x,y+g.getFontMetrics().getAscent());
                }
                public Pose pose(){return pose;}
                public void flush(){}
                public void save(Path path)throws Exception{ImageIO.write(image,"png",path.toFile());g.dispose();}
                public final class Pose {
                    private final ArrayDeque<AffineTransform> stack=new ArrayDeque<>();
                    public void pushPose(){stack.push(g.getTransform());}
                    public void popPose(){g.setTransform(stack.pop());}
                    public void translate(double x,double y,double z){g.translate(x,y);}
                    public void scale(float x,float y,float z){g.scale(x,y);}
                }
            }
            """);
        stubs.put("net.minecraft.client.gui.components.EditBox", """
            package net.minecraft.client.gui.components;
            import net.minecraft.network.chat.Component;
            import net.minecraft.client.gui.GuiGraphics;
            public class EditBox {
                private String value="", hint=""; private boolean focus;
                public EditBox(Object font,int x,int y,int w,int h,Component title){}
                public void setMaxLength(int length){} public void setBordered(boolean bordered){}
                public void setTextColor(int color){} public void setHint(Component c){hint=c.value();}
                public void setValue(String text){value=text;} public String getValue(){return value;}
                public boolean isFocused(){return focus;} public void setFocused(boolean flag){focus=flag;}
                public void tick(){}
                public void render(GuiGraphics g,int x,int y,float p){g.text(value.isEmpty()?hint:value,0,0,9,178,value.isEmpty()?0xFF96A6BD:0xFFF2F5FC,false);}
                public boolean mouseClicked(double x,double y,int button){focus=x>=0&&x<=178&&y>=0&&y<=18;return focus;}
                public boolean mouseDragged(double x,double y,int b,double dx,double dy){return focus;}
                public boolean keyPressed(int k,int s,int m){return false;}
                public boolean charTyped(char c,int m){value+=c;return true;}
            }
            """);
        stubs.put("com.mikasrevs.newphone.client.gui.PhoneModernText", """
            package com.mikasrevs.newphone.client.gui;
            import net.minecraft.client.gui.GuiGraphics;
            public class PhoneModernText {
                public static void drawColoredFitted(GuiGraphics g,String t,int x,int y,int h,int w,int c){g.text(t,x,y,h,w,c,false);}
                public static void drawCenteredColoredFitted(GuiGraphics g,String t,int x,int y,int h,int w,int c){g.text(t,x,y,h,w,c,true);}
            }
            """);
        stubs.put("com.mikasrevs.newphone.client.settings.PhoneSettingsStore", """
            package com.mikasrevs.newphone.client.settings;
            public class PhoneSettingsStore {
                public static boolean english;
                public static String text(String tr,String en){return english?en:tr;}
            }
            """);
        stubs.put("com.mikasrevs.newphone.network.PhoneNetwork", """
            package com.mikasrevs.newphone.network;
            public class PhoneNetwork {
                public static int sent;
                public static void sendAction(String a,String t,String b){sent++;}
            }
            """);
        stubs.put("com.mikasrevs.newphone.client.zphone.PhoneNavigationState", """
            package com.mikasrevs.newphone.client.zphone;
            public class PhoneNavigationState { public static void goHome(Object minecraft){} }
            """);
        stubs.put("com.mikasrevs.newphone.client.moderation.PhoneModerationClient", """
            package com.mikasrevs.newphone.client.moderation;
            import com.mikasrevs.newphone.moderation.PhoneModerationState;
            public class PhoneModerationClient {
                public static final PhoneModerationState state=new PhoneModerationState();
                public static boolean canManage(){return state.canManage();}
                public static PhoneModerationState state(){return state;}
                public static PhoneModerationState response(){return null;}
            }
            """);
        stubs.put("com.mikasrevs.newphone.client.zphone.gui.AbstractZPhoneScreen", """
            package com.mikasrevs.newphone.client.zphone.gui;
            import net.minecraft.client.gui.GuiGraphics;
            import net.minecraft.network.chat.Component;
            public class AbstractZPhoneScreen {
                public static float scale=2;
                protected Object font=new Object(),minecraft=new Object();
                public AbstractZPhoneScreen(Component title){}
                protected void init(){}
                public void tick(){}
                public void render(GuiGraphics g,int mx,int my,float p){}
                public void renderBackground(GuiGraphics g){}
                protected int x(float x){return Math.round(x*scale);} protected int y(float y){return Math.round(y*scale);}
                protected int size(float s){return Math.max(1,Math.round(s*scale));}
                protected boolean inside(double x,double y,float l,float t,float r,float b){return x>=x(l)&&x<=x(r)&&y>=y(t)&&y<=y(b);}
                protected void drawRoundedRect(GuiGraphics g,int l,int t,int r,int b,int radius,int c){g.round(l,t,r,b,radius,c);}
                protected void fillCircle(GuiGraphics g,int x,int y,int r,int c){g.circle(x,y,r,c);}
                protected void drawStatusBar(GuiGraphics g){g.text("12:30",x(23),y(18),size(8),size(55),0xFFF2F5FC,false);g.text("100%",x(250),y(18),size(8),size(30),0xFFF2F5FC,false);}
                protected void drawHomeIndicator(GuiGraphics g){g.round(x(113),y(594),x(187),y(598),size(2),0xFFFFFFFF);}
                protected void drawFrame(GuiGraphics g){}
                protected void beginOverlayLayer(GuiGraphics g){g.flush();}
                public boolean mouseClicked(double x,double y,int b){return false;}
                public boolean keyPressed(int k,int s,int m){return false;}
                public boolean charTyped(char c,int m){return false;}
                public boolean mouseDragged(double x,double y,int b,double dx,double dy){return false;}
            }
            """);
        stubs.put("com.mikasrevs.newphone.client.zphone.gui.CaptureAdmin", """
            package com.mikasrevs.newphone.client.zphone.gui;
            import com.mikasrevs.newphone.client.moderation.*;
            import com.mikasrevs.newphone.client.settings.PhoneSettingsStore;
            import com.mikasrevs.newphone.moderation.*;
            import com.mikasrevs.newphone.network.PhoneNetwork;
            import net.minecraft.client.gui.GuiGraphics;
            import net.minecraft.client.gui.components.EditBox;
            import java.nio.file.Path;
            import java.util.*;
            public class CaptureAdmin {
                static Object field(Object target,String name)throws Exception{
                    var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
                }
                static PhoneAdminSession view(ZPhoneAdminScreen screen)throws Exception{return (PhoneAdminSession)field(screen,"view");}
                static void reply(PhoneAdminSession view)throws Exception{
                    var request=(PhoneModerationRequest)field(view,"pending");
                    if(request==null)return;
                    var response=new PhoneModerationState();response.operator=true;response.requestId=request.id;
                    response.players=view.players;response.selected=view.selected;response.total=129;response.online=23;response.restricted=7;
                    response.page=2;response.pages=17;view.accept(response);
                }
                static PhoneModerationState.Player player(String name,int index){
                    var p=new PhoneModerationState.Player();p.name=name;p.uuid=new UUID(0,index).toString();p.online=index%2==0;
                    p.operator=index==1;p.moderator=index==2;p.bans=index==4?Set.of("BLOCKGRAM","TINDER"):Set.of();return p;
                }
                static void require(boolean flag,String failure){if(!flag)throw new AssertionError(failure);}
                static void capture(ZPhoneAdminScreen screen,Path out,String name)throws Exception{
                    var g=new GuiGraphics(Math.round(300*AbstractZPhoneScreen.scale),Math.round(610*AbstractZPhoneScreen.scale));
                    screen.render(g,-1,-1,0);
                    if(name.startsWith("confirm"))require(g.drawnTexts.stream().noneMatch(t->java.util.Set.of("BlockPost","Lumi","Minder","Panel access","Panel yetkisi").contains(t)),"player page labels rendered underneath the confirmation");
                    g.save(out.resolve(name+".png"));
                }
                public static void run(Path out)throws Exception{
                    PhoneModerationClient.state.operator=true;
                    var screen=new ZPhoneAdminScreen();screen.init();var view=view(screen);
                    view.players=List.of(player("PlayerOne",1),player("CommunityMod",2),player("CreeperHunter",3),player("Builder_42",4),
                        player("SkyExplorer",5),player("DiamondMiner",6),player("RedstoneWizard",7),player("NightOwl",8));
                    reply(view);capture(screen,out,"list-tr");
                    screen.mouseClicked(50*2,179*2,0);screen.charTyped('T',0);
                    require(((EditBox)field(screen,"search")).getValue().equals("T"),"scaled search failed");
                    screen.init();require(((EditBox)field(screen,"search")).getValue().equals("T"),"resize erased search draft");
                    screen.mouseClicked(100*2,(224+7*37+15)*2,0);
                    require(view.selected.name.equals("NightOwl"),"eighth player hit area wrong");reply(view);
                    int before=PhoneNetwork.sent;
                    screen.mouseClicked(220*2,(237+2*67+10)*2,0);
                    require(field(screen,"confirmation")!=null&&PhoneNetwork.sent==before,"opening confirmation sent mutation");
                    screen.mouseClicked(85*2,385*2,0);
                    require(field(screen,"confirmation")==null&&PhoneNetwork.sent==before,"cancel sent mutation");
                    view.selected=player("Builder_42",4);view.fresh=true;capture(screen,out,"detail-tr");
                    screen.mouseClicked(220*2,247*2,0);capture(screen,out,"confirm-tr");
                    screen.mouseClicked(200*2,385*2,0);
                    require(PhoneNetwork.sent==before+1&&view.busy(),"apply did not send one mutation");
                    screen.mouseClicked(220*2,247*2,0);
                    require(PhoneNetwork.sent==before+1,"pending action allowed duplicate click");reply(view);
                    PhoneSettingsStore.english=true;PhoneModerationClient.state.operator=false;PhoneModerationClient.state.moderator=true;
                    view.selected=player("VeryLongPlayerName_123456789",3);view.fresh=true;capture(screen,out,"detail-moderator-en");
                    before=PhoneNetwork.sent;screen.mouseClicked(130*2,489*2,0);
                    require(field(screen,"confirmation")==null&&PhoneNetwork.sent==before,"moderator opened role control");
                    view.selected=player("CommunityMod",2);screen.mouseClicked(220*2,247*2,0);
                    require(field(screen,"confirmation")==null,"protected staff ban enabled");
                    for(float scale:new float[]{.5f,1f,1.5f}){
                        AbstractZPhoneScreen.scale=scale;screen.init();view.selected=null;reply(view);
                        screen.mouseClicked(100*scale,(224+7*37+15)*scale,0);
                        require(view.selected.name.equals("NightOwl"),"row click wrong at scale "+scale);reply(view);
                        capture(screen,out,"detail-scale-"+scale);
                    }
                    System.out.println("Admin UI capture/input checks passed: all eight rows, 0.5/1/1.5/2 scales, resize preservation, cancel/apply, pending duplicate clicks and staff protection.");
                }
            }
            """);
        List<String> compilerArgs=new ArrayList<>(List.of("-encoding","UTF-8","-d",classes.toString()));
        for(var entry:stubs.entrySet()){
            Path file=sources.resolve(entry.getKey().replace('.','/')+".java");Files.createDirectories(file.getParent());
            Files.writeString(file,entry.getValue());compilerArgs.add(file.toString());
        }
        for(String file:List.of(
                "com/mikasrevs/newphone/client/zphone/gui/ZPhoneAdminScreen.java",
                "com/mikasrevs/newphone/client/moderation/PhoneAdminSession.java",
                "com/mikasrevs/newphone/moderation/PhoneModerationState.java",
                "com/mikasrevs/newphone/moderation/PhoneModerationRequest.java"))
            compilerArgs.add(root.resolve("src/main/java").resolve(file).toString());
        if(ToolProvider.getSystemJavaCompiler().run(null,null,null,compilerArgs.toArray(String[]::new))!=0)
            throw new IllegalStateException("Preview compilation failed");
        try(var loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            loader.loadClass("com.mikasrevs.newphone.client.zphone.gui.CaptureAdmin").getMethod("run",Path.class).invoke(null,out);
        }
        System.out.println("Layout previews: "+out);
    }
}
