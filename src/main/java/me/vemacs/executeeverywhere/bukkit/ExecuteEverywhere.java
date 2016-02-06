package me.vemacs.executeeverywhere.bukkit;

import com.google.common.base.Joiner;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.lang.Override;

public class ExecuteEverywhere extends JavaPlugin implements Listener {
    private JedisPool pool;
    private static final Joiner joiner = Joiner.on(" ");
    private final String CHANNEL = "ee";
    private final String BUNGEE_CHANNEL = "eb";
    private String SPECIFIC_CHANNEL = "";
    private static Plugin instance;
    private EESubscriber eeSubscriber;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        SPECIFIC_CHANNEL = "es_" + (getConfig().getString("name").equals("") ? getServer().getServerName() : getConfig().getString("name"));
        String ip = getConfig().getString("ip");
        int port = getConfig().getInt("port");
        String password = getConfig().getString("password");
        if (password == null || password.equals(""))
            pool = new JedisPool(new JedisPoolConfig(), ip, port, 0);
        else
            pool = new JedisPool(new JedisPoolConfig(), ip, port, 0, password);
        new BukkitRunnable() {
            @Override
            public void run() {
                eeSubscriber = new EESubscriber();
                Jedis jedis = pool.getResource();
                try {
                    jedis.subscribe(eeSubscriber, CHANNEL, SPECIFIC_CHANNEL);
                } catch (Exception e) {
                    e.printStackTrace();
                    pool.returnBrokenResource(jedis);
                    getLogger().severe("Unable to connect to Redis server.");
                    return;
                }
                pool.returnResource(jedis);
            }
        }.runTaskAsynchronously(this);
    }

    @Override
    public void onDisable() {
        eeSubscriber.unsubscribe();
        pool.destroy();
    }

    @Override
    public boolean onCommand(CommandSender sender, final Command cmd, String label, String[] args) {
        if (args.length < 2 && cmd.getName().equalsIgnoreCase("es")) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + cmd.getName() + " <name> <cmd>");
            return true;
        } else if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + cmd.getName() + " <cmd>");
            return true;
        }
        String cmdString = joiner.join(args);
        String channel = cmd.getName();
        if (cmdString.startsWith("/"))
            cmdString = cmdString.substring(1);
        if (channel.equals("es")) {
            String server = args[0];
            channel = "es_" + server;
            cmdString = cmdString.substring(server.length() + 1);
        }
        final String finalChannel = channel;
        final String finalCmdString = cmdString;
        new BukkitRunnable() {
            @Override
            public void run() {
                Jedis jedis = pool.getResource();
                try {
                    jedis.publish(finalChannel, finalCmdString);
                } catch (Exception e) {
                    pool.returnBrokenResource(jedis);
                }
                pool.returnResource(jedis);
            }
        }.runTaskAsynchronously(this);
        sender.sendMessage(ChatColor.GREEN + "Sent /" + cmdString + " for execution.");
        return true;
    }

    public class EESubscriber extends JedisPubSub {
        @Override
        public void onMessage(String channel, final String msg) {
            // Needs to be done in the server thread
             new BukkitRunnable() {
                @Override
                public void run() {
                    ExecuteEverywhere.instance.getLogger().info("Dispatching /" + msg);
                    getServer().dispatchCommand(getServer().getConsoleSender(), msg);
                }
            }.runTask(ExecuteEverywhere.instance);
        }

        @Override
        public void onPMessage(String s, String s2, String s3) {
        }

        @Override
        public void onSubscribe(String s, int i) {
        }

        @Override
        public void onUnsubscribe(String s, int i) {
        }

        @Override
        public void onPUnsubscribe(String s, int i) {
        }

        @Override
        public void onPSubscribe(String s, int i) {
        }
    }
}

