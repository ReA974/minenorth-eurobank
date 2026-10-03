package com.minenorth_eurobank.client;

import com.minenorth_eurobank.packet.BankerListPacket;
import com.minenorth_eurobank.packet.BankerListPacket.Row;
import com.minenorth_eurobank.packet.BankerPacket;
import com.minenorth_eurobank.EuroBank;
import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.loan.LoanService;
import com.minenorth_eurobank.Money;
import com.minenorth_eurobank.Network;
import com.minenorth_eurobank.packet.WatchPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Écran du banquier : demandes de prêt, prêts en cours, capital disponible et grille des taux par durée. */
@OnlyIn(Dist.CLIENT)
public class BankerScreen extends Screen {
    private static final int W = 300, H = 196, ROWS = 5, ROW_H = 14;
    private static final int BLUE = 0xFF161048;
    private static final ResourceLocation LOGO = new ResourceLocation(EuroBank.MODID, "textures/gui/logo.png");
    private static final int CYAN = AtmButton.CYAN;
    private static final int TEXT = 0xFFCFE3FF;
    private static final int WARN = 0xFFFFE066;
    private static final int ALERT = 0xFFFF6A9A;
    private static final int N_TERMS = LoanService.TERMS.length;

    @Nullable
    private final Screen parent;
    private List<Row> rows;
    private int[] termRates;
    private long reserve;
    private String message;
    private String rateText = "5";
    private String[] termTexts;
    private EditBox rateBox;
    private EditBox[] termBoxes = new EditBox[N_TERMS];
    private boolean ratesPage;
    private UUID selected;
    private int offset;
    private int left, top;
    private boolean watching;

    public BankerScreen(BankerListPacket m, @Nullable Screen parent) {
        super(Component.literal("Banquier"));
        this.parent = parent;
        this.rows = m.rows;
        this.reserve = m.reserve;
        this.message = m.message;
        this.termRates = m.termRates;
        this.termTexts = fromRates(m.termRates);
    }

    private static String[] fromRates(int[] rates) {
        String[] t = new String[N_TERMS];
        for (int i = 0; i < N_TERMS; i++) t[i] = fmtInput(i < rates.length ? rates[i] : 0);
        return t;
    }

    private static String fmtInput(int bp) {
        return bp % 100 == 0 ? String.valueOf(bp / 100) : String.format(Locale.ROOT, "%.2f", bp / 100.0);
    }

    public void update(BankerListPacket m) {
        Row before = selectedRow();
        Byte was = before == null ? null : before.status();
        boolean focus = rateBox != null && rateBox.isFocused();
        this.rows = m.rows;
        this.reserve = m.reserve;
        this.termRates = m.termRates;
        if (!ratesPage) this.termTexts = fromRates(m.termRates);   // on n'écrase pas une saisie en cours
        if (!m.message.isEmpty()) this.message = m.message;
        boolean rebuild = false;
        if (selected != null && rows.stream().noneMatch(r -> r.id().equals(selected))) {
            selected = null;
            rebuild = true;
        }
        Row after = selectedRow();
        Byte now = after == null ? null : after.status();
        if (!java.util.Objects.equals(was, now)) rebuild = true;
        if (rebuild) {
            if (rateBox != null) rateText = rateBox.getValue();
            rebuildWidgets();
            if (focus && rateBox != null) setFocused(rateBox);
        }
    }

