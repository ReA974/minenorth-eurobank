package com.minenorth.eurobank.client;

import com.minenorth.eurobank.AdminListPacket;
import com.minenorth.eurobank.AdminListPacket.Entry;
import com.minenorth.eurobank.AdminPacket;
import com.minenorth.eurobank.AdminPacket.Op;
import com.minenorth.eurobank.Money;
import com.minenorth.eurobank.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Interface d'administration (ops) : liste des comptes + ajout / retrait / définition de solde. */
@OnlyIn(Dist.CLIENT)
public class AdminScreen extends Screen {
    private static final int W = 300, H = 196, ROWS = 5, ROW_H = 14;
    private static final int BLUE = 0xFF0B3A94;
    private static final int CYAN = AtmButton.CYAN;
    private static final int TEXT = 0xFFCFE3FF;

    @Nullable
    private final Screen parent;
    private List<Entry> entries;
    private long total;
    private String message;
    private String searchText = "", amountText = "";
    private EditBox searchBox, amountBox;
    private UUID selected;
    private int offset;
    private int left, top;

    public AdminScreen(AdminListPacket m, @Nullable Screen parent) {
        super(Component.literal("Administration"));
        this.parent = parent;
        this.entries = m.entries;
        this.total = m.total;
        this.message = m.message;
    }

    public void update(AdminListPacket m) {
        saveTexts();
        this.entries = m.entries;
        this.total = m.total;
        this.message = m.message;
        if (selected != null && entries.stream().noneMatch(e -> e.id().equals(selected))) selected = null;
        rebuildWidgets();
    }

    private void saveTexts() {
        if (searchBox != null) searchText = searchBox.getValue();
        if (amountBox != null) amountText = amountBox.getValue();
    }

    private List<Entry> filtered() {
        String q = searchText.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return entries;
        return entries.stream().filter(e -> e.name().toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    private void act(Op op) {
        if (selected == null) return;
        long amount = op == Op.SET && amountBox.getValue().trim().equals("0") ? 0 : Money.parseEuros(amountBox.getValue());
        Network.CHANNEL.sendToServer(new AdminPacket(op, selected, amount));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;

        addRenderableWidget(new AtmButton(left + W - 112, top + 10, 98, 16,
                Component.literal(parent != null ? "Retour" : "Fermer"), AtmButton.GHOST, this::onClose));

        searchBox = new EditBox(font, left + 16, top + 44, 268, 14, Component.literal("Recherche"));
        searchBox.setHint(Component.literal("Rechercher un joueur..."));
        searchBox.setValue(searchText);
        searchBox.setResponder(s -> { searchText = s; offset = 0; });
        addRenderableWidget(searchBox);

        amountBox = new EditBox(font, left + 16, top + 140, 96, 18, Component.literal("Montant"));
        amountBox.setHint(Component.literal("Montant en €"));
        amountBox.setValue(amountText);
        addRenderableWidget(amountBox);

        boolean sel = selected != null;
        addRenderableWidget(new AtmButton(left + 118, top + 139, 52, 20, Component.literal("Ajouter"), CYAN, () -> act(Op.ADD)).enabled(sel));
        addRenderableWidget(new AtmButton(left + 174, top + 139, 52, 20, Component.literal("Retirer"), CYAN, () -> act(Op.TAKE)).enabled(sel));
        addRenderableWidget(new AtmButton(left + 230, top + 139, 54, 20, Component.literal("Fixer"), CYAN, () -> act(Op.SET)).enabled(sel));

        addRenderableWidget(new AtmButton(left + 270, top + 62, 14, 34, Component.literal("^"), AtmButton.DARK, () -> offset = Math.max(0, offset - 1)));
        addRenderableWidget(new AtmButton(left + 270, top + 98, 14, 34, Component.literal("v"), AtmButton.DARK,
                () -> offset = Math.min(Math.max(0, filtered().size() - ROWS), offset + 1)));
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
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, filtered().size() - ROWS);
        offset = Math.max(0, Math.min(max, offset - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        List<Entry> list = filtered();
        for (int i = 0; i < ROWS; i++) {
            int idx = offset + i;
            int y = top + 62 + i * ROW_H;
            if (idx < list.size() && mx >= left + 16 && mx < left + 266 && my >= y && my < y + ROW_H) {
                saveTexts();
                selected = list.get(idx).id();
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left - 3, top - 3, left + W + 3, top + H + 3, 0xFF0E0E10);
        g.fill(left, top, left + W, top + H, BLUE);

        g.pose().pushPose();
        g.pose().scale(1.8f, 1.8f, 1f);
        g.drawString(font, Component.literal("ADMIN").withStyle(ChatFormatting.BOLD), Math.round((left + 16) / 1.8f), Math.round((top + 12) / 1.8f), 0xFFFFFFFF, false);
        g.pose().popPose();
        g.drawString(font, "Total en banque : " + Money.format(total) + "  (" + entries.size() + " comptes)", left + 16, top + 32, TEXT, false);

        g.fill(left + 16, top + 61, left + 266, top + 62 + ROWS * ROW_H + 1, 0xFF082B6E);
        List<Entry> list = filtered();
        offset = Math.max(0, Math.min(offset, Math.max(0, list.size() - ROWS)));
        for (int i = 0; i < ROWS; i++) {
            int idx = offset + i;
            if (idx >= list.size()) break;
            Entry e = list.get(idx);
            int y = top + 62 + i * ROW_H;
            boolean sel = e.id().equals(selected);
            if (sel) g.fill(left + 16, y, left + 266, y + ROW_H, CYAN);
            else if (mx >= left + 16 && mx < left + 266 && my >= y && my < y + ROW_H) g.fill(left + 16, y, left + 266, y + ROW_H, 0xFF1A52B8);
            g.drawString(font, font.plainSubstrByWidth(e.name(), 130), left + 20, y + 3, 0xFFFFFFFF, false);
            String bal = Money.format(e.cents());
            g.drawString(font, bal, left + 262 - font.width(bal), y + 3, 0xFFFFFFFF, false);
        }
        if (list.isEmpty()) g.drawString(font, "Aucun compte.", left + 20, top + 66, TEXT, false);

        g.drawString(font, selected == null ? "Sélectionnez un compte." : "Compte sélectionné.", left + 16, top + 128, TEXT, false);
        if (message != null && !message.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(message, 268), left + 16, top + 168, 0xFFFFE066, false);
        }
        super.render(g, mx, my, pt);
    }
}
