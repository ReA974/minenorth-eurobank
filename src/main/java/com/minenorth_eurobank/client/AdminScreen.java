package com.minenorth_eurobank.client;

import com.minenorth_eurobank.packet.AdminListPacket;
import com.minenorth_eurobank.EuroBank;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import com.minenorth_eurobank.packet.AdminListPacket.Entry;
import com.minenorth_eurobank.packet.AdminPacket;
import com.minenorth_eurobank.packet.AdminPacket.Op;
import com.minenorth_eurobank.Money;
import com.minenorth_eurobank.Network;
import com.minenorth_eurobank.packet.WatchPacket;
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

/** Interface d'administration (ops) : comptes, banquiers et capital de la banque. */
@OnlyIn(Dist.CLIENT)
public class AdminScreen extends Screen {
    private static final int W = 300, H = 216, ROWS = 5, ROW_H = 14;
    private static final int LIST_Y = 70;
    private static final int BLUE = 0xFF161048;
    private static final ResourceLocation LOGO = new ResourceLocation(EuroBank.MODID, "textures/gui/logo.png");
    private static final int CYAN = AtmButton.CYAN;
    private static final int TEXT = 0xFFCFE3FF;

    @Nullable
    private final Screen parent;
    private List<Entry> entries;
    private long total, reserve;
    private String message;
    private String searchText = "", amountText = "";
    private EditBox searchBox, amountBox;
    private UUID selected;
    private int offset;
    private int left, top;
    private boolean watching;

    public AdminScreen(AdminListPacket m, @Nullable Screen parent) {
        super(Component.literal("Administration"));
        this.parent = parent;
        this.entries = m.entries;
        this.total = m.total;
        this.reserve = m.reserve;
        this.message = m.message;
    }

