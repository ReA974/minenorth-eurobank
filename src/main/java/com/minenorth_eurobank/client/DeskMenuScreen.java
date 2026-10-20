package com.minenorth_eurobank.client;

import com.minenorth_eurobank.EuroBank;
import com.minenorth_eurobank.Network;
import com.minenorth_eurobank.packet.DeskMenuPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Menu principal du guichet (PNJ banquier). */
@OnlyIn(Dist.CLIENT)
public class DeskMenuScreen extends Screen {
    private static final int W = 260, H = 176;
    private static final int BLUE = 0xFF161048;
    private static final int TEXT = 0xFFCFE3FF;
    private static final int WARN = 0xFFFFE066;
    private static final ResourceLocation LOGO = new ResourceLocation(EuroBank.MODID, "textures/gui/logo.png");

    private String message = "";
    private int left, top;

    public void update(String msg) {
        if (msg != null && !msg.isEmpty()) message = msg;
    }

    public DeskMenuScreen() {
        super(Component.literal("Banque"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(new AtmButton(left + W - 80, top + 10, 66, 14, Component.literal("Fermer"), AtmButton.GHOST, this::onClose));
        addRenderableWidget(new AtmButton(left + 20, top + 44, W - 40, 22, Component.literal("Prêt : demande, remboursement"), AtmButton.CYAN,
                () -> Network.CHANNEL.sendToServer(DeskMenuPacket.loan())));
        addRenderableWidget(new AtmButton(left + 20, top + 70, W - 40, 22, Component.literal("Contacter mon banquier"), AtmButton.CYAN,
                () -> minecraft.setScreen(new ContactScreen(this))));
        addRenderableWidget(new AtmButton(left + 20, top + 96, W - 40, 22, Component.literal("Acheter un coffre de banque (bientôt)"),
                AtmButton.DARK, () -> { }).enabled(false));
        addRenderableWidget(new AtmButton(left + 20, top + 122, W - 40, 22, Component.literal("Acheter un coffre-fort"),
                AtmButton.CYAN, () -> minecraft.setScreen(new SafeShopScreen(this))));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
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
        g.blit(LOGO, left + 10, top + 4, 28, 28, 0, 0, 96, 96, 96, 96);
        g.pose().pushPose();
        g.pose().scale(1.6f, 1.6f, 1f);
        g.drawString(font, Component.literal("MINENORTH BANK").withStyle(ChatFormatting.BOLD),
                Math.round((left + 44) / 1.6f), Math.round((top + 12) / 1.6f), 0xFFFFFFFF, false);
        g.pose().popPose();
        g.drawString(font, "Bonjour, que puis-je faire pour vous ?", left + 20, top + 32, TEXT, false);
        if (!message.isEmpty()) g.drawString(font, font.plainSubstrByWidth(message, W - 40), left + 20, top + 154, WARN, false);
        super.render(g, mx, my, pt);
    }
}
