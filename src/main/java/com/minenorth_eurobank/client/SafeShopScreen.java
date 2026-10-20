package com.minenorth_eurobank.client;

import com.minenorth_eurobank.EuroBank;
import com.minenorth_eurobank.Money;
import com.minenorth_eurobank.Network;
import com.minenorth_eurobank.packet.DeskMenuPacket;
import com.minenorth_eurobank.safe.SafeTier;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Boutique de coffres-forts du guichet : trois niveaux, paiement par carte. */
@OnlyIn(Dist.CLIENT)
public class SafeShopScreen extends Screen {
    private static final int W = 320, H = 190;
    private static final int BLUE = 0xFF161048;
    private static final int PANEL = 0xFF0E0A34;
    private static final int TEXT = 0xFFCFE3FF;
    private static final int WARN = 0xFFFFE066;
    private static final int[] ACCENT = {0xFF20AAEB, 0xFFFFC94D, 0xFFFF5FA8};
    private static final ResourceLocation LOGO = new ResourceLocation(EuroBank.MODID, "textures/gui/logo.png");

    private final DeskMenuScreen parent;
    private String message = "";
    private int left, top;

    public SafeShopScreen(DeskMenuScreen parent) {
        super(Component.literal("Coffres-forts"));
        this.parent = parent;
    }

    public void update(String msg) {
        if (msg != null && !msg.isEmpty()) message = msg;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(new AtmButton(left + W - 80, top + 8, 66, 14, Component.literal("Retour"), AtmButton.GHOST, this::onClose));
        for (SafeTier t : SafeTier.values()) {
            int idx = t.ordinal();
            addRenderableWidget(new AtmButton(left + W - 96, top + 46 + idx * 38, 80, 24, Component.literal("Acheter"),
                    ACCENT[idx], () -> Network.CHANNEL.sendToServer(DeskMenuPacket.buySafe(idx))));
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left - 3, top - 3, left + W + 3, top + H + 3, 0xFF0E0E10);
        g.fill(left, top, left + W, top + H, BLUE);
        RenderSystem.enableBlend();
        g.blit(LOGO, left + 10, top + 5, 24, 24, 0, 0, 96, 96, 96, 96);
        g.pose().pushPose();
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(font, Component.literal("COFFRES-FORTS").withStyle(ChatFormatting.BOLD),
                Math.round((left + 40) / 1.5f), Math.round((top + 10) / 1.5f), 0xFFFFFFFF, false);
        g.pose().popPose();
        g.drawString(font, "Payé par carte. Seul vous pourrez l'ouvrir.", left + 40, top + 28, TEXT, false);
        for (SafeTier t : SafeTier.values()) {
            int y = top + 44 + t.ordinal() * 38;
            g.fill(left + 10, y, left + W - 104, y + 32, PANEL);
            g.fill(left + 10, y, left + 13, y + 32, ACCENT[t.ordinal()]);
            g.drawString(font, Component.literal("Coffre-fort " + t.label).withStyle(ChatFormatting.BOLD), left + 20, y + 5, 0xFFFFFFFF, false);
            g.drawString(font, t.slots + " emplacements", left + 20, y + 18, TEXT, false);
            String price = Money.format(t.price);
            g.drawString(font, price, left + W - 108 - font.width(price), y + 12, ACCENT[t.ordinal()], false);
        }
        if (!message.isEmpty()) g.drawString(font, font.plainSubstrByWidth(message, W - 24), left + 12, top + 166, WARN, false);
        super.render(g, mx, my, pt);
    }
}
