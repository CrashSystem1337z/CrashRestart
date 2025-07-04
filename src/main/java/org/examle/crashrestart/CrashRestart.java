import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

public class CrashRestart extends JavaPlugin implements CommandExecutor {

    private BossBar bossBar;
    private int restartDelay;
    private String restartMessage;
    private String titleMessage;
    private String subtitleMessage;
    private String bossBarMessage;
    private Map<String, String> restartTimes;
    private boolean restartInProgress = false; // Флаг, чтобы предотвратить повторный запуск

    @Override
    public void onEnable() {
        getLogger().info("CrashRestart включен!");
        getCommand("restart").setExecutor(this);
        saveDefaultConfig();
        loadConfig();
        scheduleDailyRestart();
    }

    private void loadConfig() {
        restartDelay = getConfig().getInt("restart-delay", 60);
        restartMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("restart-message", "&cСервер будет перезагружен через %seconds% секунд!"));
        titleMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("title-message", "&cПерезагрузка!"));
        subtitleMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("subtitle-message", "&cОсталось %seconds% секунд"));
        bossBarMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("bossbar-message", "&cПерезагрузка сервера через %seconds% секунд"));

        restartTimes = new HashMap<>();
        for (String dayOfWeek : getConfig().getConfigurationSection("restart-times").getKeys(false)) {
            String time = getConfig().getString("restart-times." + dayOfWeek, "03:00");
            restartTimes.put(dayOfWeek.toUpperCase(), time);
        }
    }

    private void scheduleDailyRestart() {
        ZoneId moscowZone = ZoneId.of("Europe/Moscow");
        ZonedDateTime now = ZonedDateTime.now(moscowZone);
        String dayOfWeek = now.getDayOfWeek().toString().toUpperCase();

        String time = restartTimes.getOrDefault(dayOfWeek, "03:00");
        String[] timeParts = time.split(":");
        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);
        int second = 0;

        ZonedDateTime nextRestart = now.withHour(hour).withMinute(minute).withSecond(second);
        if (nextRestart.isBefore(now) || nextRestart.isEqual(now)) {
            nextRestart = nextRestart.plusDays(1);
        }

        long delay = nextRestart.toInstant().toEpochMilli() - now.toInstant().toEpochMilli();

        Bukkit.getScheduler().runTaskLater(this, this::startRestartSequence, delay / 50);
    }

    private void startRestartSequence() {
        if (restartInProgress) {  // Проверяем не запущен ли уже процесс перезагрузки
            return;
        }
        restartInProgress = true;

        bossBar = Bukkit.createBossBar(bossBarMessage.replace("%seconds%", String.valueOf(restartDelay)), BarColor.RED, BarStyle.SEGMENTED_10);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }
        bossBar.setVisible(true);

        new BukkitRunnable() {
            int timeLeft = restartDelay;

            @Override
            public void run() {
                if (timeLeft <= 0) {
                    Bukkit.getServer().shutdown();
                    cancel();
                    return;
                }

                float progress = (float) timeLeft / restartDelay;
                bossBar.setProgress(progress);
                bossBar.setTitle(bossBarMessage.replace("%seconds%", String.valueOf(timeLeft)));

                if (timeLeft <= 30) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendTitle(titleMessage, subtitleMessage.replace("%seconds%", String.valueOf(timeLeft)), 10, 20, 10);
                    }
                }

                if (timeLeft <= 5) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage(restartMessage.replace("%seconds%", String.valueOf(timeLeft)));
                    }
                }

                timeLeft--;
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                super.cancel();
                restartInProgress = false;  // Сбрасываем флаг после завершения
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("restart")) {
            if (args.length == 0) { // Если аргументы не указаны, запускаем перезагрузку
                if (sender.hasPermission("restart.use")) { //Проверяем права
                    if (!restartInProgress) { // Проверяем, не запущена ли уже перезагрузка
                        startRestartSequence();
                    } else {
                        sender.sendMessage(ChatColor.RED + "Перезагрузка уже запущена.");
                    }

                    return true;
                } else {
                    sender.sendMessage(ChatColor.RED + "У вас нет прав на использование этой команды.");
                    return true;
                }

            } else if (args.length == 1 && args[0].equalsIgnoreCase("link")) {  //Если ввели /restart link то отправляем ссылку
                sender.sendMessage(ChatColor.GREEN + "Мой GitHub: " + ChatColor.BLUE + "https://github.com/crashsystem1337z");
                return true;

            } else { // Если ввели что-то другое то показываем что можно использовать
                sender.sendMessage(ChatColor.RED + "Использование: /restart или /restart link");
                return true;
            }
        }
        return false;
    }
}