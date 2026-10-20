package com.minenorth_eurobank.client;

import com.minenorth_eurobank.EuroBank;
import com.minenorth_eurobank.Money;
import com.minenorth_eurobank.Network;
import com.minenorth_eurobank.banker.TabletOp;
import com.minenorth_eurobank.banker.TabletService;
import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.loan.LoanService;
import com.minenorth_eurobank.packet.TabletPacket;
import com.minenorth_eurobank.packet.WatchPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Tablette du banquier. Le serveur envoie, pour chaque onglet, une liste de lignes et le détail de la ligne
 * sélectionnée ; l'écran ne fait qu'afficher et renvoyer les actions.
 */
@OnlyIn(Dist.CLIENT)
public class TabletScreen extends Screen {
    private static final int W = 380, H = 238, ROWS = 6, ROW_H = 12;
    private static final int BLUE = 0xFF161048;
    private static final int CYAN = AtmButton.CYAN;
    private static final int TEXT = 0xFFCFE3FF;
    private static final int WARN = 0xFFFFE066;
    private static final int ALERT = 0xFFFF6A9A;
    private static final int N_TERMS = LoanService.TERMS.length;
    private static final String[] TAB_NAMES = {"Prêts", "Comptes", "Transactions", "Alertes", "Taux"};
    private static final ResourceLocation LOGO = new ResourceLocation(EuroBank.MODID, "textures/gui/logo.png");

    private record Row(UUID id, String left, String right, int color, UUID account, int status, int rate,
                       boolean frozen, boolean done, List<String> detail) {}

    private int tab;
    private long reserve, threshold;
    private int openAlerts;
    private int[] termRates = new int[N_TERMS];
    private String message = "", note = "", filterName = "";
    private UUID selected;
    private List<Row> rows = new ArrayList<>();
    private int offset;
    private int left, top;
    private boolean watching;

    // saisies conservées entre deux reconstructions
    private String rateText = "5", noteText = "", thresholdText = "";
    private final String[] termTexts = new String[N_TERMS];
    private EditBox rateBox, noteBox, thresholdBox;
    private final EditBox[] termBoxes = new EditBox[N_TERMS];
    private String lastSig = "";
    private UUID lastNoteFor;

    public TabletScreen(CompoundTag data) {
        super(Component.literal("Tablette banquier"));
        read(data);
        lastSig = sig();
    }

    // ------------------------------------------------------------------ données
    private void read(CompoundTag t) {
        tab = t.getInt("tab");
        reserve = t.getLong("reserve");
        threshold = t.getLong("threshold");
        openAlerts = t.getInt("alerts");
        int[] r = t.getIntArray("rates");
        for (int i = 0; i < N_TERMS; i++) termRates[i] = i < r.length ? r[i] : 0;
        String m = t.getString("msg");
        if (!m.isEmpty()) message = m;
        selected = t.hasUUID("sel") ? t.getUUID("sel") : null;
        note = t.getString("note");
        filterName = t.getString("filter");
        rows = new ArrayList<>();
        ListTag list = t.getList("rows", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            List<String> d = new ArrayList<>();
            ListTag dl = c.getList("d", Tag.TAG_STRING);
            for (int j = 0; j < dl.size(); j++) d.add(dl.getString(j));
            rows.add(new Row(c.getUUID("id"), c.getString("l"), c.getString("r"), c.getInt("c"),
                    c.hasUUID("a") ? c.getUUID("a") : null, c.getInt("s"), c.getInt("rate"),
                    c.getBoolean("fz"), c.getBoolean("done"), d));
        }
        if (tab == TabletService.TAB_RATES) {
            for (int i = 0; i < N_TERMS; i++) if (termTexts[i] == null) termTexts[i] = fmtInput(termRates[i]);
            if (thresholdText.isEmpty()) thresholdText = String.valueOf(threshold / 100);
        }
    }

