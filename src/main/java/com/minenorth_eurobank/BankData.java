package com.minenorth_eurobank;

import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.loan.LoanService;
import fr.minenorth.api.BankTx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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
    /** Comptes entreprise : leur solde est dans balances, leur libellé dans names. */
    private final Set<UUID> business = new HashSet<>();
    /** Comptes entreprise visibles dans l'ATM. */
    private final Set<UUID> listed = new HashSet<>();
    private final Map<UUID, Set<UUID>> signers = new HashMap<>();
    private final Map<UUID, TxLog> history = new HashMap<>();
    private final Map<UUID, Loan> loans = new LinkedHashMap<>();
    private long reserve;
    /** Compte entreprise (la banque RP) dont le solde tient lieu de capital ; null = capital interne. */
    private UUID institution;
    /** Taux (points de base) appliqué sur la durée du prêt, par durée, dans l'ordre de LoanService.TERMS : 1, 3, 7, 14, 30 jours. */
    private final int[] termRates = {100, 250, 500, 800, 1200};
    private boolean refreshPending;
    /** Comptes gelés (joueurs ou entreprises) : plus aucune sortie d'argent. */
    private final Set<UUID> frozen = new HashSet<>();
    /** Notes privées du banquier, par compte. */
    private final Map<UUID, String> notes = new HashMap<>();
    private final List<Alert> alerts = new ArrayList<>();
    /** Seuil (centimes) au-delà duquel un mouvement déclenche une alerte. */
    private long alertThreshold = 10_000_00L;
    /** Dernier braquage de coffre (ms), 0 = aucun : les gros dépôts qui suivent sont suspects. */
    private long lastHeistMs;

    public static final int MAX_ALERTS = 100;
    public static final long HEIST_WINDOW_MS = 60L * 60_000L;
    /** Dépôt/virement entrant jugé suspect après un braquage. */
    public static final long HEIST_SUSPECT = 500_00L;

    /** Alerte automatique pour le banquier. */
    public static final class Alert {
        public final UUID id;
        public final long time;
        public final UUID account;
        public final String text;
        public final long cents;
        public boolean handled;

        public Alert(UUID id, long time, UUID account, String text, long cents, boolean handled) {
            this.id = id;
            this.time = time;
            this.account = account;
            this.text = text;
            this.cents = cents;
            this.handled = handled;
        }
    }

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
        ListTag bz = tag.getList("business", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < bz.size(); i++) d.business.add(NbtUtils.loadUUID(bz.get(i)));
        ListTag ls = tag.getList("listed", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < ls.size(); i++) d.listed.add(NbtUtils.loadUUID(ls.get(i)));
        ListTag sg = tag.getList("signers", Tag.TAG_COMPOUND);
        for (int i = 0; i < sg.size(); i++) {
            CompoundTag t = sg.getCompound(i);
            Set<UUID> set = new HashSet<>();
            ListTag ids = t.getList("ids", Tag.TAG_INT_ARRAY);
            for (int j = 0; j < ids.size(); j++) set.add(NbtUtils.loadUUID(ids.get(j)));
            d.signers.put(t.getUUID("id"), set);
        }
        ListTag hs = tag.getList("history", Tag.TAG_COMPOUND);
        for (int i = 0; i < hs.size(); i++) {
            CompoundTag t = hs.getCompound(i);
            TxLog log = new TxLog();
            ListTag txs = t.getList("txs", Tag.TAG_COMPOUND);
            for (int j = 0; j < txs.size(); j++) {
                CompoundTag x = txs.getCompound(j);
                log.add(new BankTx(x.getLong("time"), x.getString("cat"), x.getLong("cents"),
                        x.getLong("after"), x.getString("label"), x.getString("actor")));
            }
            d.history.put(t.getUUID("id"), log);
        }
        ListTag fz = tag.getList("frozen", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < fz.size(); i++) d.frozen.add(NbtUtils.loadUUID(fz.get(i)));
        ListTag nt = tag.getList("notes", Tag.TAG_COMPOUND);
        for (int i = 0; i < nt.size(); i++) d.notes.put(nt.getCompound(i).getUUID("id"), nt.getCompound(i).getString("text"));
        ListTag al = tag.getList("alerts", Tag.TAG_COMPOUND);
        for (int i = 0; i < al.size(); i++) {
            CompoundTag a = al.getCompound(i);
            d.alerts.add(new Alert(a.getUUID("id"), a.getLong("time"), a.getUUID("account"), a.getString("text"),
                    a.getLong("cents"), a.getBoolean("handled")));
        }
        ListTag ct = tag.getList("contacts", Tag.TAG_COMPOUND);
        for (int i = 0; i < ct.size(); i++) {
            CompoundTag c = ct.getCompound(i);
            d.contacts.add(new Contact(c.getUUID("id"), c.getLong("time"), c.getUUID("player"), c.getString("first"),
                    c.getString("last"), c.getString("phone"), c.getString("message"), c.getBoolean("handled")));
            d.contacts.get(d.contacts.size() - 1).remindMs = c.getLong("remind");
        }
        if (tag.contains("threshold")) d.alertThreshold = tag.getLong("threshold");
        d.lastHeistMs = tag.getLong("heist");
        d.weekStartMs = tag.getLong("weekStart");
        CompoundTag wk = tag.getCompound("week");
        for (String k : wk.getAllKeys()) d.week.put(k, wk.getLong(k));
        ListTag rp = tag.getList("reports", Tag.TAG_COMPOUND);
        for (int i = 0; i < rp.size(); i++) {
            CompoundTag r = rp.getCompound(i);
            List<String> lines = new ArrayList<>();
            ListTag ln = r.getList("lines", Tag.TAG_STRING);
            for (int j = 0; j < ln.size(); j++) lines.add(ln.getString(j));
            d.reports.add(new Report(r.getUUID("id"), r.getLong("start"), r.getLong("end"), lines));
        }
        d.reserve = tag.getLong("reserve");
        if (tag.hasUUID("institution")) d.institution = tag.getUUID("institution");
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
        ListTag bz = new ListTag();
        for (UUID b : business) bz.add(NbtUtils.createUUID(b));
        tag.put("business", bz);
        ListTag ls = new ListTag();
        for (UUID b : listed) ls.add(NbtUtils.createUUID(b));
        tag.put("listed", ls);
        ListTag sg = new ListTag();
        signers.forEach((id, set) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            ListTag ids = new ListTag();
            for (UUID u : set) ids.add(NbtUtils.createUUID(u));
            t.put("ids", ids);
            sg.add(t);
        });
        tag.put("signers", sg);
        ListTag hs = new ListTag();
        history.forEach((id, log) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            ListTag txs = new ListTag();
            for (BankTx x : log.all()) {
                CompoundTag c = new CompoundTag();
                c.putLong("time", x.time());
                c.putString("cat", x.category());
                c.putLong("cents", x.cents());
                c.putLong("after", x.balanceAfter());
                c.putString("label", x.label() == null ? "" : x.label());
                c.putString("actor", x.actor() == null ? "" : x.actor());
                txs.add(c);
            }
            t.put("txs", txs);
            hs.add(t);
        });
        tag.put("history", hs);
        ListTag fz = new ListTag();
        for (UUID f : frozen) fz.add(NbtUtils.createUUID(f));
        tag.put("frozen", fz);
        ListTag nt = new ListTag();
        notes.forEach((id, text) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("text", text);
            nt.add(t);
        });
        tag.put("notes", nt);
        ListTag al = new ListTag();
        for (Alert a : alerts) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", a.id);
            t.putLong("time", a.time);
            t.putUUID("account", a.account);
            t.putString("text", a.text);
            t.putLong("cents", a.cents);
            t.putBoolean("handled", a.handled);
            al.add(t);
        }
        tag.put("alerts", al);
        ListTag ct = new ListTag();
        for (Contact c : contacts) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", c.id);
            t.putLong("time", c.time);
            t.putUUID("player", c.player);
            t.putString("first", c.first);
            t.putString("last", c.last);
            t.putString("phone", c.phone);
            t.putString("message", c.message);
            t.putBoolean("handled", c.handled);
            t.putLong("remind", c.remindMs);
            ct.add(t);
        }
        tag.put("contacts", ct);
        tag.putLong("threshold", alertThreshold);
        tag.putLong("heist", lastHeistMs);
        tag.putLong("weekStart", weekStartMs);
        CompoundTag wk = new CompoundTag();
        week.forEach(wk::putLong);
        tag.put("week", wk);
        ListTag rp = new ListTag();
        for (Report r : reports) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", r.id);
            t.putLong("start", r.startMs);
            t.putLong("end", r.endMs);
            ListTag ln = new ListTag();
            for (String x : r.lines) ln.add(net.minecraft.nbt.StringTag.valueOf(x));
            t.put("lines", ln);
            rp.add(t);
        }
        tag.put("reports", rp);
        tag.putLong("reserve", reserve);
        if (institution != null) tag.putUUID("institution", institution);
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
            if (e.getValue().equalsIgnoreCase(name) && balances.containsKey(e.getKey()) && !business.contains(e.getKey())) return e.getKey();
        }
        UUID found = null;
        for (UUID id : balances.keySet()) {
            if (!business.contains(id) && name(id).equalsIgnoreCase(name)) {
                if (found != null) return null;   // nom RP porté par plusieurs comptes : on ne devine pas
                found = id;
            }
        }
        return found;
    }

    /** Vrai si plusieurs comptes joueurs portent ce nom RP (et qu'aucun pseudo Minecraft ne correspond exactement). */
    public boolean rpNameAmbiguous(String name) {
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name) && balances.containsKey(e.getKey()) && !business.contains(e.getKey())) return false;
        }
        int n = 0;
        for (UUID id : balances.keySet()) {
            if (!business.contains(id) && name(id).equalsIgnoreCase(name) && ++n > 1) return true;
        }
        return false;
    }

    /** Comptes joueurs uniquement (les comptes entreprise sont exclus). */
    public Map<UUID, Long> all() {
        Map<UUID, Long> m = new HashMap<>(balances);
        m.keySet().removeAll(business);
        return Collections.unmodifiableMap(m);
    }

    public long total() { return balances.values().stream().mapToLong(Long::longValue).sum(); }

    // --- comptes entreprise
    public boolean isBusiness(UUID id) { return business.contains(id); }

    public void openBusiness(UUID id, String label) {
        business.add(id);
        balances.putIfAbsent(id, 0L);
        names.put(id, label);
        setDirty();
    }

    public boolean isListed(UUID id) { return listed.contains(id); }

    /** Marque un compte entreprise comme visible dans l'ATM ; sans effet pour un autre compte. */
    public void setListed(UUID id, boolean on) {
        if (!business.contains(id)) return;
        if (on ? listed.add(id) : listed.remove(id)) setDirty();
    }

    /** Comptes entreprise listés, triés par libellé sans tenir compte de la casse. */
    public List<Map.Entry<UUID, String>> listedBusinesses() {
        List<Map.Entry<UUID, String>> out = new ArrayList<>();
        for (UUID id : listed) {
            if (!business.contains(id)) continue;
            String n = names.get(id);
            out.add(Map.entry(id, n != null ? n : id.toString().substring(0, 8)));
        }
        out.sort((x, y) -> x.getValue().compareToIgnoreCase(y.getValue()));
        return out;
    }

    public void closeBusiness(UUID id) {
        if (id.equals(institution)) institution = null;   // la banque perd son compte : plus de lien
        business.remove(id);
        listed.remove(id);
        balances.remove(id);
        names.remove(id);
        signers.remove(id);
        history.remove(id);
        frozen.remove(id);
        notes.remove(id);
        setDirty();
    }

    public Set<UUID> signers(UUID account) {
        return Collections.unmodifiableSet(signers.getOrDefault(account, Set.of()));
    }

    public void setSigners(UUID account, Set<UUID> s) {
        signers.put(account, new HashSet<>(s));
        setDirty();
    }

    public void record(UUID account, BankTx tx) {
        TxLog log = history.computeIfAbsent(account, k -> new TxLog());
        log.add(tx);
        detect(account, tx, log);
        setDirty();
    }

    /** Enregistre une opération déjà appliquée au solde (le solde après opération est lu ici). */
    public void log(UUID account, String category, long signedCents, String label, String actor) {
        record(account, new BankTx(System.currentTimeMillis(), category, signedCents, balance(account), label, actor));
    }

    /** Alertes automatiques : gros mouvement, rafale d'opérations, gros dépôt juste après un braquage. */
    private void detect(UUID account, BankTx tx, TxLog log) {
        // Mouvements normaux de la banque elle-même : jamais d'alerte (capital, prêts, salaires, ajustements admin)
        if (account.equals(institution) || BankTx.ADMIN.equals(tx.category()) || BankTx.SALARY.equals(tx.category())
                || isLoanLabel(tx.label())) {
            return;
        }
        long abs = Math.abs(tx.cents());
        String who = displayAccount(account);
        if (abs >= alertThreshold) {
            raise(account, "gros", "Gros mouvement de " + Money.format(tx.cents()) + " sur " + who, tx.cents());
        }
        long now = tx.time();
        if (tx.cents() > 0 && lastHeistMs > 0 && now - lastHeistMs <= HEIST_WINDOW_MS && abs >= HEIST_SUSPECT) {
            raise(account, "braquage", "Entrée suspecte après un braquage : " + Money.format(tx.cents()) + " sur " + who, tx.cents());
        }
        if (!business.contains(account)) {
            int recent = 0;
            for (BankTx x : log.latest(20)) if (now - x.time() <= 10 * 60_000L) recent++;
            if (recent >= 15) {
                raise(account, "rafale", "15 opérations en moins de 10 minutes sur " + who, tx.cents());
            }
        }
    }

    private static boolean isLoanLabel(String label) {
        if (label == null) return false;
        return label.startsWith("Prêt") || label.contains("de prêt") || label.startsWith("Capital de la banque");
    }

    /** Une même alerte (compte + type) n'est répétée qu'après 15 minutes : évite d'inonder le banquier. */
    private final transient Map<String, Long> alertCooldown = new HashMap<>();

    private void raise(UUID account, String kind, String text, long cents) {
        String key = account + kind;
        long now = System.currentTimeMillis();
        Long last = alertCooldown.get(key);
        if (last != null && now - last < 15 * 60_000L) return;
        alertCooldown.put(key, now);
        addAlert(account, text, cents);
    }

    private String displayAccount(UUID id) {
        return (business.contains(id) ? "l'entreprise " : "") + name(id);
    }

    public void addAlert(UUID account, String text, long cents) {
        week.merge("alertsN", 1L, Long::sum);
        alerts.add(new Alert(UUID.randomUUID(), System.currentTimeMillis(), account, text, cents, false));
        while (alerts.size() > MAX_ALERTS) alerts.remove(0);
        setDirty();
        if (server != null) {
            for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
                if (bankers.contains(sp.getUUID())) {
                    sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[Banque] Alerte : " + text));
                }
            }
        }
    }

    // --- demandes de contact (« Contacter mon banquier » au guichet)
    public static final int MAX_CONTACTS = 100;

    /** Demande de rappel laissée par un joueur au guichet. */
    public static final class Contact {
        public final UUID id;
        public final long time;
        public final UUID player;
        public final String first, last, phone, message;
        public boolean handled;
        /** Heure (ms) à laquelle rappeler ce client ; 0 = aucun rappel programmé. */
        public long remindMs;

        public Contact(UUID id, long time, UUID player, String first, String last, String phone, String message, boolean handled) {
            this.id = id;
            this.time = time;
            this.player = player;
            this.first = first;
            this.last = last;
            this.phone = phone;
            this.message = message;
            this.handled = handled;
        }
    }

    private final List<Contact> contacts = new ArrayList<>();

    // --- rapport hebdomadaire : compteurs de la semaine en cours et rapports passés
    public static final int MAX_REPORTS = 12;

    /** Rapport d'une semaine écoulée (lignes de texte déjà formatées). */
    public static final class Report {
        public final UUID id;
        public final long startMs, endMs;
        public final List<String> lines;

        public Report(UUID id, long startMs, long endMs, List<String> lines) {
            this.id = id;
            this.startMs = startMs;
            this.endMs = endMs;
            this.lines = lines;
        }
    }

    private final Map<String, Long> week = new HashMap<>();
    private final List<Report> reports = new ArrayList<>();
    private long weekStartMs;

    public void addStat(String key, long delta) {
        week.merge(key, delta, Long::sum);
        setDirty();
    }

    public long stat(String key) { return week.getOrDefault(key, 0L); }

    public long weekStartMs() { return weekStartMs; }

    public void setWeekStart(long ms) {
        weekStartMs = ms;
        setDirty();
    }

    public List<Report> reports() { return Collections.unmodifiableList(reports); }

    /** Archive le rapport de la semaine écoulée et remet les compteurs à zéro. */
    public void closeWeek(long endMs, List<String> lines) {
        reports.add(new Report(UUID.randomUUID(), weekStartMs, endMs, lines));
        while (reports.size() > MAX_REPORTS) reports.remove(0);
        week.clear();
        weekStartMs = endMs;
        setDirty();
    }

    public List<Contact> contacts() { return Collections.unmodifiableList(contacts); }

    public long openContacts() { return contacts.stream().filter(c -> !c.handled).count(); }

    public void addContact(UUID player, String first, String last, String phone, String message) {
        contacts.add(new Contact(UUID.randomUUID(), System.currentTimeMillis(), player, first, last, phone, message, false));
        week.merge("contactsN", 1L, Long::sum);
        while (contacts.size() > MAX_CONTACTS) contacts.remove(0);
        setDirty();
        if (server != null) {
            for (net.minecraft.server.level.ServerPlayer sp : server.getPlayerList().getPlayers()) {
                if (bankers.contains(sp.getUUID())) {
                    sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§6[Banque] " + first + " " + last + " souhaite vous contacter (tél. " + phone + ")."));
                }
            }
        }
    }

    public Contact contact(UUID id) {
        for (Contact c : contacts) if (c.id.equals(id)) return c;
        return null;
    }

    public void deleteContact(UUID id) {
        if (contacts.removeIf(c -> c.id.equals(id))) setDirty();
    }

    public void clearHandledContacts() {
        if (contacts.removeIf(c -> c.handled)) setDirty();
    }

    public void clearHandledAlerts() {
        if (alerts.removeIf(a -> a.handled)) setDirty();
    }

    public boolean hasOpenContact(UUID player) {
        for (Contact c : contacts) if (!c.handled && c.player.equals(player)) return true;
        return false;
    }

    public void handleContact(UUID id) {
        for (Contact c : contacts) if (c.id.equals(id) && !c.handled) { c.handled = true; setDirty(); }
    }

    public List<Alert> alerts() { return Collections.unmodifiableList(alerts); }

    public long openAlerts() { return alerts.stream().filter(a -> !a.handled).count(); }

    public void handleAlert(UUID id) {
        for (Alert a : alerts) if (a.id.equals(id) && !a.handled) { a.handled = true; setDirty(); }
    }

    public void handleAllAlerts() {
        for (Alert a : alerts) a.handled = true;
        setDirty();
    }

    public long alertThreshold() { return alertThreshold; }

    public void setAlertThreshold(long cents) {
        alertThreshold = cents;
        setDirty();
    }

    /** Appelé quand un coffre de banque est ouvert : les gros dépôts qui suivent seront signalés. */
    public void markHeist() {
        lastHeistMs = System.currentTimeMillis();
        setDirty();
    }

    // --- gel et notes
    public boolean isFrozen(UUID id) { return frozen.contains(id); }

    public void setFrozen(UUID id, boolean on) {
        if (on ? frozen.add(id) : frozen.remove(id)) setDirty();
    }

    public String note(UUID id) { return notes.getOrDefault(id, ""); }

    public void setNote(UUID id, String text) {
        if (text == null || text.isBlank()) notes.remove(id);
        else notes.put(id, text);
        setDirty();
    }

    /** Tous les comptes (joueurs et entreprises). */
    public Set<UUID> accountIds() { return Collections.unmodifiableSet(balances.keySet()); }

    public Set<UUID> businesses() { return Collections.unmodifiableSet(business); }

    /** Une opération avec le compte concerné (flux global de la tablette banquier). */
    public record AccountTx(UUID account, BankTx tx) {}

    /** Dernières opérations de tous les comptes (ou d'un seul si {@code filter} n'est pas null), les plus récentes d'abord. */
    public List<AccountTx> recent(UUID filter, int limit) {
        List<AccountTx> all = new ArrayList<>();
        for (Map.Entry<UUID, TxLog> e : history.entrySet()) {
            if (filter != null && !filter.equals(e.getKey())) continue;
            for (BankTx t : e.getValue().latest(limit)) all.add(new AccountTx(e.getKey(), t));
        }
        all.sort((a, b) -> Long.compare(b.tx().time(), a.tx().time()));
        return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
    }

    public List<BankTx> history(UUID account, int limit) {
        TxLog log = history.get(account);
        return log == null ? new ArrayList<>() : log.latest(limit);
    }

    // --- capital de la banque (argent disponible pour les prêts)
    private boolean linked() { return institution != null && business.contains(institution); }

    public UUID institution() { return linked() ? institution : null; }

    /** Capital disponible : solde du compte de l'entreprise « banque » si elle est reliée, sinon le capital interne. */
    public long reserve() { return linked() ? balance(institution) : reserve; }

    public void addReserve(long delta) {
        if (linked()) {
            add(institution, delta);
            record(institution, new BankTx(System.currentTimeMillis(), delta >= 0 ? BankTx.INCOME : BankTx.PAYMENT, delta, balance(institution),
                    delta >= 0 ? "Remboursement de prêt" : "Prêt accordé", null));
        } else {
            reserve += delta;
        }
        setDirty();
    }

    /** Relie (ou délie avec null) le capital à un compte entreprise. Le capital interne est versé sur le compte à la liaison. */
    public boolean linkInstitution(UUID account) {
        if (account == null) {
            institution = null;
            setDirty();
            return true;
        }
        if (!business.contains(account)) return false;
        institution = account;
        if (reserve != 0) {
            add(account, reserve);
            record(account, new BankTx(System.currentTimeMillis(), BankTx.INCOME, reserve, balance(account), "Capital de la banque", null));
            reserve = 0;
        }
        setDirty();
        return true;
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
