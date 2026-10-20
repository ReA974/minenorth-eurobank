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
    private static final int W = 380, H = 250, ROWS = 5, ROW_H = 12;
    private static final int[] REMIND_MIN = {60, 1440, 4320, 10080};
    private static final String[] REMIND_LABEL = {"1 h", "1 j", "3 j", "7 j"};
    private static final int[] WAIVE_PCT = {25, 50, 100};
    private static final int BLUE = 0xFF161048;
    private static final int CYAN = AtmButton.CYAN;
    private static final int TEXT = 0xFFCFE3FF;
    private static final int WARN = 0xFFFFE066;
    private static final int ALERT = 0xFFFF6A9A;
    private static final int N_TERMS = LoanService.TERMS.length;
    private static final String[] TAB_NAMES = {"Prêts", "Comptes", "Opér.", "Alertes", "Contacts", "Rapport", "Taux"};
    private static final ResourceLocation LOGO = new ResourceLocation(EuroBank.MODID, "textures/gui/logo.png");

    private record Row(UUID id, String left, String right, int color, UUID account, int status, int rate,
                       boolean frozen, boolean done, List<String> detail) {}

    private int tab;
    private long reserve, threshold;
    private int openAlerts, openContacts;
    private boolean granting;
    private String searchText = "", grantAmountText = "", grantRateText = "";
    private int grantTerm = 7, remindIdx = 1, waiveIdx = 1;
    private EditBox searchBox, grantAmountBox, grantRateBox;
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
        openContacts = t.getInt("contacts");
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
        return tab + "|" + selected + "|" + (s == null ? "" : s.status() + "" + s.frozen() + s.done()) + "|" + filterName + "|" + openAlerts + "|" + openContacts + "|" + granting;
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
        if (grantAmountBox != null) grantAmountText = grantAmountBox.getValue();
        if (grantRateBox != null) grantRateText = grantRateBox.getValue();
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
        granting = false;
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

    /** Lignes affichées : tout, ou seulement les noms contenant la recherche (onglet Comptes). */
    private List<Row> shown() {
        if (tab != TabletService.TAB_ACCOUNTS || searchText.isBlank()) return rows;
        String q = searchText.strip().toLowerCase(Locale.ROOT);
        List<Row> out = new ArrayList<>();
        for (Row r : rows) if (r.left().toLowerCase(Locale.ROOT).contains(q)) out.add(r);
        return out;
    }

    /** Formulaire « accorder un prêt » à la place du détail du compte sélectionné. */
    private void initGrant(int by) {
        grantAmountBox = new EditBox(font, left + 80, top + 134, 80, 16, Component.literal("Montant"));
        grantAmountBox.setHint(Component.literal("Montant en €"));
        grantAmountBox.setMaxLength(12);
        grantAmountBox.setValue(grantAmountText);
        addRenderableWidget(grantAmountBox);
        for (int i = 0; i < N_TERMS; i++) {
            int days = LoanService.TERMS[i];
            addRenderableWidget(new AtmButton(left + 80 + i * 40, top + 154, 37, 16, Component.literal(days + " j"),
                    days == grantTerm ? CYAN : AtmButton.DARK, () -> {
                        saveTexts();
                        grantTerm = days;
                        grantRateText = fmtInput(termRates[Math.max(0, LoanService.termIndex(days))]);
                        rebuildWidgets();
                    }));
        }
        grantRateBox = new EditBox(font, left + 80, top + 174, 50, 16, Component.literal("Taux"));
        grantRateBox.setMaxLength(6);
        grantRateBox.setValue(grantRateText);
        addRenderableWidget(grantRateBox);
        addRenderableWidget(new AtmButton(left + 10, by, 130, 18, Component.literal("Accorder maintenant"), CYAN, () -> submitGrant(TabletOp.GRANT)));
        addRenderableWidget(new AtmButton(left + 144, by, 130, 18, Component.literal("Envoyer en offre"), AtmButton.DARK, () -> submitGrant(TabletOp.OFFER)));
        addRenderableWidget(new AtmButton(left + 278, by, 80, 18, Component.literal("Annuler"), AtmButton.DARK, () -> {
            granting = false;
            lastSig = "";
            rebuildWidgets();
        }));
    }

    /** Rappel programmé : un bouton pour choisir le délai, un pour valider ; le serveur prévient à l'échéance. */
    private void remindButtons(int y, int x, boolean enabled) {
        addRenderableWidget(new AtmButton(left + x, y, 62, 18, Component.literal("Rappel " + REMIND_LABEL[remindIdx]),
                AtmButton.DARK, () -> {
                    remindIdx = (remindIdx + 1) % REMIND_MIN.length;
                    rebuildWidgets();
                }));
        addRenderableWidget(new AtmButton(left + x + 66, y, 82, 18, Component.literal("Programmer"), CYAN,
                () -> send(TabletOp.REMIND, selected, REMIND_MIN[remindIdx])).enabled(enabled));
    }

    private void submitGrant(TabletOp op) {
        saveTexts();
        long amount = Money.parseEuros(grantAmountText);
        int bp = parseRateBp(grantRateText);
        if (amount <= 0) {
            message = "Indiquez le montant du prêt.";
            return;
        }
        if (bp < 0) {
            message = "Taux invalide.";
            return;
        }
        granting = false;
        lastSig = "";
        Network.CHANNEL.sendToServer(TabletPacket.text(op, selected, grantTerm + ";" + bp, amount));
    }

    private void renderGrant(GuiGraphics g, Row sel) {
        g.drawString(font, "Accorder un prêt à " + sel.left().replace("(en ligne) ", ""), left + 10, top + 122, CYAN, false);
        g.drawString(font, "Montant", left + 10, top + 138, TEXT, false);
        g.drawString(font, "Durée", left + 10, top + 158, TEXT, false);
        g.drawString(font, "Taux (%)", left + 10, top + 178, TEXT, false);
        long amount = Money.parseEuros(grantAmountBox == null ? "" : grantAmountBox.getValue());
        int bp = parseRateBp(grantRateBox == null ? "" : grantRateBox.getValue());
        if (amount > 0 && bp >= 0) {
            g.drawString(font, "À rembourser : " + Money.format(amount + LoanService.interest(amount, bp)),
                    left + 140, top + 178, 0xFFFFFFFF, false);
        }
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
        rateBox = noteBox = thresholdBox = searchBox = grantAmountBox = grantRateBox = null;
        for (int i = 0; i < N_TERMS; i++) termBoxes[i] = null;

        addRenderableWidget(new AtmButton(left + W - 80, top + 8, 66, 14, Component.literal("Fermer"), AtmButton.GHOST, this::onClose));
        int x = left + 10;
        int[] widths = {38, 48, 38, 70, 76, 50, 34};
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int idx = i;
            int badge = i == TabletService.TAB_ALERTS ? openAlerts : i == TabletService.TAB_CONTACTS ? openContacts : 0;
            String label = TAB_NAMES[i] + (badge > 0 ? " (" + badge + ")" : "");
            addRenderableWidget(new AtmButton(x, top + 34, widths[i] - 3, 16, Component.literal(label),
                    i == tab ? CYAN : AtmButton.DARK, () -> {
                        granting = false;
                        view(idx, idx == TabletService.TAB_REPORT ? new UUID(0L, 1L) : null, null);
                    }));
            x += widths[i];
        }

        addRenderableWidget(new AtmButton(left + W - 24, top + 56, 14, 28, Component.literal("^"), AtmButton.DARK,
                () -> offset = Math.max(0, offset - 1)));
        addRenderableWidget(new AtmButton(left + W - 24, top + 86, 14, 28, Component.literal("v"), AtmButton.DARK,
                () -> offset = Math.min(Math.max(0, shown().size() - ROWS), offset + 1)));

        Row sel = selectedRow();
        int by = top + 196, by2 = top + 216;
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
                boolean offered = sel != null && sel.status() == Loan.OFFERED;
                addRenderableWidget(new AtmButton(left + 120, by, 54, 18, Component.literal(offered ? "Retirer" : "Refuser"), AtmButton.PINK,
                        () -> send(TabletOp.REJECT, selected, 0)).enabled(pending || offered));
                addRenderableWidget(new AtmButton(left + 178, by, 76, 18, Component.literal("Annuler dette"), AtmButton.DARK,
                        () -> send(TabletOp.FORGIVE, selected, 0)).enabled(running));
                addRenderableWidget(new AtmButton(left + 258, by, 108, 18, Component.literal("Voir le compte"), AtmButton.DARK,
                        () -> view(TabletService.TAB_ACCOUNTS, sel.account(), null)).enabled(sel != null));
                // geste commercial sur les intérêts restants
                addRenderableWidget(new AtmButton(left + 10, by2, 76, 18, Component.literal("Geste " + WAIVE_PCT[waiveIdx] + " %"),
                        AtmButton.DARK, () -> {
                            waiveIdx = (waiveIdx + 1) % WAIVE_PCT.length;
                            rebuildWidgets();
                        }).enabled(running));
                addRenderableWidget(new AtmButton(left + 90, by2, 70, 18, Component.literal("Effacer"), CYAN,
                        () -> send(TabletOp.WAIVE, selected, WAIVE_PCT[waiveIdx])).enabled(running));
                remindButtons(by2, 168, sel != null && sel.status() <= Loan.OVERDUE);
            }
            case TabletService.TAB_ACCOUNTS -> {
                searchBox = new EditBox(font, left + 140, top + 18, 150, 14, Component.literal("Recherche"));
                searchBox.setHint(Component.literal("Rechercher un joueur..."));
                searchBox.setMaxLength(32);
                searchBox.setValue(searchText);
                searchBox.setResponder(t -> {
                    searchText = t;
                    offset = 0;
                });
                addRenderableWidget(searchBox);
                if (granting && sel != null) {
                    initGrant(by);
                    break;
                }
                boolean fz = sel != null && sel.frozen();
                addRenderableWidget(new AtmButton(left + 10, by, 54, 18, Component.literal(fz ? "Dégeler" : "Geler"),
                        fz ? CYAN : AtmButton.PINK, () -> send(TabletOp.FREEZE, selected, fz ? 0 : 1)).enabled(sel != null));
                addRenderableWidget(new AtmButton(left + 68, by, 44, 18, Component.literal("Prêt"), CYAN, () -> {
                    granting = true;
                    grantAmountText = "";
                    grantRateText = fmtInput(termRates[Math.max(0, LoanService.termIndex(grantTerm))]);
                    lastSig = "";
                    rebuildWidgets();
                }).enabled(sel != null));
                addRenderableWidget(new AtmButton(left + 116, by, 70, 18, Component.literal("Opérations"), AtmButton.DARK,
                        () -> view(TabletService.TAB_TX, null, selected)).enabled(sel != null));
                noteBox = new EditBox(font, left + 190, by, 110, 18, Component.literal("Note"));
                noteBox.setHint(Component.literal("Note privée"));
                noteBox.setMaxLength(TabletService.NOTE_MAX);
                noteBox.setValue(noteText);
                addRenderableWidget(noteBox);
                addRenderableWidget(new AtmButton(left + 304, by, 62, 18, Component.literal("Noter"), AtmButton.DARK,
                        () -> {
                            saveTexts();
                            Network.CHANNEL.sendToServer(TabletPacket.text(TabletOp.NOTE, selected, noteText));
                        }).enabled(sel != null));
                addRenderableWidget(new AtmButton(left + 10, by2, 110, 18, Component.literal("Relevé"), AtmButton.DARK,
                        () -> send(TabletOp.STATEMENT, selected, 0)).enabled(sel != null));
            }
            case TabletService.TAB_CONTACTS -> {
                addRenderableWidget(new AtmButton(left + 10, by, 70, 18, Component.literal("Traité"), CYAN,
                        () -> send(TabletOp.HANDLE_CONTACT, selected, 0)).enabled(sel != null && !sel.done()));
                addRenderableWidget(new AtmButton(left + 74, by, 90, 18, Component.literal("Voir le compte"), AtmButton.DARK,
                        () -> view(TabletService.TAB_ACCOUNTS, sel.account(), null)).enabled(sel != null));
                addRenderableWidget(new AtmButton(left + 168, by, 70, 18, Component.literal("Supprimer"), AtmButton.PINK,
                        () -> send(TabletOp.DELETE_CONTACT, selected, 0)).enabled(sel != null));
                addRenderableWidget(new AtmButton(left + 242, by, 100, 18, Component.literal("Vider traités"), AtmButton.DARK,
                        () -> send(TabletOp.CLEAR_CONTACTS, null, 0)));
                remindButtons(by2, 10, sel != null && !sel.done());
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
                addRenderableWidget(new AtmButton(left + 282, by, 84, 18, Component.literal("Vider traitées"), AtmButton.DARK,
                        () -> send(TabletOp.CLEAR_ALERTS, null, 0)));
            }
            case TabletService.TAB_REPORT -> { }
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
        int max = Math.max(0, shown().size() - ROWS);
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
            if (idx < shown().size() && mx >= left + 10 && mx < left + W - 28 && my >= y && my < y + ROW_H) {
                select(shown().get(idx));
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
        g.drawString(font, "Capital : " + Money.format(reserve), left + 140, top + 6, TEXT, false);

        if (tab == TabletService.TAB_RATES) renderRates(g);
        else renderList(g, mx, my);

        if (message != null && !message.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(message, 360), left + 10, top + 238, WARN, false);
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
            case TabletService.TAB_ACCOUNTS -> shown().size() + " compte(s)";
            case TabletService.TAB_TX -> filterName.isEmpty() ? "Dernières opérations de tous les comptes"
                    : "Opérations de " + filterName;
            case TabletService.TAB_CONTACTS -> openContacts + " demande(s) de rappel à traiter";
            case TabletService.TAB_REPORT -> "Rapports hebdomadaires de la banque";
            default -> openAlerts + " alerte(s) à traiter";
        };
        g.drawString(font, font.plainSubstrByWidth(head, tab == TabletService.TAB_ACCOUNTS ? 80 : 230), left + (tab == TabletService.TAB_ACCOUNTS ? 296 : 140), top + (tab == TabletService.TAB_ACCOUNTS ? 22 : 24), CYAN, false);

        g.fill(left + 10, top + 55, left + W - 28, top + 56 + ROWS * ROW_H + 1, 0xFF0E0A34);
        offset = Math.max(0, Math.min(offset, Math.max(0, shown().size() - ROWS)));
        for (int i = 0; i < ROWS; i++) {
            int idx = offset + i;
            if (idx >= shown().size()) break;
            Row r = shown().get(idx);
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
        if (shown().isEmpty()) g.drawString(font, "Rien à afficher.", left + 14, top + 60, TEXT, false);

        Row sel = selectedRow();
        if (granting && sel != null && tab == TabletService.TAB_ACCOUNTS) {
            renderGrant(g, sel);
        } else if (sel == null) {
            g.drawString(font, "Sélectionnez une ligne pour voir le détail.", left + 10, top + 122, TEXT, false);
        } else {
            int y = top + 122;
            for (String line : sel.detail()) {
                if (y > top + 192) break;
                g.drawString(font, font.plainSubstrByWidth(line, 360), left + 10, y, TEXT, false);
                y += 9;
            }
        }
    }
}
