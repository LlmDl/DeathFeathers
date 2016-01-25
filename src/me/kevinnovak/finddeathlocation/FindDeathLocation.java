package me.kevinnovak.finddeathlocation;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

// suppress the item id warnings
@SuppressWarnings("deprecation")

public class FindDeathLocation extends JavaPlugin implements Listener{
    
    // create deaths.yml file
    public File deathsFile = new File(getDataFolder()+"/deaths.yml");
    public FileConfiguration deathData = YamlConfiguration.loadConfiguration(deathsFile);
    
    // load the item to listen for
    ItemStack deathItem = new ItemStack(getConfig().getInt("item")); 
    
    // cooldown hashmaps
    private HashMap<Player, Integer> cooldownTime;
    private HashMap<Player, BukkitRunnable> cooldownTask;
    
    // ======================
    // Enable
    // ======================
    public void onEnable() {
        // save default config file if it doesnt exist
        saveDefaultConfig();
        
        // register the listeners
        Bukkit.getServer().getPluginManager().registerEvents(this, this);
        
        // prepare the cooldown hasmaps
        cooldownTime = new HashMap<Player, Integer>();
        cooldownTask = new HashMap<Player, BukkitRunnable>();
        
        // start metrics
        if (getConfig().getBoolean("metrics")) {
            try {
                MetricsLite metrics = new MetricsLite(this);
                metrics.start();
                Bukkit.getServer().getLogger().info("[FindDeathLocation] Metrics Enabled!");
            } catch (IOException e) {
                Bukkit.getServer().getLogger().info("[FindDeathLocation] Failed to Start Metrics.");
            }
        } else {
            Bukkit.getServer().getLogger().info("[FindDeathLocation] Metrics Disabled.");
        }
        
        // plugin is enabled
        Bukkit.getServer().getLogger().info("[FindDeathLocation] Plugin Enabled!");
    }
  
    // ======================
    // Disable
    // ======================
    public void onDisable() {
        // plugin is disabled
        Bukkit.getServer().getLogger().info("[FindDeathLocation] Plugin Disabled!");
    }

