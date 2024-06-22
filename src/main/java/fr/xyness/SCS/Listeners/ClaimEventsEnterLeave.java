package fr.xyness.SCS.Listeners;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import fr.xyness.SCS.CPlayer;
import fr.xyness.SCS.CPlayerMain;
import fr.xyness.SCS.ClaimMain;
import fr.xyness.SCS.SimpleClaimSystem;
import fr.xyness.SCS.Config.ClaimLanguage;
import fr.xyness.SCS.Config.ClaimSettings;
import fr.xyness.SCS.Guis.*;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;

public class ClaimEventsEnterLeave implements Listener {
	
	
	// ***************
	// *  Variables  *
	// ***************
	
	
	private static Map<Player,BossBar> bossBars = new HashMap<>();
	
	
	// ******************
	// *  EventHandler  *
	// ******************
	
	
	// Register the player and update his bossbar
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		CPlayerMain.addPlayerPermSetting(player);
		if(player.hasPermission("scs.admin")) {
			if(SimpleClaimSystem.isUpdateAvailable()) {
				player.sendMessage(SimpleClaimSystem.getUpdateMessage());
			}
		}
		Chunk chunk = player.getLocation().getChunk();
		boolean checkClaim = ClaimMain.checkIfClaimExists(chunk);
		if(checkClaim && !ClaimMain.canPermCheck(chunk, "Weather")) {
			player.setPlayerWeather(WeatherType.CLEAR);
		}
		if(ClaimSettings.getBooleanSetting("bossbar")) {
			BossBar b = Bukkit.getServer().createBossBar("", BarColor.valueOf(ClaimSettings.getSetting("bossbar-color")), BarStyle.SOLID);
			bossBars.put(player, b);
			b.setVisible(false);
			b.addPlayer(player);
			if(checkClaim){
				String owner = ClaimMain.getOwnerInClaim(chunk);
	        	if(owner.equals("admin")) {
	        		b.setTitle(ClaimSettings.getSetting("bossbar-protected-area-message").replaceAll("%player%", player.getName()).replaceAll("%name%", ClaimMain.getClaimNameByChunk(chunk)));
	        		b.setVisible(true);
	            	return;
	        	}
	        	if(owner.equals(player.getName())) {
	        		b.setTitle(ClaimSettings.getSetting("bossbar-owner-message").replaceAll("%owner%", owner).replaceAll("%player%", player.getName()).replaceAll("%name%", ClaimMain.getClaimNameByChunk(chunk)));
	        		b.setVisible(true);
	            	return;
	        	}
	        	if(ClaimMain.checkMembre(chunk, player)) {
	        		b.setTitle(ClaimSettings.getSetting("bossbar-member-message").replaceAll("%player%", player.getName()).replaceAll("%owner%", owner).replaceAll("%name%", ClaimMain.getClaimNameByChunk(chunk)));
	        		b.setVisible(true);
	            	return;
	        	}
	        	String message = ClaimSettings.getSetting("bossbar-visitor-message").replaceAll("%player%", player.getName()).replaceAll("%owner%", owner).replaceAll("%name%", ClaimMain.getClaimNameByChunk(chunk));
	        	b.setTitle(message);
	        	b.setVisible(true);
	        	return;
			}
		}
	}
	
	// Delete the player's bossbar and clear his data
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		player.resetPlayerWeather();
		CPlayerMain.removeCPlayer(player.getName());
		if(bossBars.containsKey(player)) bossBars.remove(player);
	}
	
	// Update his bossbar and send enabled messages on teleport
	@EventHandler
	public void onPlayerTeleport(PlayerTeleportEvent event) {
    	Chunk to = event.getTo().getChunk();
    	if(!ClaimMain.checkIfClaimExists(to)) return;
    	Player player = event.getPlayer();
    	if(ClaimMain.checkBan(to, player.getName())) {
    		event.setCancelled(true);
    		ClaimMain.sendMessage(player, ClaimLanguage.getMessage("player-banned"),ClaimSettings.getSetting("protection-message"));
    		return;
    	}
    	if(!CPlayerMain.checkPermPlayer(player, "scs.bypass") && !ClaimMain.checkMembre(to, player) && !ClaimMain.canPermCheck(to, "Teleportations")) {
    		PlayerTeleportEvent.TeleportCause cause = event.getCause();
            switch (cause) {
                case ENDER_PEARL:
                case CHORUS_FRUIT:
                	ClaimMain.sendMessage(player,ClaimLanguage.getMessage("teleportations"),ClaimSettings.getSetting("protection-message"));
                    event.setCancelled(true);
                    return;
            }
    	}
		Chunk from = event.getFrom().getChunk();
		if(!ClaimMain.canPermCheck(to, "Weather")) {
			player.setPlayerWeather(WeatherType.CLEAR);
		} else {
			player.resetPlayerWeather();
		}
    	String ownerTO = ClaimMain.getOwnerInClaim(to);
    	String ownerFROM = ClaimMain.getOwnerInClaim(from);
    	String playerName = player.getName();
    	CPlayer cPlayer = CPlayerMain.getCPlayer(playerName);
    	if(cPlayer.getClaimAutofly() && (ownerTO.equals(playerName) || ClaimMain.canPermCheck(to, "Fly")) && !SimpleClaimSystem.isFolia()) {
    		CPlayerMain.activePlayerFly(player);
    		if(ClaimSettings.getBooleanSetting("claim-fly-message-auto-fly")) ClaimMain.sendMessage(player, ClaimLanguage.getMessage("fly-enabled"), "CHAT");
    	} else if (!ClaimMain.canPermCheck(to, "Fly") && !ownerTO.equals(playerName) && cPlayer.getClaimFly() && !SimpleClaimSystem.isFolia()) {
    		CPlayerMain.removePlayerFly(player);
    		if(ClaimSettings.getBooleanSetting("claim-fly-message-auto-fly")) ClaimMain.sendMessage(player, ClaimLanguage.getMessage("fly-disabled"), "CHAT");
    	}
    	if(ClaimSettings.getBooleanSetting("bossbar")) bossbarMessages(player,to,ownerTO);
    	String world = player.getWorld().getName();
		if(!ownerTO.equals(ownerFROM)) {
	    	if(ClaimSettings.getBooleanSetting("enter-leave-messages")) enterleaveMessages(player,to,from,ownerTO,ownerFROM);
	    	if(cPlayer.getClaimAutoclaim()) {
	            if(ClaimSettings.isWorldDisabled(world)) {
	            	player.sendMessage(ClaimLanguage.getMessage("autoclaim-world-disabled").replaceAll("%world%", world));
	            	cPlayer.setClaimAutoclaim(false);
	            } else {
	            	ClaimMain.createClaim(player, to);	
	            }
	    	}
		}
		if(cPlayer.getClaimAutomap()) {
            if(ClaimSettings.isWorldDisabled(world)) {
            	player.sendMessage(ClaimLanguage.getMessage("automap-world-disabled").replaceAll("%world%", world));
            	cPlayer.setClaimAutomap(false);
            } else {
            	ClaimMain.getMap(player,to);	
            }
		}
	}
	
	// Update his bossbar and send enabled messages on respawn
	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		Player player = event.getPlayer();
		Chunk to = event.getRespawnLocation().getChunk();
		if(!ClaimMain.checkIfClaimExists(to)) return;
		if(!ClaimMain.canPermCheck(to, "Weather")) {
			player.setPlayerWeather(WeatherType.CLEAR);
		} else {
			player.resetPlayerWeather();
		}
    	String ownerTO = ClaimMain.getOwnerInClaim(to);
    	if(ClaimSettings.getBooleanSetting("bossbar")) bossbarMessages(player,to,ownerTO);
    	String playerName = player.getName();
    	CPlayer cPlayer = CPlayerMain.getCPlayer(playerName);
    	if(cPlayer.getClaimAutofly() && (ownerTO.equals(playerName) || ClaimMain.canPermCheck(to, "Fly")) && !SimpleClaimSystem.isFolia()) {
    		CPlayerMain.activePlayerFly(player);
    		if(ClaimSettings.getBooleanSetting("claim-fly-message-auto-fly")) ClaimMain.sendMessage(player, ClaimLanguage.getMessage("fly-enabled"), "CHAT");
    	} else if (!ClaimMain.canPermCheck(to, "Fly") && !ownerTO.equals(playerName) && cPlayer.getClaimFly() && !SimpleClaimSystem.isFolia()) {
    		CPlayerMain.removePlayerFly(player);
    		if(ClaimSettings.getBooleanSetting("claim-fly-message-auto-fly")) ClaimMain.sendMessage(player, ClaimLanguage.getMessage("fly-disabled"), "CHAT");
    	}
    	String world = player.getWorld().getName();
    	if(cPlayer.getClaimAutoclaim()) {
            if(ClaimSettings.isWorldDisabled(world)) {
            	player.sendMessage(ClaimLanguage.getMessage("autoclaim-world-disabled").replaceAll("%world%", world));
            	cPlayer.setClaimAutoclaim(false);
            } else {
            	ClaimMain.createClaim(player, to);	
            }
    	}
		if(cPlayer.getClaimAutomap()) {
            if(ClaimSettings.isWorldDisabled(world)) {
            	player.sendMessage(ClaimLanguage.getMessage("automap-world-disabled").replaceAll("%world%", world));
            	cPlayer.setClaimAutomap(false);
            } else {
            	ClaimMain.getMap(player,to);	
            }
		}
	}
	
	// Update his bossbar and send enabled messages on changing chunk
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (hasChangedChunk(event)) {
        	Chunk to = event.getTo().getChunk();
        	Chunk from = event.getFrom().getChunk();
        	String ownerTO = ClaimMain.getOwnerInClaim(to);
        	String ownerFROM = ClaimMain.getOwnerInClaim(from);
        	Player player = event.getPlayer();
        	String playerName = player.getName();
        	CPlayer cPlayer = CPlayerMain.getCPlayer(playerName);
        	if(ClaimMain.checkBan(to, player.getName())) {
        		player.teleport(event.getFrom());
        		ClaimMain.sendMessage(player, ClaimLanguage.getMessage("player-banned"),"ACTION_BAR");
        		return;
        	}
    		if(ClaimMain.checkIfClaimExists(to) && !ClaimMain.canPermCheck(to, "Weather")) {
    			player.setPlayerWeather(WeatherType.CLEAR);
    		} else if (ClaimMain.checkIfClaimExists(from) && !ClaimMain.canPermCheck(from, "Weather")){
    			player.resetPlayerWeather();
    		}
        	if(cPlayer.getClaimAutofly() && (ownerTO.equals(playerName) || ClaimMain.canPermCheck(to, "Fly")) && !SimpleClaimSystem.isFolia()) {
        		CPlayerMain.activePlayerFly(player);
        		if(ClaimSettings.getBooleanSetting("claim-fly-message-auto-fly")) ClaimMain.sendMessage(player, ClaimLanguage.getMessage("fly-enabled"), "CHAT");
        	} else if (!ClaimMain.canPermCheck(to, "Fly") && !ownerTO.equals(playerName) && cPlayer.getClaimFly() && !SimpleClaimSystem.isFolia()) {
        		CPlayerMain.removePlayerFly(player);
        		if(ClaimSettings.getBooleanSetting("claim-fly-message-auto-fly")) ClaimMain.sendMessage(player, ClaimLanguage.getMessage("fly-disabled"), "CHAT");
        	}
        	if(ClaimSettings.getBooleanSetting("bossbar")) bossbarMessages(player,to,ownerTO);
        	String world = player.getWorld().getName();
        	if(cPlayer.getClaimAutoclaim()) {
                if(ClaimSettings.isWorldDisabled(world)) {
                	player.sendMessage(ClaimLanguage.getMessage("autoclaim-world-disabled").replaceAll("%world%", world));
                	cPlayer.setClaimAutoclaim(false);
                } else {
                	ClaimMain.createClaim(player, to);	
                }
        	}
    		if(cPlayer.getClaimAutomap()) {
                if(ClaimSettings.isWorldDisabled(world)) {
                	player.sendMessage(ClaimLanguage.getMessage("automap-world-disabled").replaceAll("%world%", world));
                	cPlayer.setClaimAutomap(false);
                } else {
                	ClaimMain.getMap(player,to);	
                }
    		}
        	if(ownerTO.equals(ownerFROM)) return;
        	if(ClaimSettings.getBooleanSetting("enter-leave-messages")) enterleaveMessages(player,to,from,ownerTO,ownerFROM);
        	if(ClaimSettings.getBooleanSetting("enter-leave-chat-messages")) enterleaveChatMessages(player,to,from,ownerTO,ownerFROM);
        	if(ClaimSettings.getBooleanSetting("enter-leave-title-messages")) enterleavetitleMessages(player,to,from,ownerTO,ownerFROM);
        }
    }
    
    
	// ********************
	// *  Others Methods  *
	// ********************
    
    
	// Update the color of all bossbars
	public static void setBossBarColor(BarColor color){
		bossBars.values().forEach(b -> b.setColor(color));
	}
	
	// Send the claim enter message to the player (chat)
    private void enterleaveChatMessages(Player player, Chunk to, Chunk from, String ownerTO, String ownerFROM) {
    	if(ClaimMain.checkIfClaimExists(to)) {
        	if(ownerTO.equals("admin")) {
        		player.sendMessage(ClaimLanguage.getMessage("enter-protected-area-chat").replaceAll("%name%", ClaimMain.getClaimNameByChunk(to)));
            	return;
        	}
        	String message = ClaimLanguage.getMessage("enter-territory-chat").replaceAll("%owner%", ownerTO).replaceAll("%player%", player.getName()).replaceAll("%name%", ClaimMain.getClaimNameByChunk(to));
        	player.sendMessage(message);
        	return;
        }
        if(ClaimMain.checkIfClaimExists(from)) {
        	if(ownerFROM.equals("admin")) {
        		player.sendMessage(ClaimLanguage.getMessage("leave-protected-area-chat").replaceAll("%name%", ClaimMain.getClaimNameByChunk(from)));
            	return;
        	}
        	String message = ClaimLanguage.getMessage("leave-territory-chat").replaceAll("%owner%", ownerFROM).replaceAll("%player%", player.getName()).replaceAll("%name%", ClaimMain.getClaimNameByChunk(from));
        	player.sendMessage(message);
        	return;
        }
    }
    
    // Send the claim enter message to the player (action bar)
    private void enterleaveMessages(Player player, Chunk to, Chunk from, String ownerTO, String ownerFROM) {
    	if(ClaimMain.checkIfClaimExists(to)) {
        	if(ownerTO.equals("admin")) {
        		ClaimMain.sendMessage(player,ClaimLanguage.getMessage("enter-protected-area").replaceAll("%name%", ClaimMain.getClaimNameByChunk(to)),"ACTION_BAR");
            	return;
        	}
        	String message = ClaimLanguage.getMessage("enter-territory").replaceAll("%owner%", ownerTO).replaceAll("%player%", player.getName()).replaceAll("%name%", ClaimMain.getClaimNameByChunk(to));
        	ClaimMain.sendMessage(player,message,"ACTION_BAR");
        	return;
        }
        if(ClaimMain.checkIfClaimExists(from)) {
        	if(ownerFROM.equals("admin")) {
        		ClaimMain.sendMessage(player,ClaimLanguage.getMessage("leave-protected-area").replaceAll("%name%", ClaimMain.getClaimNameByChunk(from)),"ACTION_BAR");
            	return;
        	}
        	String message = ClaimLanguage.getMessage("leave-territory").replaceAll("%owner%", ownerFROM).replaceAll("%player%", player.getName()).replaceAll("%name%", ClaimMain.getClaimNameByChunk(from));
        	ClaimMain.sendMessage(player,message,"ACTION_BAR");
        	return;
        }
    }
    
    // Send the claim enter message to the player (title)
    private void enterleavetitleMessages(Player player, Chunk to, Chunk from, String ownerTO, String ownerFROM) {
    	if(ClaimMain.checkIfClaimExists(to)) {
        	if(ownerTO.equals("admin")) {
        		player.sendTitle(ClaimLanguage.getMessage("enter-protected-area-title").replaceAll("%name%", ClaimMain.getClaimNameByChunk(to)), ClaimLanguage.getMessage("enter-protected-area-subtitle").replaceAll("%name%", ClaimMain.getClaimNameByChunk(to)), 5, 25, 5);
            	return;
        	}
        	player.sendTitle(ClaimLanguage.getMessage("enter-territory-title").replaceAll("%owner%", ownerTO).replaceAll("%player%", player.getName()).replaceAll("%name%", ClaimMain.getClaimNameByChunk(to)), ClaimLanguage.getMessage("enter-territory-subtitle").replaceAll("%owner%", ownerTO).replaceAll("%player%", player.getName()).replaceAll("%name%", ClaimMain.getClaimNameByChunk(to)), 5, 25, 5);
        	return;
        }
        if(ClaimMain.checkIfClaimExists(from)) {
        	if(ownerFROM.equals("admin")) {
        		player.sendTitle(ClaimLanguage.getMessage("leave-protected-area-title").replaceAll("%name%", ClaimMain.getClaimNameByChunk(from)), ClaimLanguage.getMessage("leave-protected-area-subtitle").replaceAll("%name%", ClaimMain.getClaimNameByChunk(from)), 5, 25, 5);
            	return;
        	}
        	player.sendTitle(ClaimLanguage.getMessage("leave-territory-title").replaceAll("%owner%", ownerFROM).replaceAll("%player%", player.getName()).replaceAll("%name%", ClaimMain.getClaimNameByChunk(from)), ClaimLanguage.getMessage("leave-territory-subtitle").replaceAll("%owner%", ownerFROM).replaceAll("%player%", player.getName()).replaceAll("%name%", ClaimMain.getClaimNameByChunk(from)), 5, 25, 5);
        	return;
        }
    }
    
    // Method to check if the player has a bossbar
    public static BossBar checkBossBar(Player player) {
		BossBar b;
		if(bossBars.containsKey(player)) {
			b = bossBars.get(player);
			return b;
		}
		b = Bukkit.getServer().createBossBar("", BarColor.valueOf(ClaimSettings.getSetting("bossbar-color")), BarStyle.SOLID);
		bossBars.put(player, b);
		b.addPlayer(player);
		return b;
    }
    
    // Update the bossbar message
    public static void bossbarMessages(Player player, Chunk to, String ownerTO) {
    	if(!ClaimSettings.getBooleanSetting("bossbar")) return;
    	if(ClaimMain.checkIfClaimExists(to)) {
    		BossBar b = checkBossBar(player);
        	if(ownerTO.equals("admin")) {
        		b.setTitle(ClaimSettings.getSetting("bossbar-protected-area-message").replaceAll("%name%", ClaimMain.getClaimNameByChunk(to)));
        		b.setVisible(true);
            	return;
        	}
        	if(ownerTO.equals(player.getName())) {
        		b.setTitle(ClaimSettings.getSetting("bossbar-owner-message").replaceAll("%owner%", ownerTO).replaceAll("%name%", ClaimMain.getClaimNameByChunk(to)));
        		b.setVisible(true);
            	return;
        	}
        	if(ClaimMain.checkMembre(to, player)) {
        		b.setTitle(ClaimSettings.getSetting("bossbar-member-message").replaceAll("%player%", player.getName()).replaceAll("%owner%", ownerTO).replaceAll("%name%", ClaimMain.getClaimNameByChunk(to)));
        		b.setVisible(true);
            	return;
        	}
        	String message = ClaimSettings.getSetting("bossbar-visitor-message").replaceAll("%player%", player.getName()).replaceAll("%owner%", ownerTO).replaceAll("%name%", ClaimMain.getClaimNameByChunk(to));
        	b.setTitle(message);
        	b.setVisible(true);
        	return;
        }
    	bossBars.get(player).setVisible(false);
    	return;
    }

    // Check if the player has changed chunk
    private boolean hasChangedChunk(PlayerMoveEvent event) {
        int fromChunkX = event.getFrom().getChunk().getX();
        int fromChunkZ = event.getFrom().getChunk().getZ();
        int toChunkX = event.getTo().getChunk().getX();
        int toChunkZ = event.getTo().getChunk().getZ();
        return fromChunkX != toChunkX || fromChunkZ != toChunkZ;
    }
    
    // Method to active the bossbar of a player
    public static void activeBossBar(Player player, Chunk chunk) {
    	if(!ClaimSettings.getBooleanSetting("bossbar")) {
    		bossBars.get(player).setVisible(false);
    		return;
    	}
    	if(player == null) return;
    	if(ClaimMain.checkIfClaimExists(chunk)) {
    		BossBar b = checkBossBar(player);
    		String owner = ClaimMain.getOwnerInClaim(chunk);
        	if(owner.equals("admin")) {
        		b.setTitle(ClaimSettings.getSetting("bossbar-protected-area-message").replaceAll("%name%", ClaimMain.getClaimNameByChunk(chunk)));
        		b.setVisible(true);
            	return;
        	}
        	if(owner.equals(player.getName())) {
        		b.setTitle(ClaimSettings.getSetting("bossbar-owner-message").replaceAll("%owner%", owner).replaceAll("%name%", ClaimMain.getClaimNameByChunk(chunk)));
        		b.setVisible(true);
            	return;
        	}
        	if(ClaimMain.checkMembre(player.getLocation().getChunk(), player)) {
        		b.setTitle(ClaimSettings.getSetting("bossbar-member-message").replaceAll("%player%", player.getName()).replaceAll("%owner%", owner).replaceAll("%name%", ClaimMain.getClaimNameByChunk(chunk)));
        		b.setVisible(true);
            	return;
        	}
        	String message = ClaimSettings.getSetting("bossbar-visitor-message").replaceAll("%player%", player.getName()).replaceAll("%owner%", owner).replaceAll("%name%", ClaimMain.getClaimNameByChunk(chunk));
        	b.setTitle(message);
        	b.setVisible(true);
        	return;
        } else {
        	bossBars.get(player).setVisible(false);
        }
    }
    
    // Method to disable the bossbar of a player
    public static void disableBossBar(Player player) {
    	if(!ClaimSettings.getBooleanSetting("bossbar")) return;
    	if(player == null) return;
    	BossBar b = checkBossBar(player);
    	b.setVisible(false);
    }
	
}