    private Row selectedRow() {
        if (selected == null) return null;
        for (Row r : rows) if (r.id().equals(selected)) return r;
        return null;
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

    private void act(BankerPacket.Op op) {
        if (selected == null) return;
        int bp = op == BankerPacket.Op.APPROVE ? parseRateBp(rateBox.getValue()) : 0;
        Network.CHANNEL.sendToServer(new BankerPacket(op, selected, bp));
    }

    private void saveRates() {
        int[] r = new int[N_TERMS];
        for (int i = 0; i < N_TERMS; i++) {
            int bp = parseRateBp(termBoxes[i] != null ? termBoxes[i].getValue() : termTexts[i]);
            if (bp < 0) {
                message = "Taux invalide (0 à " + (LoanService.MAX_RATE_BP / 100) + " %).";
                return;
            }
            r[i] = bp;
        }
        Network.CHANNEL.sendToServer(new BankerPacket(BankerPacket.Op.SET_RATES, new UUID(0, 0), 0, r));
    }

    private void togglePage() {
        if (rateBox != null) rateText = rateBox.getValue();
        for (int i = 0; i < N_TERMS; i++) if (termBoxes[i] != null) termTexts[i] = termBoxes[i].getValue();
        ratesPage = !ratesPage;
        if (ratesPage) termTexts = fromRates(termRates);
        message = "";
        rebuildWidgets();
    }

    @Override
    protected void init() {
        if (!watching) {
            watching = true;
            Network.CHANNEL.sendToServer(new WatchPacket(WatchPacket.Kind.BANKER, true));
        }
        left = (width - W) / 2;
        top = (height - H) / 2;
        rateBox = null;
        termBoxes = new EditBox[N_TERMS];

        addRenderableWidget(new AtmButton(left + W - 112, top + 10, 98, 16,
                Component.literal(parent != null ? "Retour" : "Fermer"), AtmButton.GHOST, this::onClose));
        addRenderableWidget(new AtmButton(left + W - 112, top + 24, 98, 14,
                Component.literal(ratesPage ? "Dossiers" : "Taux"), AtmButton.GHOST, this::togglePage));

        if (ratesPage) {
            for (int i = 0; i < N_TERMS; i++) {
                int idx = i;
                EditBox b = new EditBox(font, left + 84, top + 62 + i * 18, 56, 16, Component.literal("Taux"));
                b.setMaxLength(6);
                b.setValue(termTexts[i]);
                b.setResponder(s -> termTexts[idx] = s);
                termBoxes[i] = b;
                addRenderableWidget(b);
            }
            addRenderableWidget(new AtmButton(left + 16, top + 157, 130, 20, Component.literal("Enregistrer la grille"), CYAN, this::saveRates));
            return;
        }

        Row sel = selectedRow();
        boolean pending = sel != null && sel.status() == Loan.PENDING;
        boolean running = sel != null && (sel.status() == Loan.ACTIVE || sel.status() == Loan.OVERDUE);

        rateBox = new EditBox(font, left + 16, top + 158, 46, 18, Component.literal("Taux"));
        rateBox.setHint(Component.literal("Taux %"));
        rateBox.setValue(rateText);
        addRenderableWidget(rateBox);

        addRenderableWidget(new AtmButton(left + 66, top + 157, 64, 20, Component.literal("Accepter"), CYAN,
                () -> act(BankerPacket.Op.APPROVE)).enabled(pending));
        addRenderableWidget(new AtmButton(left + 134, top + 157, 64, 20, Component.literal("Refuser"), AtmButton.PINK,
                () -> act(BankerPacket.Op.REJECT)).enabled(pending));
        addRenderableWidget(new AtmButton(left + 202, top + 157, 82, 20, Component.literal("Annuler dette"), AtmButton.DARK,
                () -> act(BankerPacket.Op.FORGIVE)).enabled(running));

        addRenderableWidget(new AtmButton(left + 270, top + 62, 14, 34, Component.literal("^"), AtmButton.DARK,
                () -> offset = Math.max(0, offset - 1)));
        addRenderableWidget(new AtmButton(left + 270, top + 98, 14, 34, Component.literal("v"), AtmButton.DARK,
                () -> offset = Math.min(Math.max(0, rows.size() - ROWS), offset + 1)));
    }

    @Override
    public void removed() {
        watching = false;
        Network.CHANNEL.sendToServer(new WatchPacket(WatchPacket.Kind.BANKER, false));
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
        if (ratesPage) return super.mouseScrolled(mx, my, delta);
        int max = Math.max(0, rows.size() - ROWS);
        offset = Math.max(0, Math.min(max, offset - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (ratesPage) return false;
        for (int i = 0; i < ROWS; i++) {
            int idx = offset + i;
            int y = top + 62 + i * ROW_H;
            if (idx < rows.size() && mx >= left + 16 && mx < left + 266 && my >= y && my < y + ROW_H) {
                Row r = rows.get(idx);
                selected = r.id();
                // pré-remplit avec le taux annoncé au joueur pour cette durée
                rateText = r.status() == Loan.PENDING ? fmtInput(r.rateBp()) : (rateBox != null ? rateBox.getValue() : rateText);
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    private Component bold(String s) {
        return Component.literal(s).withStyle(ChatFormatting.BOLD);
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
        g.drawString(font, bold("BANQUIER"), Math.round((left + 40) / 1.8f), Math.round((top + 12) / 1.8f), 0xFFFFFFFF, false);
        g.pose().popPose();
        g.drawString(font, "Capital disponible : " + Money.format(reserve), left + 16, top + 34, TEXT, false);

        if (ratesPage) {
            renderRates(g);
        } else {
            renderLoans(g, mx, my);
        }
        if (message != null && !message.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(message, 268), left + 16, top + 180, WARN, false);
        }
        super.render(g, mx, my, pt);
    }

    private void renderRates(GuiGraphics g) {
        g.drawString(font, "Taux par durée (annoncé aux clients)", left + 16, top + 46, CYAN, false);
        for (int i = 0; i < N_TERMS; i++) {
            int days = LoanService.TERMS[i];
            int y = top + 62 + i * 18;
            g.drawString(font, days == 1 ? "1 jour" : days + " jours", left + 16, y + 4, 0xFFFFFFFF, false);
            g.drawString(font, "%", left + 144, y + 4, TEXT, false);
            int bp = parseRateBp(termBoxes[i] != null ? termBoxes[i].getValue() : termTexts[i]);
            if (bp < 0) g.drawString(font, "invalide", left + 160, y + 4, ALERT, false);
        }
    }

    private void renderLoans(GuiGraphics g, int mx, int my) {
        long pendingCount = rows.stream().filter(r -> r.status() == Loan.PENDING).count();
        g.drawString(font, pendingCount + " demande(s) en attente  -  " + (rows.size() - pendingCount) + " prêt(s) en cours",
                left + 16, top + 46, CYAN, false);

        g.fill(left + 16, top + 61, left + 266, top + 62 + ROWS * ROW_H + 1, 0xFF0E0A34);
        offset = Math.max(0, Math.min(offset, Math.max(0, rows.size() - ROWS)));
        for (int i = 0; i < ROWS; i++) {
            int idx = offset + i;
            if (idx >= rows.size()) break;
            Row r = rows.get(idx);
            int y = top + 62 + i * ROW_H;
            boolean sel = r.id().equals(selected);
            if (sel) g.fill(left + 16, y, left + 266, y + ROW_H, CYAN);
            else if (mx >= left + 16 && mx < left + 266 && my >= y && my < y + ROW_H) g.fill(left + 16, y, left + 266, y + ROW_H, 0xFF2E2480);
            g.drawString(font, font.plainSubstrByWidth(r.name(), 100), left + 20, y + 3, 0xFFFFFFFF, false);
            String label;
            int color;
            if (r.status() == Loan.PENDING) {
                label = "demande " + Money.format(r.principal());
                color = sel ? 0xFFFFFFFF : WARN;
            } else if (r.status() == Loan.OVERDUE) {
                label = "RETARD " + Money.format(Math.max(0, r.totalDue() - r.repaid()));
                color = sel ? 0xFFFFFFFF : ALERT;
            } else {
                label = "reste " + Money.format(Math.max(0, r.totalDue() - r.repaid()));
                color = 0xFFFFFFFF;
            }
            g.drawString(font, label, left + 262 - font.width(label), y + 3, color, false);
        }
        if (rows.isEmpty()) g.drawString(font, "Aucune demande ni prêt en cours.", left + 20, top + 66, TEXT, false);

        Row sel = selectedRow();
        if (sel == null) {
            g.drawString(font, "Sélectionnez un dossier.", left + 16, top + 138, TEXT, false);
        } else if (sel.status() == Loan.PENDING) {
            g.drawString(font, "Demande : " + Money.format(sel.principal()) + " sur " + sel.termDays() + " jours",
                    left + 16, top + 134, TEXT, false);
            long after = reserve - sel.principal();
            g.drawString(font, after >= 0 ? "Capital après prêt : " + Money.format(after) : "Capital insuffisant pour ce prêt",
                    left + 16, top + 144, after >= 0 ? TEXT : ALERT, false);
        } else {
            g.drawString(font, "Total " + Money.format(sel.totalDue()) + " (taux " + LoanService.rateLabel(sel.rateBp())
                    + "), payé " + Money.format(sel.repaid()), left + 16, top + 134, TEXT, false);
            g.drawString(font, "Échéance : " + LoanService.date(sel.dueMs()), left + 16, top + 144,
                    sel.status() == Loan.OVERDUE ? ALERT : TEXT, false);
        }
    }
}