    // ======================
    // Saving Death Locations
    // ======================
    @EventHandler
    // when a player dies...
    public void onDeath(PlayerDeathEvent e) {
        if (e.getEntityType() == EntityType.PLAYER) {
            // saving the players death location
            Player player = e.getEntity();
            deathData.set(player.getName() + ".World", player.getLocation().getWorld().getName());
            deathData.set(player.getName() + ".X", player.getLocation().getBlockX());
            deathData.set(player.getName() + ".Y", player.getLocation().getBlockY());
            deathData.set(player.getName() + ".Z", player.getLocation().getBlockZ());
            try {
                deathData.save(deathsFile);
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }
            
            // ======================
            // Death Logging
            // ======================
            
            // creating the string to log
            String location = player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ();
            String deathLog = convertedLang("deathLog").replace("{PLAYER}", player.getName()).replace("{LOCATION}", location).replace("{WORLD}", player.getLocation().getWorld().getName());
            
            // sending log to console
            if (getConfig().getBoolean("consoleLog")) {
                Bukkit.getServer().getLogger().info("[FindDeathLocation] " + player.getName() + " has died at " + location + " in " + player.getLocation().getWorld().getName() + ".");
            }
            
            // sending log to players with permissions
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.hasPermission("finddeathlocation.log")) {
                    onlinePlayer.sendMessage(deathLog);
                }
            }
        }
    }
    
    // ======================
    // Events to Set Compass
    // ======================
    @EventHandler
    // when a player respawns...
    public void onRespawn(PlayerRespawnEvent e) {
        
        // give player the respawn item
        if (getConfig().getBoolean("itemOnRespawn")) {
            e.getPlayer().getInventory().addItem(deathItem);
        }
        
        // set the players compass
        if (getConfig().getBoolean("compassDirection")) {
            setCompass(e.getPlayer());
        }
    }
    
    @EventHandler
    // when a player joins...
    public void onPlayerJoin(PlayerJoinEvent e) {
        
        // set the players compass
        if (getConfig().getBoolean("compassDirection")) {
            setCompass(e.getPlayer());
        }
    }
    
    @EventHandler
    // when a player changes worlds...
    public void onChangeWorld(PlayerChangedWorldEvent e) {
        
        // set the players compass
        if (getConfig().getBoolean("compassDirection")) {
            setCompass(e.getPlayer());
        }
    }
    
    // ======================
    // Setting Compass
    // ======================
    // sets the players compass to point to their death location
    void setCompass(Player player) {
        // delay setting the compass until the player has had time to properly respawn
        getServer().getScheduler().scheduleAsyncDelayedTask(this, new Runnable() {
            public void run() {
                String playername = player.getName();
                
                // if player has no death data, dont set the compass
                if (deathData.getString(playername) == null) {
                    return;
                }
                
                // otherwise grab the players death location
                World world = getServer().getWorld(deathData.getString(playername + ".World"));
                int xPos = Integer.parseInt(deathData.getString(playername + ".X"));
                int yPos = Integer.parseInt(deathData.getString(playername + ".Y")) + getConfig().getInt("numBlocksAbove");
                int zPos = Integer.parseInt(deathData.getString(playername + ".Z"));
                Location targetLocation = new Location(world, xPos, yPos, zPos);
                
                // set the players compass to death location
                player.setCompassTarget(targetLocation);
            }
        }, 1 * 20L); // delayed to allow player time to respawn
    }

    // ======================
    // Clicking with Item
    // ======================
    @EventHandler
    // when a player clicks
    public void interact(PlayerInteractEvent event) {
        // get the players name and type of click
        Player player = event.getPlayer();
        Action action = event.getAction();
        
        // if player is left clicking with the death item
        if (getConfig().getBoolean("leftClick")) {
            if (action.equals(Action.LEFT_CLICK_AIR) || action.equals(Action.LEFT_CLICK_BLOCK)) {
                if (player.getItemInHand().getType() == deathItem.getType()) {
                    
                    // check for permission
                    if (!player.hasPermission("finddeathlocation.item")) {
                        player.sendMessage(convertedLang("notPermitted"));
                        return;
                    }
                    
                    // send the distance to the player
                    sendDistance(player);
                }
            }
        }
        
     // if player is right clicking with the death item
        if (getConfig().getBoolean("rightClick")) {
            if (action.equals(Action.RIGHT_CLICK_AIR) || action.equals(Action.RIGHT_CLICK_BLOCK)) {
                if (player.getItemInHand().getType() == deathItem.getType()) {
                    
                    // check for permission
                    if (!player.hasPermission("finddeathlocation.item")) {
                        player.sendMessage(convertedLang("notPermitted"));
                        return;
                    }
                    
                    // send the distance to the player
                    sendDistance(player);
                }
            }
        }
    }
    
    // ===========================
    // Sending Distance to Player
    // ===========================
    void sendDistance(Player player) {
        String playername = player.getName();
        
        // if the player has not died, let them know
        if (deathData.getString(playername) == null) {
            player.sendMessage(convertedLang("notDied"));
            return;
        }
        
        // otherwise grab the world the player is in
        World world = getServer().getWorld(deathData.getString(playername + ".World"));
        
        // if their death world is the same world they are in
        if (world == player.getWorld()) {
            
            // grab the players death coodinates
            int xPos = Integer.parseInt(deathData.getString(playername + ".X"));
            int yPos = Integer.parseInt(deathData.getString(playername + ".Y"));
            int zPos = Integer.parseInt(deathData.getString(playername + ".Z"));
            
            // grab the players current coodinates
            int pxPos = player.getLocation().getBlockX();
            int pyPos = player.getLocation().getBlockY();
            int pzPos = player.getLocation().getBlockZ();
            
            // calculate the distance
            int distanceToDeath = 0;
            if (getConfig().getBoolean("distance3D")) {
                distanceToDeath = (int) Math.sqrt(((xPos - pxPos)*(xPos - pxPos)) + ((zPos - pzPos)*(zPos - pzPos)) + ((yPos - pyPos)*(yPos - pyPos)));
            } else {
                distanceToDeath = (int) Math.sqrt(((xPos - pxPos)*(xPos - pxPos)) + ((zPos - pzPos)*(zPos - pzPos)));
            }
            
            // send that distance to the player
            player.sendMessage(convertedLang("senddistance").replace("{DISTANCE}", Integer.toString(distanceToDeath)));
        
        // otherwise tell the player their death is in another world
        } else {
            player.sendMessage(convertedLang("anotherWorld"));
        }
    }

    // ======================
    // Commands
    // ======================
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        // ======================
        // Console
        // ======================
        // if command sender is the console, let them know, cancel command
        if (!(sender instanceof Player)) {
            sender.sendMessage(convertedLang("noConsole"));
            return true;
        }
        
        // otherwise the command sender is a player
        final Player player = (Player) sender;
        
        // ======================
        // /finddeath
        // ======================
        if(cmd.getName().equalsIgnoreCase("finddeath")) {
            
            // check for permission
            if (!player.hasPermission("finddeathlocation.command")) {
                player.sendMessage(convertedLang("notPermitted"));
                return true;
            
            // send the distance to the player
            } else {
                sendDistance(player);
            }
            
            return true;
        }
        
        // ======================
        // /tpdeath
        // ======================
        if(cmd.getName().equalsIgnoreCase("tpdeath")) {
            
            // if the player is still in cooldown
            if (cooldownTime.containsKey(player)) {
                
                // if the player is still in waiting to teleport
                if(getConfig().getInt("cooldownSeconds") - cooldownTime.get(player) < 0) {
                    
                    // send a message telling them theyre already teleporting
                    player.sendMessage(convertedLang("tpWait"));
               
                // the player is not waiting to teleport
                } else {
                    
                    // send a message telling them the time to wait
                    player.sendMessage(convertedLang("cooldown").replace("{COOLDOWN}", convertTime(cooldownTime.get(player))));
                }
                return true;
            }

            // the player is no longer in cooldown
            // if the command sender has not specified a player to tp to
            if(args.length == 0) {
                
                // check for permission
                if (!player.hasPermission("finddeathlocation.tp")) {
                    player.sendMessage(convertedLang("notPermitted"));
                    return true;
                
                } else {
                    
                    // if the command sender does not have death data
                    if (deathData.getString(player.getName()) == null) {
                        player.sendMessage(convertedLang("notDied"));
                        return true;
                    
                    // command sender has death data
                    } else {
                        
                        // teleport the player to their own death location
                        teleportPlayer(player, player.getName());
                        
                        // if cooldown in config, set the players cooldown
                        if (getConfig().getInt("cooldownSeconds") > 0) {
                            cooldown(player); 
                        }
                        return true;
                    }
                }
            }
            
            // command sender has specified a player to tp to
            // set the player as the target
            String target = args[0];
            
            // check for permission to tp to others
            if (!player.hasPermission("finddeathlocation.tp.others")) {
                player.sendMessage(convertedLang("notPermitted"));
                return true;
            } else {
                
                // if the target does not have death data
                if (deathData.getString(target) == null) {
                    player.sendMessage(convertedLang("noLocation"));
                    return true;
                
                // target has death data
                } else {
                    
                    // teleport player to targets death location
                    teleportPlayer(player, target);
                    
                    // if cooldown in config, set the players cooldown
                    if (getConfig().getInt("cooldownSeconds") > 0) {
                        cooldown(player); 
                    }
                    return true;
                }
            }
        }  
        return true;
    }
    
    // ======================
    // Teleport Player
    // ======================
    // teleports player to their death location, or another players death location
    void teleportPlayer(Player player, String target) {
        
        // grabs the provide targets death location
        World world = getServer().getWorld(deathData.getString(target + ".World"));
        int xPos = Integer.parseInt(deathData.getString(target + ".X"));
        int yPos = Integer.parseInt(deathData.getString(target + ".Y")) + getConfig().getInt("numBlocksAbove");
        int zPos = Integer.parseInt(deathData.getString(target + ".Z"));
        Location targetLocation = new Location(world, xPos, yPos, zPos);
        
        // delay teleportation if configured so
        if (getConfig().getInt("delaySeconds") > 0) {
            if (!player.hasPermission("finddeathlocation.tp.bypass")) {
                int delaySeconds = getConfig().getInt("delaySeconds");
                player.sendMessage(convertedLang("teleporting").replace("{DELAY}", Integer.toString(delaySeconds)));
                getServer().getScheduler().scheduleAsyncDelayedTask(this, new Runnable() {
                    public void run() {
                        player.teleport(targetLocation);
                    }
                }, delaySeconds * 20L);
            } else {
                // then teleport player
                player.teleport(targetLocation);
            }
        
        // otherwise just teleport the player
        } else {
            player.teleport(targetLocation);
        }
    }
    
    // =========================
    // Cooldown
    // =========================
    void cooldown(Player player) {
        
        // put the player in the hash table with delay time
        cooldownTime.put(player, getConfig().getInt("cooldownSeconds") + getConfig().getInt("delaySeconds"));
        
        cooldownTask.put(player, new BukkitRunnable() {
                public void run() {
                    
                        // subtract 1 from cooldown time every second
                        cooldownTime.put(player, cooldownTime.get(player) - 1);
                        
                        // if cooldown time reaches 0, then remove the player from the hash table
                        if (cooldownTime.get(player) == 0) {
                                cooldownTime.remove(player);
                                cooldownTask.remove(player);
                                cancel();
                        }
                }
        });
       
        // run this every 20 ticks, or 1 second
        cooldownTask.get(player).runTaskTimer(this, 20, 20);
    }
    
    // ================================
    // Convert Cooldown Time to String
    // ================================
    String convertTime(int initSeconds) {
        String init = "";
        
        // days
        if ((initSeconds/86400) >= 1) {
            int days = initSeconds/86400;
            initSeconds = initSeconds%86400;
            if (days > 1) {
                init = init + " " + days + " Days";
            } else {
                init = init + " " + days + " Day";
            }
        }
        
        // hours
        if ((initSeconds/3600) >= 1) {
            int hours = initSeconds/3600;
            initSeconds = initSeconds%3600;
            if (hours > 1) {
                init = init + " " + hours + " Hours";
            } else {
                init = init + " " + hours + " Hour";
            }
        }
        
        // minutes
        if ((initSeconds/60) >= 1) {
            int minutes = initSeconds/60;
            initSeconds = initSeconds%60;
            if (minutes > 1) {
                init = init + " " + minutes + " Minutes";
            } else {
                init = init + " " + minutes + " Minute";
            }
        }
        
        // seconds
        if (initSeconds >= 1) {
            if (initSeconds > 1) {
                init = init + " " + initSeconds + " Seconds";
            } else {
                init = init + " " + initSeconds + " Second";
            }
        }
        // remove the initial space
        init = init.substring(1, init.length());
        return init;
    }
    
    // =========================
    // Convert String in Config
    // =========================
    // converts string in config, to a string with colors
    String convertedLang(String toConvert) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString(toConvert));
    }
}