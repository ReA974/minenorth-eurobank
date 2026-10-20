package com.minenorth_eurobank.client;

import com.minenorth_eurobank.EuroBank;
import com.minenorth_eurobank.Money;
import com.minenorth_eurobank.loan.LoanService;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Relevé de compte : solde, entrées/sorties sur 30 jours, dépenses des 7 derniers jours et liste des dernières opérations. */
@OnlyIn(Dist.CLIENT)
public class StatementScreen extends Screen {
    private static final int W = 340, H = 252, ROWS = 7, ROW_H = 13;
    private static final int LIST_Y = 150;
    private static final int BLUE = 0xFF161048;
    private static final int PANEL = 0xFF0E0A34;
    private static final int CYAN = AtmButton.CYAN;
    private static final int TEXT = 0xFFCFE3FF;
    private static final int GREEN = 0xFF5DE08A;
    private static final int RED = 0xFFFF6A9A;
    private static final int WARN = 0xFFFFE066;
    private static final ResourceLocation LOGO = new ResourceLocation(EuroBank.MODID, "textures/gui/logo.png");

    private record Tx(long time, long cents, long after, String label, String cat) {}

    @Nullable
    private final Screen parent;
    private final String name;
    private final boolean business, frozen, loanLate;
    private final long balance, now, in30, out30, biggest, loanLeft, loanDue;
    private final long[] days;
    private final int count;
    private final List<Tx> txs = new ArrayList<>();
    private int offset;
    private int left, top;

    public StatementScreen(CompoundTag t, @Nullable Screen parent) {
        super(Component.literal("Relevé de compte"));
        this.parent = parent;
        name = t.getString("name");
        business = t.getBoolean("business");
        frozen = t.getBoolean("frozen");
        balance = t.getLong("balance");
        now = t.getLong("now");
        in30 = t.getLong("in30");
        out30 = t.getLong("out30");
        biggest = t.getLong("biggest");
        loanLeft = t.getLong("loanLeft");
        loanDue = t.getLong("loanDue");
        loanLate = t.getBoolean("loanLate");
        days = t.getLongArray("days").length == 7 ? t.getLongArray("days") : new long[7];
        count = t.getInt("count");
        ListTag list = t.getList("tx", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            txs.add(new Tx(c.getLong("t"), c.getLong("c"), c.getLong("b"), c.getString("l"), c.getString("k")));
        }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(new AtmButton(left + W - 80, top + 8, 66, 14, Component.literal(parent != null ? "Retour" : "Fermer"),
                AtmButton.GHOST, this::onClose));
        addRenderableWidget(new AtmButton(left + W - 24, top + LIST_Y, 14, 44, Component.literal("^"), AtmButton.DARK,
                () -> offset = Math.max(0, offset - 1)));
        addRenderableWidget(new AtmButton(left + W - 24, top + LIST_Y + 48, 14, 43, Component.literal("v"), AtmButton.DARK,
                () -> offset = Math.min(Math.max(0, txs.size() - ROWS), offset + 1)));
    }

    public Screen parentScreen() {
        return parent;
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
        offset = Math.max(0, Math.min(Math.max(0, txs.size() - ROWS), offset - (int) Math.signum(delta)));
        return true;
    }

    /** Texte réduit pour tenir dans {@code maxWidth}, jamais agrandi au-delà de {@code maxScale}. */
    private void fitted(GuiGraphics g, Component c, int x, int y, int maxWidth, float maxScale, int color) {
        float sc = Math.min(maxScale, maxWidth / (float) Math.max(1, font.width(c)));
        g.pose().pushPose();
        g.pose().scale(sc, sc, 1f);
        g.drawString(font, c, Math.round(x / sc), Math.round(y / sc), color, false);
        g.pose().popPose();
    }

