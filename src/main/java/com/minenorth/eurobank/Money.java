package com.minenorth.eurobank;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class Money {
    private Money() {}

    public static String format(long cents) {
        long a = Math.abs(cents);
        return (cents < 0 ? "-" : "") + (a / 100) + "," + String.format("%02d", a % 100) + " €";
    }

    /** Parse "12", "12,5", "12.50" -> cents, or -1 if invalid. */
    public static long parseEuros(String s) {
        try {
            s = s.trim().replace(',', '.').replace("€", "").trim();
            if (s.isEmpty()) return -1;
            long c = new BigDecimal(s).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
            return c > 0 ? c : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    public static long value(ItemStack s) {
        return s.getItem() instanceof MoneyItem m ? m.cents * s.getCount() : 0;
    }

    public static long cashIn(Player p) {
        Inventory inv = p.getInventory();
        long t = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) t += value(inv.getItem(i));
        return t;
    }

    public static long takeAllCash(Player p) {
        Inventory inv = p.getInventory();
        long t = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            long v = value(inv.getItem(i));
            if (v > 0) {
                t += v;
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        return t;
    }

    public static List<ItemStack> change(long cents) {
        List<ItemStack> out = new ArrayList<>();
        for (Denomination d : Denomination.DESCENDING) {
            long n = cents / d.cents;
            cents %= d.cents;
            while (n > 0) {
                int c = (int) Math.min(n, 64);
                out.add(new ItemStack(ModItems.MONEY.get(d).get(), c));
                n -= c;
            }
        }
        return out;
    }

    public static void giveCash(Player p, long cents) {
        for (ItemStack s : change(cents)) giveStack(p, s);
    }

    public static void giveStack(Player p, ItemStack s) {
        p.getInventory().add(s);
        if (!s.isEmpty()) p.drop(s, false);
    }
}