    private Row selectedRow() {
        if (selected == null) return null;
        for (Row r : rows) if (r.id().equals(selected)) return r;
        return null;
    }

    /** Ce qui change la structure de l'écran (boutons actifs, champs) et non juste les textes. */
    private String sig() {
        Row s = selectedRow();
        return tab + "|" + selected + "|" + (s == null ? "" : s.status() + "" + s.frozen() + s.done()) + "|" + filterName + "|" + openAlerts;
    }

    public void update(CompoundTag data) {
        int oldTab = tab;
        UUID oldSel = selected;
        saveTexts();
        read(data);
        if (oldTab != tab) {
            offset = 0;
            message = data.getString("msg");
        }
        Row s = selectedRow();
        if (s != null && !s.id().equals(oldSel) && tab == TabletService.TAB_LOANS) rateText = fmtInput(s.rate());
        if (tab == TabletService.TAB_ACCOUNTS && selected != null && !selected.equals(lastNoteFor)) {
            noteText = note;
            lastNoteFor = selected;
        }
        String now = sig();
        if (!now.equals(lastSig)) {
            lastSig = now;
            rebuildWidgets();
        }
    }

    private void saveTexts() {
        if (rateBox != null) rateText = rateBox.getValue();
        if (noteBox != null) noteText = noteBox.getValue();
        if (thresholdBox != null) thresholdText = thresholdBox.getValue();
        for (int i = 0; i < N_TERMS; i++) if (termBoxes[i] != null) termTexts[i] = termBoxes[i].getValue();
    }

    private static String fmtInput(int bp) {
        return bp % 100 == 0 ? String.valueOf(bp / 100) : String.format(Locale.ROOT, "%.2f", bp / 100.0);
    }

