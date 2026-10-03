package com.minenorth.eurobank.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Bouton plat façon écran de distributeur. */
@OnlyIn(Dist.CLIENT)
public class AtmButton extends AbstractButton {
    public static final int CYAN = 0xFF00B4F5;
    public static final int DARK = 0xFF2A5BC7;
    public static final int PINK = 0xFFFF0A6B;
    public static final int GHOST = 0;

    private final Runnable action;
    private final int color;
    private Component leftText, rightText;

    public AtmButton(int x, int y, int w, int h, Component label, int color, Runnable action) {
        super(x, y, w, h, label);
        this.color = color;
        this.action = action;
    }

    public AtmButton sides(Component left, Component right) {
        this.leftText = left;
        this.rightText = right;
        return this;
    }

    public AtmButton enabled(boolean on) {
        this.active = on;
        return this;
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 35);
        int g = Math.min(255, ((c >> 8) & 0xFF) + 35);
        int b = Math.min(255, (c & 0xFF) + 35);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        Font font = Minecraft.getInstance().font;
        int x = getX(), y = getY();
        boolean hov = isHoveredOrFocused();
        int ty = y + (height - 8) / 2;

        if (color == GHOST) {
            int tc = !active ? 0xFF8FA8E0 : hov ? 0xFFFFFFFF : 0xFFCFE3FF;
            int tx = x + width - font.width(getMessage());
            g.drawString(font, getMessage(), tx, ty, tc, false);
            if (hov && active) g.fill(tx, ty + 9, x + width, ty + 10, tc);
            return;
        }

        int bg = !active ? 0xFF1F4AA8 : hov ? lighten(color) : color;
        g.fill(x, y, x + width, y + height, bg);
        int tc = active ? 0xFFFFFFFF : 0xFF8FA8E0;
        if (leftText != null) {
            g.drawString(font, leftText, x + 14, ty, tc, false);
            g.drawString(font, rightText, x + width - 14 - font.width(rightText), ty, tc, false);
        } else {
            g.drawCenteredString(font, getMessage(), x + width / 2, ty, tc);
        }
    }
}
