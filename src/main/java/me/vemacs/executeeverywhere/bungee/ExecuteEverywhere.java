package me.vemacs.executeeverywhere.bungee;

import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class ExecuteEverywhere extends Plugin implements Listener {
    private JedisPool pool;
    private static final Joiner joiner = Joiner.on(" ");
    private final String BUNGEE_CHANNEL = "eb";
    private static Plugin instance;
    private EESubscriber eeSubscriber;

    Configuration config;

    @Override
    public void onEnable() {
        instance = this;
        try {
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(
                    loadResource(this, "config.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        getProxy().getPluginManager().registerCommand(this, new EECommand());
        getProxy().getPluginManager().registerCommand(this, new EBCommand());
        getProxy().getPluginManager().registerCommand(this, new ESCommand());
        final String specificChannel = "es_" + (config.getString("name").equals("") ? getProxy().getName() : config.getString("name"));
        final String ip = config.getString("ip");
        final int port = config.getInt("port");
        final String password = config.getString("password");
        getProxy().getScheduler().runAsync(this, new Runnable() {
            @Override
            public void run() {
                if (password == null || password.equals(""))
                    pool = new JedisPool(new JedisPoolConfig(), ip, port, 0);
                else
                    pool = new JedisPool(new JedisPoolConfig(), ip, port, 0, password);
                eeSubscriber = new EESubscriber();
                Jedis jedis = pool.getResource();
                try {
                    jedis.subscribe(eeSubscriber, BUNGEE_CHANNEL, specificChannel);
                } catch (Exception e) {
                    e.printStackTrace();
                    pool.returnBrokenResource(jedis);
                    getLogger().severe("Unable to connect to Redis server.");
                    return;
                }
                pool.returnResource(jedis);
            }
        });
    }

    @Override
    public void onDisable() {
        eeSubscriber.unsubscribe();
        pool.destroy();
    }

    public class EESubscriber extends JedisPubSub {
        @Override
        public void onMessage(String channel, final String msg) {
            ExecuteEverywhere.instance.getLogger().info("Dispatching /" + msg);
            ProxyServer ps = ProxyServer.getInstance();
            ps.getPluginManager().dispatchCommand(ps.getConsole(), msg);
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

    public static File loadResource(Plugin plugin, String resource) {
        File folder = plugin.getDataFolder();
        if (!folder.exists())
            folder.mkdir();
        File resourceFile = new File(folder, resource);
        try {
            if (!resourceFile.exists()) {
                resourceFile.createNewFile();
                try (InputStream in = plugin.getResourceAsStream(resource);
                     OutputStream out = new FileOutputStream(resourceFile)) {
                    ByteStreams.copy(in, out);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resourceFile;
    }

    public abstract class ECommand extends Command {
        String name;

        public ECommand(String name, String alias) {
            super(name, "executeeverywhere.use", alias);
            this.name = name;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length < 2 && name.equals("es")) {
                sender.sendMessage(ChatColor.RED + "Usage: /" + name + " <name> <cmd>");
                return;
            }
            else if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /" + name + " <cmd>");
                return;
            }
            String cmdString = joiner.join(args);
            String channel = name;
            if (cmdString.startsWith("/"))
                cmdString = cmdString.substring(1);
            if (channel.equals("es")) {
                String server = args[0];
                channel = "es_" + server;
                cmdString = cmdString.substring(server.length() + 1);
            }
            final String finalChannel = channel;
            final String finalCmdString = cmdString;
            instance.getProxy().getScheduler().schedule(instance, new Runnable() {
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
            }, 0, TimeUnit.SECONDS);
            sender.sendMessage(ChatColor.GREEN + "Sent /" + cmdString + " for execution.");
        }
    }

    public class EECommand extends ECommand {
        public EECommand() {
            super("ee", "executeeverywhere");
        }
    }

    public class EBCommand extends ECommand {
        public EBCommand() {
            super("eb", "executebungee");
        }
    }

    public class ESCommand extends ECommand {
        public ESCommand() {
            super("es", "executespecific");
        }
    }
}