    private void card(GuiGraphics g, int x, int y, int w, String title, String value, int color) {
        g.fill(x, y, x + w, y + 36, PANEL);
        g.fill(x, y, x + 2, y + 36, color);
        g.drawString(font, font.plainSubstrByWidth(title, w - 10), x + 7, y + 5, CYAN, false);
        fitted(g, Component.literal(value).withStyle(ChatFormatting.BOLD), x + 7, y + 20, w - 12, 1.25f, color);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(left - 3, top - 3, left + W + 3, top + H + 3, 0xFF0E0E10);
        g.fill(left, top, left + W, top + H, BLUE);

        RenderSystem.enableBlend();
        g.blit(LOGO, left + 10, top + 5, 24, 24, 0, 0, 96, 96, 96, 96);
        g.pose().pushPose();
        g.pose().scale(1.4f, 1.4f, 1f);
        g.drawString(font, Component.literal("RELEVÉ DE COMPTE").withStyle(ChatFormatting.BOLD),
                Math.round((left + 40) / 1.4f), Math.round((top + 8) / 1.4f), 0xFFFFFFFF, false);
        g.pose().popPose();
        g.drawString(font, font.plainSubstrByWidth((business ? "Entreprise : " : "") + name, 150) + "  -  " + LoanService.date(now),
                left + 40, top + 24, TEXT, false);

        // cartes de synthèse (valeurs réduites si elles sont longues)
        card(g, left + 10, top + 40, 104, "Solde", Money.format(balance), frozen ? RED : 0xFFFFFFFF);
        card(g, left + 120, top + 40, 104, "Entrées 30 j", "+" + Money.format(in30), GREEN);
        card(g, left + 230, top + 40, 104, "Sorties 30 j", "-" + Money.format(out30), RED);
        if (frozen) g.drawString(font, "COMPTE GELÉ", left + 12, top + 78, RED, false);

        // dépenses des 7 derniers jours
        g.fill(left + 10, top + 88, left + 176, top + 138, PANEL);
        g.drawString(font, font.plainSubstrByWidth("Dépenses sur 7 jours", 158), left + 14, top + 92, CYAN, false);
        long max = 1;
        for (long v : days) max = Math.max(max, v);
        for (int i = 0; i < 7; i++) {
            int x = left + 16 + i * 22;
            int h = (int) Math.round(24.0 * days[i] / max);
            g.fill(x, top + 134 - 24, x + 16, top + 134, 0xFF1B1560);
            if (days[i] > 0) g.fill(x, top + 134 - Math.max(1, h), x + 16, top + 134, i == 6 ? CYAN : 0xFF4A3CB4);
        }
        // résumé
        g.fill(left + 182, top + 88, left + 334, top + 138, PANEL);
        g.drawString(font, "Plus grosse dépense", left + 187, top + 92, CYAN, false);
        fitted(g, Component.literal(Money.format(biggest)), left + 187, top + 103, 142, 1.0f, 0xFFFFFFFF);
        g.drawString(font, font.plainSubstrByWidth(count + " opération(s)", 142), left + 187, top + 116, TEXT, false);
        String loan = loanLeft > 0 ? "Prêt : " + Money.format(loanLeft) + " restant" : "Aucun prêt en cours";
        g.drawString(font, font.plainSubstrByWidth(loan, 142), left + 187, top + 127, loanLeft > 0 ? (loanLate ? RED : WARN) : TEXT, false);

        // liste des opérations
        g.drawString(font, "Dernières opérations", left + 10, top + 140, CYAN, false);
        g.fill(left + 10, top + LIST_Y, left + W - 28, top + LIST_Y + ROWS * ROW_H + 2, PANEL);
        offset = Math.max(0, Math.min(offset, Math.max(0, txs.size() - ROWS)));
        for (int i = 0; i < ROWS; i++) {
            int idx = offset + i;
            if (idx >= txs.size()) break;
            Tx t = txs.get(idx);
            int y = top + LIST_Y + 1 + i * ROW_H;
            if (i % 2 == 1) g.fill(left + 10, y - 1, left + W - 28, y + ROW_H - 1, 0x20FFFFFF);
            String date = LoanService.date(t.time());
            g.drawString(font, date.substring(0, 5) + " " + date.substring(11), left + 13, y + 2, 0xFF8FA8E0, false);
            String amount = (t.cents() > 0 ? "+" : "") + Money.format(t.cents());
            int aw = font.width(amount);
            g.drawString(font, amount, left + W - 32 - aw, y + 2, t.cents() >= 0 ? GREEN : RED, false);
            String label = t.label().isEmpty() ? label(t.cat()) : t.label();
            g.drawString(font, font.plainSubstrByWidth(label, W - 32 - aw - 100), left + 96, y + 2, 0xFFFFFFFF, false);
        }
        if (txs.isEmpty()) g.drawString(font, "Aucune opération enregistrée.", left + 14, top + LIST_Y + 4, TEXT, false);
        super.render(g, mx, my, pt);
    }

    private static String label(String cat) {
        return switch (cat) {
            case "DEPOSIT" -> "Dépôt";
            case "WITHDRAW" -> "Retrait";
            case "SALARY" -> "Salaire";
            case "PAYMENT" -> "Paiement";
            case "INCOME" -> "Encaissement";
            case "ADMIN" -> "Opération administrative";
            default -> "Opération";
        };
    }
}
