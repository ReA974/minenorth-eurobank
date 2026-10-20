package com.minenorth_eurobank.client;

import com.minenorth_eurobank.EuroBank;
import com.minenorth_eurobank.Network;
import com.minenorth_eurobank.packet.DeskMenuPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** « Contacter mon banquier » : nom, prénom, téléphone et demande ; le banquier est prévenu et rappelle. */
@OnlyIn(Dist.CLIENT)
public class ContactScreen extends Screen {
    private static final int W = 300, H = 188;
    private static final int BLUE = 0xFF161048;
    private static final int CYAN = AtmButton.CYAN;
    private static final int TEXT = 0xFFCFE3FF;
    private static final int WARN = 0xFFFFE066;
    private static final ResourceLocation LOGO = new ResourceLocation(EuroBank.MODID, "textures/gui/logo.png");

    private final DeskMenuScreen parent;
    private String message = "";
    private boolean sent;
    private String lastName = "", firstName = "", phone = "", request = "";
    private EditBox lastBox, firstBox, phoneBox, requestBox;
    private int left, top;

    public ContactScreen(DeskMenuScreen parent) {
        super(Component.literal("Contacter mon banquier"));
        this.parent = parent;
    }

    public void update(String msg) {
        if (msg == null || msg.isEmpty()) return;
        message = msg;
        sent = msg.startsWith("Demande envoyée");
        rebuildWidgets();
    }

    private void saveTexts() {
        if (lastBox != null) lastName = lastBox.getValue();
        if (firstBox != null) firstName = firstBox.getValue();
        if (phoneBox != null) phone = phoneBox.getValue();
        if (requestBox != null) request = requestBox.getValue();
    }

    private EditBox box(int x, int y, int w, String hint, int max, String value) {
        EditBox b = new EditBox(font, left + x, top + y, w, 16, Component.literal(hint));
        b.setHint(Component.literal(hint));
        b.setMaxLength(max);
        b.setValue(value);
        return b;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(new AtmButton(left + W - 80, top + 10, 66, 14, Component.literal("Retour"), AtmButton.GHOST, this::onClose));
        lastBox = box(16, 56, 130, "Nom", DeskMenuPacket.NAME_MAX, lastName);
        firstBox = box(158, 56, 126, "Prénom", DeskMenuPacket.NAME_MAX, firstName);
        phoneBox = box(16, 86, 130, "Téléphone (4 chiffres)", 4, phone);
        phoneBox.setFilter(s -> s.chars().allMatch(Character::isDigit));
        requestBox = box(16, 116, 268, "Votre demande", DeskMenuPacket.MESSAGE_MAX, request);
        addRenderableWidget(lastBox);
        addRenderableWidget(firstBox);
        addRenderableWidget(phoneBox);
        addRenderableWidget(requestBox);
        addRenderableWidget(new AtmButton(left + 16, top + 148, 150, 20, Component.literal("Envoyer"), CYAN, () -> {
            saveTexts();
            Network.CHANNEL.sendToServer(DeskMenuPacket.contact(firstName, lastName, phone, request));
        }).enabled(!sent));
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
        g.blit(LOGO, left + 10, top + 4, 24, 24, 0, 0, 96, 96, 96, 96);
        g.pose().pushPose();
        g.pose().scale(1.4f, 1.4f, 1f);
        g.drawString(font, Component.literal("CONTACTER MON BANQUIER").withStyle(ChatFormatting.BOLD),
                Math.round((left + 40) / 1.4f), Math.round((top + 12) / 1.4f), 0xFFFFFFFF, false);
        g.pose().popPose();
        g.drawString(font, "Le banquier sera prévenu et vous rappellera.", left + 16, top + 34, TEXT, false);
        g.drawString(font, "Nom", left + 16, top + 46, CYAN, false);
        g.drawString(font, "Prénom", left + 158, top + 46, CYAN, false);
        g.drawString(font, "Téléphone", left + 16, top + 76, CYAN, false);
        g.drawString(font, "Votre demande", left + 16, top + 106, CYAN, false);
        if (!message.isEmpty()) g.drawString(font, font.plainSubstrByWidth(message, 130), left + 170, top + 148, WARN, false);
        super.render(g, mx, my, pt);
    }
}
