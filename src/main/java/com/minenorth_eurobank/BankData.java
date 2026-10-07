package com.minenorth_eurobank;

import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.loan.LoanService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Comptes (un par joueur), capital de la banque, banquiers et prêts. Sauvegardé avec le monde. */
public class BankData extends SavedData {
    private static final String NAME = "eurobank_accounts";
    private static final int MAX_LOANS = 300;

    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Set<UUID> bankers = new HashSet<>();
    private final Map<UUID, Loan> loans = new LinkedHashMap<>();
    private long reserve;
    /** Taux (points de base) appliqué sur la durée du prêt, par durée, dans l'ordre de LoanService.TERMS : 1, 3, 7, 14, 30 jours. */
    private final int[] termRates = {100, 250, 500, 800, 1200};
    private boolean refreshPending;

    /** Serveur courant (non sauvegardé) : sert à lire l'identité RP pour les noms affichés. */
    private transient MinecraftServer server;

    public static BankData get(MinecraftServer server) {
        BankData d = server.overworld().getDataStorage().computeIfAbsent(BankData::load, BankData::new, NAME);
        d.server = server;
        return d;
    }

    public static BankData load(CompoundTag tag) {
        BankData d = new BankData();
        ListTag list = tag.getList("accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            UUID id = t.getUUID("id");
            d.balances.put(id, t.getLong("cents"));
            if (t.contains("name")) d.names.put(id, t.getString("name"));
        }
        d.reserve = tag.getLong("reserve");
        if (tag.contains("rates")) {
            int[] r = tag.getIntArray("rates");
            for (int i = 0; i < Math.min(r.length, d.termRates.length); i++) d.termRates[i] = r[i];
        }
        ListTag bl = tag.getList("bankers", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < bl.size(); i++) d.bankers.add(NbtUtils.loadUUID(bl.get(i)));
        ListTag ll = tag.getList("loans", Tag.TAG_COMPOUND);
        for (int i = 0; i < ll.size(); i++) {
            Loan l = Loan.load(ll.getCompound(i));
            d.loans.put(l.id, l);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        balances.forEach((id, c) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putLong("cents", c);
            String n = names.get(id);
            if (n != null) t.putString("name", n);
            list.add(t);
        });
        tag.put("accounts", list);
        tag.putLong("reserve", reserve);
        tag.putIntArray("rates", termRates);
        ListTag bl = new ListTag();
        for (UUID b : bankers) bl.add(NbtUtils.createUUID(b));
        tag.put("bankers", bl);
        ListTag ll = new ListTag();
        for (Loan l : loans.values()) ll.add(l.save());
        tag.put("loans", ll);
        return tag;
    }

    @Override
    public void setDirty(boolean dirty) {
        super.setDirty(dirty);
        if (dirty) refreshPending = true;
    }

    /** Vrai si quelque chose a changé depuis le dernier appel (sert à pousser les écrans ouverts). */
    public boolean consumeRefresh() {
        boolean r = refreshPending;
        refreshPending = false;
        return r;
    }

    // --- comptes
    public boolean has(UUID id) { return balances.containsKey(id); }

    public void open(UUID id) {
        if (balances.putIfAbsent(id, 0L) == null) setDirty();
    }

    public long balance(UUID id) { return balances.getOrDefault(id, 0L); }

    public void add(UUID id, long delta) {
        balances.merge(id, delta, Long::sum);
        setDirty();
    }

    public void set(UUID id, long v) {
        balances.put(id, v);
        setDirty();
    }

    public void rename(UUID id, String name) {
        if (name != null && !name.equals(names.get(id))) {
            names.put(id, name);
            setDirty();
        }
    }

    /** Nom affiché : « Prénom Nom » de la carte d'identité (règle MineNorth), sinon pseudo enregistré. */
    public String name(UUID id) {
        if (server != null) {
            var idt = fr.minenorth.api.MineNorth.identity().get(server, id);
            if (idt.isPresent()) return idt.get().fullName();
        }
        String n = names.get(id);
        return n != null ? n : id.toString().substring(0, 8);
    }

    /** Pseudo Minecraft enregistré (clé technique, jamais affiché en RP). */
    public String pseudo(UUID id) {
        String n = names.get(id);
        return n != null ? n : id.toString().substring(0, 8);
    }

    /** Compte par pseudo OU par nom RP (« Prénom Nom »), sans tenir compte des majuscules. */
    public UUID findByName(String name) {
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name) && balances.containsKey(e.getKey())) return e.getKey();
        }
        for (UUID id : balances.keySet()) {
            if (name(id).equalsIgnoreCase(name)) return id;
        }
        return null;
    }

    public Map<UUID, Long> all() { return Collections.unmodifiableMap(balances); }

    public long total() { return balances.values().stream().mapToLong(Long::longValue).sum(); }

    // --- capital de la banque (argent disponible pour les prêts)
    public long reserve() { return reserve; }

    public void addReserve(long delta) {
        reserve += delta;
        setDirty();
    }

    // --- grille des taux
    public int termRate(int days) {
        int i = LoanService.termIndex(days);
        return i < 0 ? 0 : termRates[i];
    }

    public int[] termRates() { return termRates.clone(); }

    public void setTermRates(int[] r) {
        for (int i = 0; i < Math.min(r.length, termRates.length); i++) termRates[i] = r[i];
        setDirty();
    }

    // --- banquiers
    public boolean isBanker(UUID id) { return bankers.contains(id); }

    public void setBanker(UUID id, boolean on) {
        if (on ? bankers.add(id) : bankers.remove(id)) setDirty();
    }

    // --- prêts
    public Collection<Loan> loans() { return Collections.unmodifiableCollection(loans.values()); }

    public Loan loan(UUID id) { return loans.get(id); }

    public Loan openLoanOf(UUID borrower) {
        for (Loan l : loans.values()) if (l.open() && l.borrower.equals(borrower)) return l;
        return null;
    }

    public void addLoan(Loan l) {
        loans.put(l.id, l);
        int excess = loans.size() - MAX_LOANS;
        for (Iterator<Loan> it = loans.values().iterator(); it.hasNext() && excess > 0; ) {
            if (!it.next().open()) {
                it.remove();
                excess--;
            }
        }
        setDirty();
    }
}
