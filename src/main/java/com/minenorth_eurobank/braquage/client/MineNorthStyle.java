package com.minenorth_eurobank.braquage.client;

import com.minenorth_eurobank.EuroBank;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class MineNorthStyle {
    public static final int FRAME = 0xFF0E0E10;
    public static final int NAVY = 0xFF173B68;
    public static final int BLUE = 0xFF2B5C92;
    public static final int LIST = 0xFFF0F4F7;
    public static final int HOVER = 0xFFE3EDF5;
    public static final int CYAN = 0xFF20AAEB;
    public static final int DARK = 0xFF355C82;
    public static final int PINK = 0xFFB44C68;
    public static final int GREEN = 0xFF3C9B68;
    public static final int TEXT = 0xFF31485D;
    public static final int MUTED = 0xFF6D7D8C;
    public static final int WARN = 0xFFC58A18;
    public static final int ALERT = 0xFFC53D4F;
    public static final int WHITE = 0xFFFFFFFF;
    public static final ResourceLocation LOGO = new ResourceLocation(EuroBank.MODID, "textures/gui/logo.png");

    private MineNorthStyle() {}

    public static int lighten(int c) {
        int r=Math.min(255,((c>>16)&255)+25), g=Math.min(255,((c>>8)&255)+25), b=Math.min(255,(c&255)+25);
        return 0xFF000000|(r<<16)|(g<<8)|b;
    }
    public static Component bold(String s){ return Component.literal(s).withStyle(ChatFormatting.BOLD); }
    public static void panel(GuiGraphics g,int left,int top,int w,int h,String title,String subtitle){
        g.fill(left-4,top-4,left+w+4,top+h+4,FRAME);
        g.fill(left,top,left+w,top+h,0xFFF7F9FB);
        g.renderOutline(left,top,w,h,0xFFB8C5D0);
        g.fill(left,top,left+w,top+58,NAVY);
        RenderSystem.enableBlend();
        g.blit(LOGO,left+12,top+10,28,28,0,0,96,96,96,96);
        g.drawString(Minecraft.getInstance().font,bold(title),left+50,top+10,WHITE,false);
        if(subtitle!=null&&!subtitle.isBlank()) g.drawString(Minecraft.getInstance().font,subtitle,left+50,top+30,0xFFD9E7F7,false);
    }
    public static void card(GuiGraphics g,int x,int y,int w,int h,boolean hover,int accent){
        g.fill(x+3,y+4,x+w+3,y+h+4,0x18000000);
        g.fill(x,y,x+w,y+h,hover?HOVER:0xFFFFFFFF);
        g.renderOutline(x,y,w,h,0xFFD0D9E1);
        g.fill(x,y,x+4,y+h,accent);
    }
    public static void button(GuiGraphics g,int x,int y,int w,int h,String label,int color,boolean hover,boolean active){
        int bg=!active?0xFFD1D9E0:(hover?lighten(color):color);
        g.fill(x+2,y+3,x+w+2,y+h+3,0x22000000);
        g.fill(x,y,x+w,y+h,bg);
        g.renderOutline(x,y,w,h,0xFFB7C4CF);
        Font f=Minecraft.getInstance().font; int tx=x+w/2-f.width(label)/2; g.drawString(f,label,tx,y+(h-8)/2,active?WHITE:0xFF7C8995,false);
    }
    public static void item(GuiGraphics g,ItemStack st,int x,int y,float scale){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.renderItem(st,0,0);g.pose().popPose();
    }
    public static String euros(long cents){
        long a=Math.abs(cents); return (cents<0?"-":"")+(a/100)+(a%100==0?"":","+String.format("%02d",a%100))+" €";
    }
}
