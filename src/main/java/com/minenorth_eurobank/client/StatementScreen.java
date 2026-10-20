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
    private static final int W = 330, H = 246, ROWS = 8, ROW_H = 12;
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
        addRenderableWidget(new AtmButton(left + W - 24, top + 138, 14, 40, Component.literal("^"), AtmButton.DARK,
                () -> offset = Math.max(0, offset - 1)));
        addRenderableWidget(new AtmButton(left + W - 24, top + 182, 14, 40, Component.literal("v"), AtmButton.DARK,
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

    private void card(GuiGraphics g, int x, int y, int w, String title, String value, int color) {
        g.fill(x, y, x + w, y + 34, PANEL);
        g.fill(x, y, x + 2, y + 34, color);
        g.drawString(font, title, x + 7, y + 5, CYAN, false);
        g.pose().pushPose();
        g.pose().scale(1.25f, 1.25f, 1f);
        g.drawString(font, Component.literal(font.plainSubstrByWidth(value, (int) ((w - 12) / 1.25f))).withStyle(ChatFormatting.BOLD),
                Math.round((x + 7) / 1.25f), Math.round((y + 18) / 1.25f), color, false);
        g.pose().popPose();
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
        g.drawString(font, Component.literal("RELEVÉ DE COMPTE").withStyle(ChatFormatting.BOLD),
                Math.round((left + 40) / 1.5f), Math.round((top + 8) / 1.5f), 0xFFFFFFFF, false);
        g.pose().popPose();
        g.drawString(font, font.plainSubstrByWidth((business ? "Entreprise : " : "") + name, 200) + "  -  " + LoanService.date(now),
                left + 40, top + 24, TEXT, false);

        // cartes de synthèse
        card(g, left + 10, top + 38, 104, "Solde", Money.format(balance), frozen ? RED : 0xFFFFFFFF);
        card(g, left + 120, top + 38, 98, "Entrées (30 j)", "+" + Money.format(in30), GREEN);
        card(g, left + 224, top + 38, 96, "Sorties (30 j)", "-" + Money.format(out30), RED);
        if (frozen) g.drawString(font, "COMPTE GELÉ", left + 12, top + 76, RED, false);

        // dépenses des 7 derniers jours
        g.fill(left + 10, top + 86, left + 176, top + 130, PANEL);
        g.drawString(font, "Dépenses des 7 derniers jours", left + 14, top + 89, CYAN, false);
        long max = 1;
        for (long v : days) max = Math.max(max, v);
        for (int i = 0; i < 7; i++) {
            int x = left + 16 + i * 22;
            int h = (int) Math.round(22.0 * days[i] / max);
            g.fill(x, top + 126 - 22, x + 16, top + 126, 0xFF1B1560);
            if (days[i] > 0) g.fill(x, top + 126 - Math.max(1, h), x + 16, top + 126, i == 6 ? CYAN : 0xFF4A3CB4);
        }
        g.fill(left + 182, top + 86, left + 320, top + 130, PANEL);
        g.drawString(font, "Plus grosse dépense : " + Money.format(biggest), left + 186, top + 90, TEXT, false);
        g.drawString(font, count + " opération(s) enregistrée(s)", left + 186, top + 102, TEXT, false);
        if (loanLeft > 0) {
            g.drawString(font, "Prêt : " + Money.format(loanLeft) + " restant", left + 186, top + 114, loanLate ? RED : WARN, false);
        } else {
            g.drawString(font, "Aucun prêt en cours", left + 186, top + 114, TEXT, false);
        }

        // liste des opérations
        g.drawString(font, "Dernières opérations", left + 10, top + 128 + 6, CYAN, false);
        g.fill(left + 10, top + 138, left + W - 28, top + 138 + ROWS * ROW_H + 2, PANEL);
        offset = Math.max(0, Math.min(offset, Math.max(0, txs.size() - ROWS)));
        for (int i = 0; i < ROWS; i++) {
            int idx = offset + i;
            if (idx >= txs.size()) break;
            Tx t = txs.get(idx);
            int y = top + 139 + i * ROW_H;
            if (i % 2 == 1) g.fill(left + 10, y - 1, left + W - 28, y + ROW_H - 1, 0x20FFFFFF);
            String date = LoanService.date(t.time());
            g.drawString(font, date.substring(0, 5) + " " + date.substring(11), left + 13, y + 1, 0xFF8FA8E0, false);
            String amount = (t.cents() > 0 ? "+" : "") + Money.format(t.cents());
            int aw = font.width(amount);
            g.drawString(font, amount, left + W - 32 - aw, y + 1, t.cents() >= 0 ? GREEN : RED, false);
            g.drawString(font, font.plainSubstrByWidth(t.label().isEmpty() ? label(t.cat()) : t.label(), W - 32 - aw - 84),
                    left + 62, y + 1, 0xFFFFFFFF, false);
        }
        if (txs.isEmpty()) g.drawString(font, "Aucune opération enregistrée.", left + 14, top + 144, TEXT, false);
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