    /** "5", "5,5" ou "5.5" -> points de base (500, 550), ou -1 si invalide. */
    private static int parseRateBp(String s) {
        try {
            s = s.trim().replace(',', '.').replace("%", "").trim();
            if (s.isEmpty()) return -1;
            int bp = new BigDecimal(s).movePointRight(2).intValueExact();
            return bp >= 0 && bp <= LoanService.MAX_RATE_BP ? bp : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ envoi
    private void view(int newTab, UUID sel, UUID filter) {
        saveTexts();
        Network.CHANNEL.sendToServer(TabletPacket.view(newTab, sel, filter));
    }

    private void select(Row r) {
        saveTexts();
        UUID filter = null;
        Network.CHANNEL.sendToServer(TabletPacket.view(tab, r.id(), filter));
    }

    private void send(TabletOp op, UUID id, long num) {
        saveTexts();
        Network.CHANNEL.sendToServer(TabletPacket.op(op, id, num));
    }

    private void saveRates() {
        saveTexts();
        int[] r = new int[N_TERMS];
        for (int i = 0; i < N_TERMS; i++) {
            int bp = parseRateBp(termTexts[i] == null ? "" : termTexts[i]);
            if (bp < 0) {
                message = "Taux invalide (0 à " + (LoanService.MAX_RATE_BP / 100) + " %).";
                return;
            }
            r[i] = bp;
        }
        long th = Money.parseEuros(thresholdText);
        if (th < 0) {
            message = "Seuil d'alerte invalide.";
            return;
        }
        Network.CHANNEL.sendToServer(TabletPacket.rates(r));
        Network.CHANNEL.sendToServer(TabletPacket.op(TabletOp.SET_THRESHOLD, null, th));
    }

    // ------------------------------------------------------------------ widgets
    @Override
    protected void init() {
        if (!watching) {
            watching = true;
            Network.CHANNEL.sendToServer(new WatchPacket(WatchPacket.Kind.BANKER, true));
        }
        left = (width - W) / 2;
        top = (height - H) / 2;
        rateBox = noteBox = thresholdBox = null;
        for (int i = 0; i < N_TERMS; i++) termBoxes[i] = null;

        addRenderableWidget(new AtmButton(left + W - 80, top + 8, 66, 14, Component.literal("Fermer"), AtmButton.GHOST, this::onClose));
        int x = left + 10;
        int[] widths = {52, 62, 86, 76, 48};
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int idx = i;
            String label = TAB_NAMES[i] + (i == TabletService.TAB_ALERTS && openAlerts > 0 ? " (" + openAlerts + ")" : "");
            addRenderableWidget(new AtmButton(x, top + 34, widths[i] - 3, 16, Component.literal(label),
                    i == tab ? CYAN : AtmButton.DARK, () -> view(idx, null, null)));
            x += widths[i];
        }

        addRenderableWidget(new AtmButton(left + W - 24, top + 56, 14, 34, Component.literal("^"), AtmButton.DARK,
                () -> offset = Math.max(0, offset - 1)));
        addRenderableWidget(new AtmButton(left + W - 24, top + 92, 14, 34, Component.literal("v"), AtmButton.DARK,
                () -> offset = Math.min(Math.max(0, rows.size() - ROWS), offset + 1)));

        Row sel = selectedRow();
        int by = top + 214;
        switch (tab) {
            case TabletService.TAB_LOANS -> {
                boolean pending = sel != null && sel.status() == Loan.PENDING;
                boolean running = sel != null && (sel.status() == Loan.ACTIVE || sel.status() == Loan.OVERDUE);
                rateBox = new EditBox(font, left + 10, by, 44, 18, Component.literal("Taux"));
                rateBox.setHint(Component.literal("Taux %"));
                rateBox.setValue(rateText);
                addRenderableWidget(rateBox);
                addRenderableWidget(new AtmButton(left + 58, by, 58, 18, Component.literal("Accepter"), CYAN, () -> {
                    int bp = parseRateBp(rateBox.getValue());
                    if (bp < 0) message = "Taux invalide.";
                    else send(TabletOp.APPROVE, selected, bp);
                }).enabled(pending));
                addRenderableWidget(new AtmButton(left + 120, by, 54, 18, Component.literal("Refuser"), AtmButton.PINK,
                        () -> send(TabletOp.REJECT, selected, 0)).enabled(pending));
                addRenderableWidget(new AtmButton(left + 178, by, 76, 18, Component.literal("Annuler dette"), AtmButton.DARK,
                        () -> send(TabletOp.FORGIVE, selected, 0)).enabled(running));
                addRenderableWidget(new AtmButton(left + 258, by, 108, 18, Component.literal("Voir le compte"), AtmButton.DARK,
                        () -> view(TabletService.TAB_ACCOUNTS, sel.account(), null)).enabled(sel != null));
            }
            case TabletService.TAB_ACCOUNTS -> {
                noteBox = new EditBox(font, left + 10, by, 150, 18, Component.literal("Note"));
                noteBox.setHint(Component.literal("Note privée"));
                noteBox.setMaxLength(TabletService.NOTE_MAX);
                noteBox.setValue(noteText);
                addRenderableWidget(noteBox);
                addRenderableWidget(new AtmButton(left + 164, by, 62, 18, Component.literal("Noter"), AtmButton.DARK,
                        () -> {
                            saveTexts();
                            Network.CHANNEL.sendToServer(TabletPacket.text(TabletOp.NOTE, selected, noteText));
                        }).enabled(sel != null));
                boolean fz = sel != null && sel.frozen();
                addRenderableWidget(new AtmButton(left + 230, by, 68, 18, Component.literal(fz ? "Dégeler" : "Geler"),
                        fz ? CYAN : AtmButton.PINK, () -> send(TabletOp.FREEZE, selected, fz ? 0 : 1)).enabled(sel != null));
                addRenderableWidget(new AtmButton(left + 302, by, 64, 18, Component.literal("Opérations"), AtmButton.DARK,
                        () -> view(TabletService.TAB_TX, null, selected)).enabled(sel != null));
            }
            case TabletService.TAB_TX -> {
                addRenderableWidget(new AtmButton(left + 10, by, 100, 18, Component.literal("Voir le compte"), AtmButton.DARK,
                        () -> view(TabletService.TAB_ACCOUNTS, sel.account(), null)).enabled(sel != null));
                if (!filterName.isEmpty()) {
                    addRenderableWidget(new AtmButton(left + 114, by, 120, 18, Component.literal("Tous les comptes"), AtmButton.DARK,
                            () -> view(TabletService.TAB_TX, null, null)));
                }
            }
            case TabletService.TAB_ALERTS -> {
                addRenderableWidget(new AtmButton(left + 10, by, 70, 18, Component.literal("Traitée"), CYAN,
                        () -> send(TabletOp.HANDLE, selected, 0)).enabled(sel != null && !sel.done()));
                addRenderableWidget(new AtmButton(left + 84, by, 90, 18, Component.literal("Tout traiter"), AtmButton.DARK,
                        () -> send(TabletOp.HANDLE_ALL, null, 0)));
                addRenderableWidget(new AtmButton(left + 178, by, 100, 18, Component.literal("Voir le compte"), AtmButton.DARK,
                        () -> view(TabletService.TAB_ACCOUNTS, sel.account(), null)).enabled(sel != null));
            }
            default -> {
                for (int i = 0; i < N_TERMS; i++) {
                    EditBox b = new EditBox(font, left + 84, top + 74 + i * 18, 56, 16, Component.literal("Taux"));
                    b.setMaxLength(6);
                    b.setValue(termTexts[i] == null ? "" : termTexts[i]);
                    termBoxes[i] = b;
                    addRenderableWidget(b);
                }
                thresholdBox = new EditBox(font, left + 250, top + 74, 80, 16, Component.literal("Seuil"));
                thresholdBox.setMaxLength(10);
                thresholdBox.setValue(thresholdText);
                addRenderableWidget(thresholdBox);
                addRenderableWidget(new AtmButton(left + 10, by, 150, 18, Component.literal("Enregistrer"), CYAN, this::saveRates));
            }
        }
    }

    @Override
    public void removed() {
        watching = false;
        Network.CHANNEL.sendToServer(new WatchPacket(WatchPacket.Kind.BANKER, false));
        super.removed();
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
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (tab == TabletService.TAB_RATES) return super.mouseScrolled(mx, my, delta);
        int max = Math.max(0, rows.size() - ROWS);
        offset = Math.max(0, Math.min(max, offset - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (tab == TabletService.TAB_RATES) return false;
        for (int i = 0; i < ROWS; i++) {
            int idx = offset + i;
            int y = top + 56 + i * ROW_H;
            if (idx < rows.size() && mx >= left + 10 && mx < left + W - 28 && my >= y && my < y + ROW_H) {
                select(rows.get(idx));
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ rendu
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left - 3, top - 3, left + W + 3, top + H + 3, 0xFF0E0E10);
        g.fill(left, top, left + W, top + H, BLUE);

        RenderSystem.enableBlend();
        g.blit(LOGO, left + 10, top + 4, 24, 24, 0, 0, 96, 96, 96, 96);
        g.pose().pushPose();
        g.pose().scale(1.6f, 1.6f, 1f);
        g.drawString(font, Component.literal("BANQUIER").withStyle(ChatFormatting.BOLD),
                Math.round((left + 40) / 1.6f), Math.round((top + 11) / 1.6f), 0xFFFFFFFF, false);
        g.pose().popPose();
        g.drawString(font, "Capital : " + Money.format(reserve), left + 140, top + 14, TEXT, false);

        if (tab == TabletService.TAB_RATES) renderRates(g);
        else renderList(g, mx, my);

        if (message != null && !message.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(message, 360), left + 10, top + 228, WARN, false);
        }
        super.render(g, mx, my, pt);
    }

    private void renderRates(GuiGraphics g) {
        g.drawString(font, "Taux par durée (annoncé aux clients)", left + 10, top + 58, CYAN, false);
        for (int i = 0; i < N_TERMS; i++) {
            int days = LoanService.TERMS[i];
            int y = top + 74 + i * 18;
            g.drawString(font, days == 1 ? "1 jour" : days + " jours", left + 10, y + 4, 0xFFFFFFFF, false);
            g.drawString(font, "%", left + 144, y + 4, TEXT, false);
            String v = termBoxes[i] != null ? termBoxes[i].getValue() : "";
            if (parseRateBp(v) < 0) g.drawString(font, "invalide", left + 156, y + 4, ALERT, false);
        }
        g.drawString(font, "Alerte automatique au-delà de (€)", left + 200, top + 58, CYAN, false);
        g.drawString(font, "Actuel : " + Money.format(threshold), left + 250, top + 94, TEXT, false);
        g.drawString(font, "Alertes : gros mouvements, rafales", left + 200, top + 118, TEXT, false);
        g.drawString(font, "d'opérations, entrées suspectes", left + 200, top + 128, TEXT, false);
        g.drawString(font, "après un braquage.", left + 200, top + 138, TEXT, false);
    }

    private void renderList(GuiGraphics g, int mx, int my) {
        String head = switch (tab) {
            case TabletService.TAB_LOANS -> {
                long pending = rows.stream().filter(r -> r.status() == Loan.PENDING).count();
                yield pending + " demande(s) en attente  -  " + (rows.size() - pending) + " prêt(s) en cours";
            }
            case TabletService.TAB_ACCOUNTS -> rows.size() + " compte(s) (joueurs et entreprises)";
            case TabletService.TAB_TX -> filterName.isEmpty() ? "Dernières opérations de tous les comptes"
                    : "Opérations de " + filterName;
            default -> openAlerts + " alerte(s) à traiter";
        };
        g.drawString(font, font.plainSubstrByWidth(head, 230), left + 140, top + 24, CYAN, false);

        g.fill(left + 10, top + 55, left + W - 28, top + 56 + ROWS * ROW_H + 1, 0xFF0E0A34);
        offset = Math.max(0, Math.min(offset, Math.max(0, rows.size() - ROWS)));
        for (int i = 0; i < ROWS; i++) {
            int idx = offset + i;
            if (idx >= rows.size()) break;
            Row r = rows.get(idx);
            int y = top + 56 + i * ROW_H;
            boolean sel = r.id().equals(selected);
            if (sel) g.fill(left + 10, y, left + W - 28, y + ROW_H, CYAN);
            else if (mx >= left + 10 && mx < left + W - 28 && my >= y && my < y + ROW_H) g.fill(left + 10, y, left + W - 28, y + ROW_H, 0xFF2E2480);
            int color = sel ? 0xFFFFFFFF : r.color() == 2 ? ALERT : r.color() == 1 ? WARN : 0xFFFFFFFF;
            String right = r.right();
            int rw = font.width(right);
            g.drawString(font, right, left + W - 32 - rw, y + 2, color, false);
            g.drawString(font, font.plainSubstrByWidth(r.left(), W - 50 - rw), left + 13, y + 2, color, false);
        }
        if (rows.isEmpty()) g.drawString(font, "Rien à afficher.", left + 14, top + 60, TEXT, false);

        Row sel = selectedRow();
        if (sel == null) {
            g.drawString(font, "Sélectionnez une ligne pour voir le détail.", left + 10, top + 134, TEXT, false);
        } else {
            int y = top + 134;
            for (String line : sel.detail()) {
                if (y > top + 205) break;
                g.drawString(font, font.plainSubstrByWidth(line, 360), left + 10, y, TEXT, false);
                y += 9;
            }
        }
    }
}
