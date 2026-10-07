package com.minenorth_eurobank.braquage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class MineNorthButton extends AbstractButton {
    private final Runnable action; private final int color;
    public MineNorthButton(int x,int y,int w,int h,Component label,int color,Runnable action){super(x,y,w,h,label);this.color=color;this.action=action;}
    public MineNorthButton enabled(boolean on){this.active=on;return this;}
    @Override public void onPress(){if(active)action.run();}
    @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
    @Override public void renderWidget(GuiGraphics g,int mx,int my,float pt){
        MineNorthStyle.button(g,getX(),getY(),width,height,getMessage().getString(),color,isHoveredOrFocused(),active);
    }
}
