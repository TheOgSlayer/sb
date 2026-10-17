package smp.jalebi.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BoardManager {
    private static final int NAME_SCORE = 6;
    private static final String TITLE = "JALEBI SMP";
    private static final TextColor START = TextColor.fromHexString("#FF8C00"); // orange
    private static final TextColor END = TextColor.fromHexString("#FFE000");   // yellow

    private final JalebiScoreboard plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private Economy economy;

    public BoardManager(JalebiScoreboard plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        if (economy == null) plugin.getLogger().warning("Vault economy not found - money will show as $0.");
    }

    private static Component gradientTitle() {
        String text = SmallCaps.of(TITLE);
        Component out = Component.empty();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            float t = n == 1 ? 0 : (float) i / (n - 1);
            out = out.append(Component.text(text.charAt(i),
                    TextColor.lerp(t, START, END)).decorate(TextDecoration.BOLD));
        }
        return out;
    }

    private static String entry(int score) {
        return "§" + score + "§r";
    }

    private Team team(Scoreboard sb, int score) {
        String name = "line" + score;
        Team t = sb.getTeam(name);
        if (t == null) {
            t = sb.registerNewTeam(name);
            t.addEntry(entry(score));
        }
        return t;
    }

    private void setLine(Scoreboard sb, Objective obj, int score, Component text, boolean visible) {
        Team t = team(sb, score);
        if (visible) {
            t.prefix(text);
            obj.getScore(entry(score)).setScore(score);
        } else {
            sb.resetScores(entry(score));
        }
    }

    private Component line(Line l, Component value) {
        return Component.text(l.symbol + " ", TextColor.fromHexString(l.hex))
                .append(Component.text(SmallCaps.of(l.label) + " » ", NamedTextColor.GRAY))
                .append(value);
    }

    public void update(Player p) {
        DataStore d = plugin.data();
        UUID id = p.getUniqueId();

        if (!d.isEnabled(id, Line.SCOREBOARD)) {
            remove(p);
            return;
        }

        Scoreboard sb = boards.computeIfAbsent(id, k -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective obj = sb.getObjective("jalebi");
        if (obj == null) {
            obj = sb.registerNewObjective("jalebi", Criteria.DUMMY, gradientTitle());
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank()); // hide red numbers
        }

        // player name line
        setLine(sb, obj, NAME_SCORE,
                Component.text("➤ ", TextColor.fromHexString("#FFA500"))
                        .append(Component.text(SmallCaps.of(p.getName()), NamedTextColor.WHITE)), true);

        double money = economy != null ? economy.getBalance(p) : 0;
        setLine(sb, obj, Line.MONEY.score, line(Line.MONEY,
                Component.text("$", NamedTextColor.GREEN)
                        .append(Component.text(NumberFormatter.shorten(money), NamedTextColor.WHITE))),
                d.isEnabled(id, Line.MONEY));

        setLine(sb, obj, Line.KILLS.score, line(Line.KILLS,
                Component.text(NumberFormatter.shorten(p.getStatistic(Statistic.PLAYER_KILLS)), NamedTextColor.WHITE)),
                d.isEnabled(id, Line.KILLS));

        setLine(sb, obj, Line.DEATHS.score, line(Line.DEATHS,
                Component.text(NumberFormatter.shorten(p.getStatistic(Statistic.DEATHS)), NamedTextColor.WHITE)),
                d.isEnabled(id, Line.DEATHS));

        long secs = p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L;
        setLine(sb, obj, Line.PLAYTIME.score, line(Line.PLAYTIME,
                Component.text(SmallCaps.of(NumberFormatter.playtime(secs)), NamedTextColor.WHITE)),
                d.isEnabled(id, Line.PLAYTIME));

        setLine(sb, obj, Line.SHARDS.score, line(Line.SHARDS,
                Component.text(NumberFormatter.shorten(d.getShards(id)), NamedTextColor.WHITE)),
                d.isEnabled(id, Line.SHARDS));

        if (p.getScoreboard() != sb) p.setScoreboard(sb);
    }

    public void remove(Player p) {
        Scoreboard sb = boards.remove(p.getUniqueId());
        if (sb != null && p.getScoreboard() == sb) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void forget(Player p) {
        boards.remove(p.getUniqueId());
    }
}