    public void update(AdminListPacket m) {
        Entry before = selectedEntry();
        Boolean wasBanker = before == null ? null : before.banker();
        boolean searchFocus = searchBox != null && searchBox.isFocused();
        boolean amountFocus = amountBox != null && amountBox.isFocused();
        this.entries = m.entries;
        this.total = m.total;
        this.reserve = m.reserve;
        if (!m.message.isEmpty()) this.message = m.message;
        boolean rebuild = false;
        if (selected != null && entries.stream().noneMatch(e -> e.id().equals(selected))) {
            selected = null;
            rebuild = true;
        }
        Entry after = selectedEntry();
        Boolean nowBanker = after == null ? null : after.banker();
        if (!java.util.Objects.equals(wasBanker, nowBanker)) rebuild = true;
        if (rebuild) {
            saveTexts();
            rebuildWidgets();
            if (searchFocus && searchBox != null) setFocused(searchBox);
            else if (amountFocus && amountBox != null) setFocused(amountBox);
        }
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

    private Entry selectedEntry() {
        if (selected == null) return null;
        for (Entry e : entries) if (e.id().equals(selected)) return e;
        return null;
    }

    private void act(Op op) {
        boolean noTarget = op == Op.RESERVE_ADD || op == Op.RESERVE_TAKE;
        boolean noAmount = op == Op.BANKER_ADD || op == Op.BANKER_REMOVE;
        if (!noTarget && selected == null) return;
        long amount = noAmount ? 0
                : op == Op.SET && amountBox.getValue().trim().equals("0") ? 0
                : Money.parseEuros(amountBox.getValue());
        Network.CHANNEL.sendToServer(new AdminPacket(op, noTarget ? new UUID(0, 0) : selected, amount));
    }

    @Override
    protected void init() {
        if (!watching) {
            watching = true;
            Network.CHANNEL.sendToServer(new WatchPacket(WatchPacket.Kind.ADMIN, true));
        }
        left = (width - W) / 2;
        top = (height - H) / 2;
        boolean sel = selected != null;
        Entry e = selectedEntry();

        addRenderableWidget(new AtmButton(left + W - 112, top + 10, 98, 16,
                Component.literal(parent != null ? "Retour" : "Fermer"), AtmButton.GHOST, this::onClose));

        searchBox = new EditBox(font, left + 16, top + 52, 268, 14, Component.literal("Recherche"));
        searchBox.setHint(Component.literal("Rechercher un joueur..."));
        searchBox.setValue(searchText);
        searchBox.setResponder(s -> { searchText = s; offset = 0; });
        addRenderableWidget(searchBox);

        amountBox = new EditBox(font, left + 16, top + 152, 96, 18, Component.literal("Montant"));
        amountBox.setHint(Component.literal("Montant en €"));
        amountBox.setValue(amountText);
        addRenderableWidget(amountBox);

        addRenderableWidget(new AtmButton(left + 118, top + 151, 52, 20, Component.literal("Ajouter"), CYAN, () -> act(Op.ADD)).enabled(sel));
        addRenderableWidget(new AtmButton(left + 174, top + 151, 52, 20, Component.literal("Retirer"), CYAN, () -> act(Op.TAKE)).enabled(sel));
        addRenderableWidget(new AtmButton(left + 230, top + 151, 54, 20, Component.literal("Fixer"), CYAN, () -> act(Op.SET)).enabled(sel));

        boolean isBanker = e != null && e.banker();
        addRenderableWidget(new AtmButton(left + 16, top + 175, 64, 18, Component.literal("+ Banquier"), AtmButton.DARK, () -> act(Op.BANKER_ADD)).enabled(sel && !isBanker));
        addRenderableWidget(new AtmButton(left + 84, top + 175, 64, 18, Component.literal("- Banquier"), AtmButton.DARK, () -> act(Op.BANKER_REMOVE)).enabled(sel && isBanker));
        addRenderableWidget(new AtmButton(left + 152, top + 175, 64, 18, Component.literal("+ Capital"), AtmButton.DARK, () -> act(Op.RESERVE_ADD)));
        addRenderableWidget(new AtmButton(left + 220, top + 175, 64, 18, Component.literal("- Capital"), AtmButton.DARK, () -> act(Op.RESERVE_TAKE)));

        addRenderableWidget(new AtmButton(left + 270, top + LIST_Y, 14, 34, Component.literal("^"), AtmButton.DARK,
                () -> offset = Math.max(0, offset - 1)));
        addRenderableWidget(new AtmButton(left + 270, top + LIST_Y + 36, 14, 34, Component.literal("v"), AtmButton.DARK,
                () -> offset = Math.min(Math.max(0, filtered().size() - ROWS), offset + 1)));
    }

    @Override
    public void removed() {
        watching = false;
        Network.CHANNEL.sendToServer(new WatchPacket(WatchPacket.Kind.ADMIN, false));
        super.removed();
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
            int y = top + LIST_Y + i * ROW_H;
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

        RenderSystem.enableBlend();
        g.blit(LOGO, left + 10, top + 4, 24, 24, 0, 0, 96, 96, 96, 96);
        g.pose().pushPose();
        g.pose().scale(1.8f, 1.8f, 1f);
        g.drawString(font, Component.literal("ADMIN").withStyle(ChatFormatting.BOLD), Math.round((left + 40) / 1.8f), Math.round((top + 12) / 1.8f), 0xFFFFFFFF, false);
        g.pose().popPose();
        g.drawString(font, "Total des comptes : " + Money.format(total) + "  (" + entries.size() + ")", left + 16, top + 30, TEXT, false);
        g.drawString(font, "Capital de la banque : " + Money.format(reserve), left + 16, top + 40, CYAN, false);

        g.fill(left + 16, top + LIST_Y - 1, left + 266, top + LIST_Y + ROWS * ROW_H + 1, 0xFF0E0A34);
        List<Entry> list = filtered();
        offset = Math.max(0, Math.min(offset, Math.max(0, list.size() - ROWS)));
        for (int i = 0; i < ROWS; i++) {
            int idx = offset + i;
            if (idx >= list.size()) break;
            Entry e = list.get(idx);
            int y = top + LIST_Y + i * ROW_H;
            boolean sel = e.id().equals(selected);
            if (sel) g.fill(left + 16, y, left + 266, y + ROW_H, CYAN);
            else if (mx >= left + 16 && mx < left + 266 && my >= y && my < y + ROW_H) g.fill(left + 16, y, left + 266, y + ROW_H, 0xFF2E2480);
            String name = (e.banker() ? "[B] " : "") + e.name();
            g.drawString(font, font.plainSubstrByWidth(name, 130), left + 20, y + 3, 0xFFFFFFFF, false);
            String bal = Money.format(e.cents());
            g.drawString(font, bal, left + 262 - font.width(bal), y + 3, 0xFFFFFFFF, false);
        }
        if (list.isEmpty()) g.drawString(font, "Aucun compte.", left + 20, top + LIST_Y + 4, TEXT, false);

        Entry se = selectedEntry();
        g.drawString(font, se == null ? "Sélectionnez un compte  ([B] = banquier)." : "Sélectionné : " + se.name() + (se.banker() ? " (banquier)" : ""),
                left + 16, top + 142, TEXT, false);
        if (message != null && !message.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(message, 268), left + 16, top + 198, 0xFFFFE066, false);
        }
        super.render(g, mx, my, pt);
    }
}
