package com.crimsonwarpedcraft.jenny;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public final class JennyEntity extends JavaPlugin implements Listener {
    
    // ========== JENNY NPC ==========
    private ArmorStand jenny;
    private Location currentLocation;
    private UUID currentFollowingPlayer = null;
    
    // ========== OG MOD STATE VARIABLES ==========
    private boolean lookingForBed = false;
    private boolean isPreparingPayment = false;
    private boolean isDoingAction = false;
    private int bedSearchTick = 0;
    private int preparingPaymentTick = 0;
    private int flip = 0;
    private String currentAction = ""; // "strip", "blowjob", "doggy"
    private Location targetBedPos = null;
    private float targetYaw = 0f;
    private Player currentCustomer = null;
    
    // ========== PAYMENT SYSTEM (OG VALUES) ==========
    private enum PaymentItems {
        GOLD(1, "strip"),
        EMERALD(3, "blowjob"),
        DIAMOND(2, "doggy");
        
        final int amount;
        final String action;
        PaymentItems(int amount, String action) { this.amount = amount; this.action = action; }
    }
    
    // ========== COOLDOWN MAPS ==========
    private final Map<UUID, Long> actionCooldowns = new HashMap<>();
    private final Map<UUID, Integer> paymentProgress = new HashMap<>();
    
    // ========== CUSTOM SKIN DATA (YOURS) ==========
    private static final String SKIN_VALUE = "ewogICJ0aW1lc3RhbXAiIDogMTc3NzExNzk1Nzk3NSwKICAicHJvZmlsZUlkIiA6ICIxMjE4YWNiNDJiYzA0MzY4YjIxOTU4ZTZiYWU2NDMyMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJQYXRhdGplTUMiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTk1N2E0MDU1Y2EzMjMzNTAxZTA4MjRmYTgyYTU5NTZjZGIyZWU3MGFhYWM2ODA5NmQwMjVkZmE3ZjEzZDNkMyIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9";
    private static final String SKIN_SIGNATURE = "gGpTOwPx5g0I4rsgec/qii6L/I//0adHXwJ3WQnxt/tH6Csj0SM0ySlOtqhvYw6Ll8i9xflfPo39r4gJ0nLKwor8Yv0dtWQ7kXt6lwi5BYRs2Ny5j/1T/FYP8zcCFXaXvdFNzlNwTJVkw20//xtbddHzblTo6WYmQhyJbmj5Ar6FKMXBnkvYNCzFTyg+mKQ3fE9cDG6dbdmimmhCbptnURIM1Gbr9vbjZp+kK+6W78bW8frGvpGwbe9bYec73RodxPwbpbkciI/7B3CgnFlyNEd16Kor2YnmX/fJEdGspJmWazg81gsZ2A0bEyDb7YSvcs+vazXkxmRtICPpdPEmSunM/aVXuBmnHuwcZdQKQpYejIbFOi1pARrpT2BfKJd7CSBMzfT9W41fH9jRTUWARfJ4YvvD4JGNtWwvmsXnDN3/H77JrlWX/0xxG3tvSuyObqEhKxgLvcSDwrQiRiTgWFo3I1daTSpxUa27cw71hDBw26kxsz63CqPqJofEqTTDOKJs7y1GKaRVLGxOkTQ3uJaCmo64q6jhVse+EWpLjbIoMCEkMSuGDz56DSP+NL8WP6vsIps0YEhoHnXvR9I9fLkCMqxfR6G/RQWHRp8Qn3s1ncIbwrC2J3ER8shSepKHPrtSImULcWSkOdyaVtXhPf4I6QENkoakknvZXTrWxLs=";
    
    // ========== PLUGIN LIFECYCLE ==========
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("jenny").setExecutor(this);
        
        World world = Bukkit.getWorlds().get(0);
        spawnJenny(new Location(world, 0, 64, 0));
        
        // Start AI task (runs every tick)
        new BukkitRunnable() {
            @Override
            public void run() {
                updateAITasks();
            }
        }.runTaskTimer(this, 0L, 1L);
        
        getLogger().info("JennyEntity (OG Port) enabled - Payment system active");
    }
    
    @Override
    public void onDisable() {
        if (jenny != null) jenny.remove();
    }
    
    // ========== SPAWN LOGIC ==========
    private void spawnJenny(Location loc) {
        if (jenny != null) jenny.remove();
        
        World world = loc.getWorld();
        jenny = world.spawn(loc, ArmorStand.class);
        jenny.setVisible(true);
        jenny.setGravity(false);
        jenny.setBasePlate(false);
        jenny.setArms(true);
        jenny.setSmall(false);
        jenny.setMarker(false);
        jenny.setInvulnerable(true);
        jenny.setCanPickupItems(false);
        
        // Apply custom skin
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        applySkinToSkull(meta);
        skull.setItemMeta(meta);
        jenny.getEquipment().setHelmet(skull);
        
        // No extra armor (skin shows through)
        jenny.getEquipment().setChestplate(null);
        jenny.getEquipment().setLeggings(null);
        jenny.getEquipment().setBoots(null);
        
        jenny.setCustomNameVisible(true);
        jenny.setCustomName(ChatColor.LIGHT_PURPLE + "Jenny " + ChatColor.GRAY + "[Right-click]");
        
        currentLocation = loc.clone();
    }
    
    private void applySkinToSkull(SkullMeta meta) {
        try {
            com.destroystokyo.paper.profile.PlayerProfile profile = 
                Bukkit.createProfile(UUID.fromString("1218acb4-2bc0-4368-b219-58e6bae64320"), "Jenny");
            profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", SKIN_VALUE, SKIN_SIGNATURE));
            meta.setPlayerProfile(profile);
        } catch (Exception e) {
            getLogger().warning("Skin failed: " + e.getMessage());
        }
    }
    
    // ========== AI TASKS (PORTED FROM OG) ==========
    private void updateAITasks() {
        if (jenny == null || jenny.isDead()) return;
        
        // Bed-finding logic (ported from OG goForDoggy)
        if (lookingForBed) {
            if (targetBedPos == null || bedSearchTick > 200) {
                lookingForBed = false;
                isDoingAction = true;
                bedSearchTick = 0;
                startDoggyOnBed();
            } else {
                bedSearchTick++;
                double distance = jenny.getLocation().distance(targetBedPos);
                if (distance < 0.6) {
                    lookingForBed = false;
                    isDoingAction = true;
                    startDoggyOnBed();
                }
            }
        }
        
        // Payment preparation (ported from prepareAction)
        if (isPreparingPayment) {
            preparingPaymentTick++;
            if (preparingPaymentTick > 40) {
                isPreparingPayment = false;
                preparingPaymentTick = 0;
                startActionAnimation(currentAction);
            } else {
                // Move smoothly toward target (simulated)
                if (currentCustomer != null) {
                    Location target = getInFrontOfPlayer(currentCustomer);
                    Vector direction = target.toVector().subtract(jenny.getLocation().toVector());
                    if (direction.length() > 0.1) {
                        direction = direction.normalize().multiply(0.15);
                        jenny.teleport(jenny.getLocation().add(direction));
                    }
                    // Face the player
                    lookAt(currentCustomer.getLocation());
                }
            }
        }
    }
    
    private Location getInFrontOfPlayer(Player player) {
        Location loc = player.getLocation();
        Vector direction = loc.getDirection().normalize();
        return loc.clone().add(direction.multiply(1.5));
    }
    
    private void lookAt(Location target) {
        if (jenny == null) return;
        Location jennyLoc = jenny.getLocation();
        double dx = target.getX() - jennyLoc.getX();
        double dz = target.getZ() - jennyLoc.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        jenny.setRotation(yaw, 0);
    }
    
    // ========== BED FINDING (OG goForDoggy logic) ==========
    private void goForDoggy(Player player) {
        Block bed = findNearestBed(jenny.getLocation());
        if (bed == null) {
            sayToAll("&7no bed in sight...");
            playSoundAround(Sound.ENTITY_VILLAGER_NO, 0.8f);
            return;
        }
        
        // OG algorithm: find free space around bed
        Location bedPos = bed.getLocation();
        double[][] potentialSpaces = {
            {0.5, 0, -0.5, 0},    // front
            {0.5, 0, 1.5, 180},   // back
            {-0.5, 0, 0.5, -90},  // left
            {1.5, 0, 0.5, 90}     // right
        };
        
        int bestIndex = -1;
        double bestDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < potentialSpaces.length; i++) {
            Location checkLoc = bedPos.clone().add(potentialSpaces[i][0], potentialSpaces[i][1], potentialSpaces[i][2]);
            if (checkLoc.getBlock().getType() == Material.AIR) {
                double dist = jenny.getLocation().distance(checkLoc);
                if (dist < bestDistance) {
                    bestDistance = dist;
                    bestIndex = i;
                }
            }
        }
        
        if (bestIndex == -1) {
            sayToAll("&7bed is obscured...");
            playSoundAround(Sound.ENTITY_VILLAGER_NO, 0.8f);
            return;
        }
        
        targetYaw = (float) potentialSpaces[bestIndex][3];
        targetBedPos = bedPos.clone().add(potentialSpaces[bestIndex][0], potentialSpaces[bestIndex][1], potentialSpaces[bestIndex][2]);
        lookingForBed = true;
        bedSearchTick = 0;
        
        sayToAll("&dJenny: &7Follow me~");
        playSoundAround(Sound.ENTITY_VILLAGER_YES, 1.0f);
    }
    
    private Block findNearestBed(Location from) {
        for (int radius = 1; radius <= 10; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    for (int y = -2; y <= 2; y++) {
                        Block block = from.clone().add(x, y, z).getBlock();
                        if (block.getType().toString().contains("BED")) {
                            return block;
                        }
                    }
                }
            }
        }
        return null;
    }
    
    // ========== PAYMENT & ACTION TRIGGERS (OG exact values) ==========
    private void prepareStrip(Player player) {
        currentAction = "strip";
        currentCustomer = player;
        isPreparingPayment = true;
        preparingPaymentTick = 0;
        sayToAll("&dJenny: &7Huh?");
        playSoundAround(Sound.ENTITY_VILLAGER_AMBIENT, 0.7f);
    }
    
    private void prepareBlowjob(Player player) {
        currentAction = "blowjob";
        currentCustomer = player;
        isPreparingPayment = true;
        preparingPaymentTick = 0;
        sayToAll("&dJenny: &7Oh? Give me the sucky sucky~");
        playSoundAround(Sound.ENTITY_VILLAGER_YES, 0.9f);
    }
    
    private void prepareDoggy(Player player) {
        currentAction = "doggy";
        currentCustomer = player;
        isPreparingPayment = true;
        preparingPaymentTick = 0;
        sayToAll("&dJenny: &7Give me the sex pls :)");
        playSoundAround(Sound.ENTITY_VILLAGER_TRADE, 0.8f);
    }
    
    private void startActionAnimation(String action) {
        isDoingAction = true;
        
        switch (action) {
            case "strip":
                sayToAll("&dJenny: &7Hihi~");
                playSoundAround(Sound.ENTITY_PLAYER_LEVELUP, 1.2f);
                // Simulate strip with particle effects
                jenny.getWorld().spawnParticle(Particle.CRIT, jenny.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0);
                break;
                
            case "blowjob":
                startBlowjobSequence();
                break;
                
            case "doggy":
                goForDoggy(currentCustomer);
                break;
        }
    }
    
    private void startBlowjobSequence() {
        if (currentCustomer == null) return;
        
        new BukkitRunnable() {
            int tick = 0;
            final int duration = 80; // 4 seconds
            
            @Override
            public void run() {
                if (tick >= duration || jenny == null || !currentCustomer.isOnline()) {
                    finishAction();
                    cancel();
                    return;
                }
                
                // Simulate BJ animation: head bobbing
                double heightOffset = Math.sin(tick * 0.6) * 0.1;
                Location loc = jenny.getLocation();
                loc.setY(loc.getY() + heightOffset);
                jenny.teleport(loc);
                
                // Particles at mouth level
                jenny.getWorld().spawnParticle(Particle.HEART, 
                    jenny.getLocation().add(0, 1.2, 0.3), 2, 0.2, 0.1, 0.2, 0);
                
                // Sound effects at specific ticks (OG timing)
                if (tick == 10) {
                    playSoundAround(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f);
                    sayToAll("&dJenny: &7*slurp*");
                }
                if (tick == 30) {
                    playSoundAround(Sound.ENTITY_SLIME_SQUISH, 0.6f);
                }
                if (tick == 50) {
                    playSoundAround(Sound.ENTITY_VILLAGER_YES, 0.7f);
                    sayToAll("&dJenny: &7Mmm~");
                }
                if (tick == 70) {
                    playSoundAround(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f);
                }
                
                tick++;
            }
        }.runTaskTimer(this, 0L, 1L);
    }
    
    private void startDoggyOnBed() {
        if (currentCustomer == null || targetBedPos == null) return;
        
        // Teleport Jenny to bed position
        jenny.teleport(targetBedPos);
        jenny.setRotation(targetYaw, 0);
        
        new BukkitRunnable() {
            int tick = 0;
            int flipCount = 0;
            
            @Override
            public void run() {
                if (tick >= 120 || jenny == null || !currentCustomer.isOnline()) {
                    finishAction();
                    cancel();
                    return;
                }
                
                // OG doggy animation simulation: rhythmic thrust counter-movement
                double bounce = Math.sin(tick * 0.8) * 0.05;
                Location loc = jenny.getLocation();
                loc.add(0, bounce, 0);
                jenny.teleport(loc);
                
                // Particles and sounds
                jenny.getWorld().spawnParticle(Particle.HEART, 
                    jenny.getLocation().add(0, 0.8, 0.5), 3, 0.2, 0.2, 0.2, 0);
                
                if (tick % 15 == 0) {
                    playSoundAround(Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.4f);
                }
                if (tick % 30 == 0) {
                    flipCount++;
                    if (flipCount % 2 == 0) {
                        playSoundAround(Sound.ENTITY_VILLAGER_TRADE, 0.6f);
                    } else {
                        playSoundAround(Sound.ENTITY_VILLAGER_WORK_ARMORER, 0.5f);
                    }
                }
                
                tick++;
            }
        }.runTaskTimer(this, 0L, 1L);
    }
    
    private void finishAction() {
        isDoingAction = false;
        currentAction = "";
        lookingForBed = false;
        targetBedPos = null;
        
        // Reset Jenny's position and name
        if (jenny != null) {
            jenny.setCustomName(ChatColor.LIGHT_PURPLE + "Jenny " + ChatColor.GRAY + "[Right-click]");
            playSoundAround(Sound.ENTITY_PLAYER_LEVELUP, 1.0f);
        }
        
        if (currentCustomer != null) {
            sayTo(currentCustomer, "&dJenny: &7That was wonderful~ ❤");
            // Small reward (OG gave 0.02 cum percentage, here give saturation)
            currentCustomer.setSaturation(Math.min(20, currentCustomer.getSaturation() + 6));
        }
        
        currentCustomer = null;
    }
    
    // ========== RIGHT-CLICK INTERACTION (OG processInteract) ==========
    @EventHandler
    public void onRightClick(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand)) return;
        if (!event.getRightClicked().equals(jenny)) return;
        
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        // Name tag check (OG behavior)
        if (item.getType() == Material.NAME_TAG) {
            event.setCancelled(true);
            // Let nametag work normally
            return;
        }
        
        // Payment system: check for gold, emerald, diamond
        Material material = item.getType();
        PaymentItems payment = null;
        
        switch (material) {
            case GOLD_INGOT:
                payment = PaymentItems.GOLD;
                break;
            case EMERALD:
                payment = PaymentItems.EMERALD;
                break;
            case DIAMOND:
                payment = PaymentItems.DIAMOND;
                break;
            default:
                // No payment item, just open menu
                openJennyMenu(player);
                return;
        }
        
        // Check if player has enough quantity
        if (item.getAmount() < payment.amount) {
            player.sendMessage(ChatColor.RED + "Jenny: " + ChatColor.GRAY + "You need " + payment.amount + " " + 
                material.toString().toLowerCase() + " for that...");
            return;
        }
        
        // Cooldown check (30 seconds, OG had no explicit cooldown but let's add)
        if (actionCooldowns.containsKey(player.getUniqueId()) &&
            System.currentTimeMillis() - actionCooldowns.get(player.getUniqueId()) < 30000) {
            long remaining = (30000 - (System.currentTimeMillis() - actionCooldowns.get(player.getUniqueId()))) / 1000;
            player.sendMessage(ChatColor.RED + "Jenny needs a rest... " + remaining + " seconds.");
            return;
        }
        
        // Consume payment
        item.setAmount(item.getAmount() - payment.amount);
        actionCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        
        // Trigger action (OG exact mapping)
        switch (payment) {
            case GOLD:
                prepareStrip(player);
                break;
            case EMERALD:
                prepareBlowjob(player);
                break;
            case DIAMOND:
                prepareDoggy(player);
                break;
        }
        
        player.sendMessage(ChatColor.GREEN + "Jenny accepts your payment~");
    }
    
    private void openJennyMenu(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "=== Jenny Menu ===");
        player.sendMessage(ChatColor.YELLOW + "1 Gold Ingot" + ChatColor.GRAY + " - Strip");
        player.sendMessage(ChatColor.YELLOW + "3 Emeralds" + ChatColor.GRAY + " - Blowjob");
        player.sendMessage(ChatColor.YELLOW + "2 Diamonds" + ChatColor.GRAY + " - Doggy");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Hold the payment item and right-click Jenny!");
    }
    
    // ========== HELPER METHODS ==========
    private void sayToAll(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }
    
    private void sayTo(Player player, String message) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
    
    private void playSoundAround(Sound sound, float pitch) {
        if (jenny != null) {
            jenny.getWorld().playSound(jenny.getLocation(), sound, 0.8f, pitch);
        }
    }
    
    // ========== COMMANDS ==========
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        
        if (args.length == 0) {
            player.sendMessage(ChatColor.GOLD + "=== Jenny Commands ===");
            player.sendMessage(ChatColor.YELLOW + "/jenny spawn" + ChatColor.GRAY + " - Spawn Jenny");
            player.sendMessage(ChatColor.YELLOW + "/jenny remove" + ChatColor.GRAY + " - Remove Jenny");
            player.sendMessage(ChatColor.YELLOW + "/jenny here" + ChatColor.GRAY + " - Teleport Jenny to you");
            player.sendMessage(ChatColor.YELLOW + "/jenny follow" + ChatColor.GRAY + " - Make Jenny follow");
            player.sendMessage(ChatColor.YELLOW + "/jenny stop" + ChatColor.GRAY + " - Stop following");
            player.sendMessage(ChatColor.YELLOW + "/jenny bed" + ChatColor.GRAY + " - Find nearest bed");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "spawn":
                spawnJenny(player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Jenny spawned!");
                break;
            case "remove":
                if (jenny != null) jenny.remove();
                jenny = null;
                player.sendMessage(ChatColor.RED + "Jenny removed.");
                break;
            case "here":
                if (jenny == null) spawnJenny(player.getLocation());
                else jenny.teleport(player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Jenny teleported to you!");
                break;
            case "follow":
                if (jenny == null) spawnJenny(player.getLocation());
                currentFollowingPlayer = player.getUniqueId();
                player.sendMessage(ChatColor.GREEN + "Jenny is now following you!");
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (jenny == null || currentFollowingPlayer == null) {
                            cancel();
                            return;
                        }
                        Player target = Bukkit.getPlayer(currentFollowingPlayer);
                        if (target == null || !target.isOnline()) {
                            cancel();
                            return;
                        }
                        Location targetLoc = target.getLocation();
                        Location jennyLoc = jenny.getLocation();
                        if (jennyLoc.distance(targetLoc) > 2.5) {
                            Vector dir = targetLoc.toVector().subtract(jennyLoc.toVector()).normalize();
                            jenny.teleport(jennyLoc.add(dir.multiply(0.3)));
                            lookAt(targetLoc);
                        }
                    }
                }.runTaskTimer(this, 0L, 2L);
                break;
            case "stop":
                currentFollowingPlayer = null;
                player.sendMessage(ChatColor.YELLOW + "Jenny stopped following.");
                break;
            case "bed":
                Block bed = findNearestBed(jenny != null ? jenny.getLocation() : player.getLocation());
                if (bed == null) player.sendMessage(ChatColor.RED + "No bed found nearby!");
                else player.sendMessage(ChatColor.GREEN + "Bed found at " + bed.getLocation().getBlockX() + ", " + bed.getLocation().getBlockY() + ", " + bed.getLocation().getBlockZ());
                break;
        }
        return true;
    }
}
