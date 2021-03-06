/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package l2server.gameserver.instancemanager;

import gnu.trove.TIntIntHashMap;
import l2server.Config;
import l2server.gameserver.ThreadPoolManager;
import l2server.gameserver.events.instanced.EventsManager;
import l2server.gameserver.model.actor.CreatureZone;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.model.entity.BlockCheckerEngine;
import l2server.gameserver.model.itemcontainer.PcInventory;
import l2server.gameserver.model.olympiad.OlympiadManager;
import l2server.gameserver.network.SystemMessageId;
import l2server.gameserver.network.serverpackets.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * @author BiggBoss
 */
public final class HandysBlockCheckerManager {
	/*
	 * This class manage the player add/remove, team change and
	 * event arena status, as the clearance of the participants
	 * list or liberate the arena
	 */

	// All the participants and their team classifed by arena
	private static ArenaParticipantsHolder[] arenaPlayers;

	// Arena votes to start the game
	private static TIntIntHashMap arenaVotes = new TIntIntHashMap();

	// Arena Status, True = is being used, otherwise, False
	private static HashMap<Integer, Boolean> arenaStatus;

	// Registration request penalty (10 seconds)
	private static ArrayList<Integer> registrationPenalty = new ArrayList<>();

	/**
	 * Return the number of event-start votes for the spcified
	 * arena id
	 *
	 * @return int (number of votes)
	 */
	public synchronized int getArenaVotes(int arenaId) {
		return arenaVotes.get(arenaId);
	}

	/**
	 * Add a new vote to start the event for the specified
	 * arena id
	 *
	 */
	public synchronized void increaseArenaVotes(int arena) {
		int newVotes = arenaVotes.get(arena) + 1;
		ArenaParticipantsHolder holder = arenaPlayers[arena];

		if (newVotes > holder.getAllPlayers().size() / 2 && !holder.getEvent().isStarted()) {
			clearArenaVotes(arena);
			if (holder.getBlueTeamSize() == 0 || holder.getRedTeamSize() == 0) {
				return;
			}
			if (Config.HBCE_FAIR_PLAY) {
				holder.checkAndShuffle();
			}
			ThreadPoolManager.getInstance().executeTask(holder.getEvent().new StartEvent());
		} else {
			arenaVotes.put(arena, newVotes);
		}
	}

	/**
	 * Will clear the votes queue (of event start) for the
	 * specified arena id
	 *
	 */
	public synchronized void clearArenaVotes(int arena) {
		arenaVotes.put(arena, 0);
	}

	private HandysBlockCheckerManager() {
		// Initialize arena status
		if (arenaStatus == null) {
			arenaStatus = new HashMap<>();
			arenaStatus.put(0, false);
			arenaStatus.put(1, false);
			arenaStatus.put(2, false);
			arenaStatus.put(3, false);
		}
	}

	/**
	 * Returns the players holder
	 *
	 * @return ArenaParticipantsHolder
	 */
	public ArenaParticipantsHolder getHolder(int arena) {
		return arenaPlayers[arena];
	}

	/**
	 * Initializes the participants holder
	 */
	public void startUpParticipantsQueue() {
		arenaPlayers = new ArenaParticipantsHolder[4];

		for (int i = 0; i < 4; ++i) {
			arenaPlayers[i] = new ArenaParticipantsHolder(i);
		}
	}

	/**
	 * Add the player to the specified arena (throught the specified
	 * arena manager) and send the needed server ->  client packets
	 *
	 */
	public boolean addPlayerToArena(Player player, int arenaId) {
		ArenaParticipantsHolder holder = arenaPlayers[arenaId];

		synchronized (holder) {
			boolean isRed;

			for (int i = 0; i < 4; i++) {
				if (arenaPlayers[i].getAllPlayers().contains(player)) {
					SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.C1_IS_ALREADY_REGISTERED_ON_THE_MATCH_WAITING_LIST);
					msg.addCharName(player);
					player.sendPacket(msg);
					return false;
				}
			}

			if (player.isCursedWeaponEquipped()) {
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANNOT_REGISTER_PROCESSING_CURSED_WEAPON));
				return false;
			}

			if (EventsManager.getInstance().isPlayerParticipant(player.getObjectId()) || player.isInOlympiadMode()) {
				player.sendMessage("Couldnt register you due other event participation");
				return false;
			}

			if (OlympiadManager.getInstance().isRegistered(player)) {
				OlympiadManager.getInstance().unRegisterNoble(player);
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.COLISEUM_OLYMPIAD_KRATEIS_APPLICANTS_CANNOT_PARTICIPATE));
			}
            /*
			if (UnderGroundColiseum.getInstance().isRegisteredPlayer(player))
			{
				UngerGroundColiseum.getInstance().removeParticipant(player);
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.COLISEUM_OLYMPIAD_KRATEIS_APPLICANTS_CANNOT_PARTICIPATE));
			}
			if (KrateiCubeManager.getInstance().isRegisteredPlayer(player))
			{
				KrateiCubeManager.getInstance().removeParticipant(player);
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.COLISEUM_OLYMPIAD_KRATEIS_APPLICANTS_CANNOT_PARTICIPATE));
			}
			 */

			if (registrationPenalty.contains(player.getObjectId())) {
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.CANNOT_REQUEST_REGISTRATION_10_SECS_AFTER));
				return false;
			}

			if (holder.getBlueTeamSize() < holder.getRedTeamSize()) {
				holder.addPlayer(player, 1);
				isRed = false;
			} else {
				holder.addPlayer(player, 0);
				isRed = true;
			}
			holder.broadCastPacketToTeam(new ExCubeGameAddPlayer(player, isRed));
			return true;
		}
	}

	/**
	 * Will remove the specified player from the specified
	 * team and arena and will send the needed packet to all
	 * his team mates / enemy team mates
	 *
	 */
	public void removePlayer(Player player, int arenaId, int team) {
		ArenaParticipantsHolder holder = arenaPlayers[arenaId];
		synchronized (holder) {
			boolean isRed = team == 0;

			holder.removePlayer(player, team);
			holder.broadCastPacketToTeam(new ExCubeGameRemovePlayer(player, isRed));

			// End event if theres an empty team
			int teamSize = isRed ? holder.getRedTeamSize() : holder.getBlueTeamSize();
			if (teamSize == 0) {
				holder.getEvent().endEventAbnormally();
			}

			Integer objId = player.getObjectId();
			if (!registrationPenalty.contains(objId)) {
				registrationPenalty.add(objId);
			}
			schedulePenaltyRemoval(objId);
		}
	}

	/**
	 * Will change the player from one team to other (if possible)
	 * and will send the needed packets
	 *
	 */
	public void changePlayerToTeam(Player player, int arena, int team) {
		ArenaParticipantsHolder holder = arenaPlayers[arena];

		synchronized (holder) {
			boolean isFromRed = holder.redPlayers.contains(player);

			if (isFromRed && holder.getBlueTeamSize() == 6) {
				player.sendMessage("The team is full");
				return;
			} else if (!isFromRed && holder.getRedTeamSize() == 6) {
				player.sendMessage("The team is full");
				return;
			}

			int futureTeam = isFromRed ? 1 : 0;
			holder.addPlayer(player, futureTeam);

			if (isFromRed) {
				holder.removePlayer(player, 0);
			} else {
				holder.removePlayer(player, 1);
			}
			holder.broadCastPacketToTeam(new ExCubeGameChangeTeam(player, isFromRed));
		}
	}

	/**
	 * Will erase all participants from the specified holder
	 *
	 */
	public synchronized void clearPaticipantQueueByArenaId(int arenaId) {
		arenaPlayers[arenaId].clearPlayers();
	}

	/**
	 * Returns true if arena is holding an event at this momment
	 *
	 * @return boolean
	 */
	public boolean arenaIsBeingUsed(int arenaId) {
		if (arenaId < 0 || arenaId > 3) {
			return false;
		}
		return arenaStatus.get(arenaId);
	}

	/**
	 * Set the specified arena as being used
	 *
	 */
	public void setArenaBeingUsed(int arenaId) {
		arenaStatus.put(arenaId, true);
	}

	/**
	 * Set as free the specified arena for future
	 * events
	 *
	 */
	public void setArenaFree(int arenaId) {
		arenaStatus.put(arenaId, false);
	}

	/**
	 * Called when played logs out while participating
	 * in Block Checker Event
	 */
	public void onDisconnect(Player player) {
		int arena = player.getBlockCheckerArena();
		int team = getHolder(arena).getPlayerTeam(player);
		HandysBlockCheckerManager.getInstance().removePlayer(player, arena, team);
		if (player.getTeam() > 0) {
			player.stopAllEffects();
			// Remove team aura
			player.setTeam(0);

			// Remove the event items
			PcInventory inv = player.getInventory();

			if (inv.getItemByItemId(13787) != null) {
				long count = inv.getInventoryItemCount(13787, 0);
				inv.destroyItemByItemId("Handys Block Checker", 13787, count, player, player);
			}
			if (inv.getItemByItemId(13788) != null) {
				long count = inv.getInventoryItemCount(13788, 0);
				inv.destroyItemByItemId("Handys Block Checker", 13788, count, player, player);
			}
			player.setInsideZone(CreatureZone.ZONE_PVP, false);
			// Teleport Back
			player.teleToLocation(-57478, -60367, -2370);
		}
	}

	public static HandysBlockCheckerManager getInstance() {
		return SingletonHolder.instance;
	}

	private static class SingletonHolder {
		private static HandysBlockCheckerManager instance = new HandysBlockCheckerManager();
	}

	public class ArenaParticipantsHolder {
		int arena;
		List<Player> redPlayers;
		List<Player> bluePlayers;
		BlockCheckerEngine engine;

		public ArenaParticipantsHolder(int arena) {
			this.arena = arena;
			redPlayers = new ArrayList<>(6);
			bluePlayers = new ArrayList<>(6);
			engine = new BlockCheckerEngine(this, arena);
		}

		public List<Player> getRedPlayers() {
			return redPlayers;
		}

		public List<Player> getBluePlayers() {
			return bluePlayers;
		}

		public ArrayList<Player> getAllPlayers() {
			ArrayList<Player> all = new ArrayList<>(12);
			all.addAll(redPlayers);
			all.addAll(bluePlayers);
			return all;
		}

		public void addPlayer(Player player, int team) {
			if (team == 0) {
				redPlayers.add(player);
			} else {
				bluePlayers.add(player);
			}
		}

		public void removePlayer(Player player, int team) {
			if (team == 0) {
				redPlayers.remove(player);
			} else {
				bluePlayers.remove(player);
			}
		}

		public int getPlayerTeam(Player player) {
			if (redPlayers.contains(player)) {
				return 0;
			} else if (bluePlayers.contains(player)) {
				return 1;
			} else {
				return -1;
			}
		}

		public int getRedTeamSize() {
			return redPlayers.size();
		}

		public int getBlueTeamSize() {
			return bluePlayers.size();
		}

		public void broadCastPacketToTeam(L2GameServerPacket packet) {
			for (Player p : redPlayers) {
				p.sendPacket(packet);
			}
			for (Player p : bluePlayers) {
				p.sendPacket(packet);
			}
		}

		public void clearPlayers() {
			redPlayers.clear();
			bluePlayers.clear();
		}

		public BlockCheckerEngine getEvent() {
			return engine;
		}

		public void updateEvent() {
			engine.updatePlayersOnStart(this);
		}

		private void checkAndShuffle() {
			int redSize = redPlayers.size();
			int blueSize = bluePlayers.size();
			if (redSize > blueSize + 1) {
				broadCastPacketToTeam(SystemMessage.getSystemMessage(SystemMessageId.TEAM_ADJUSTED_BECAUSE_WRONG_POPULATION_RATIO));
				int needed = redSize - (blueSize + 1);
				for (int i = 0; i < needed + 1; i++) {
					Player plr = redPlayers.get(i);
					if (plr == null) {
						continue;
					}
					changePlayerToTeam(plr, arena, 1);
				}
			} else if (blueSize > redSize + 1) {
				broadCastPacketToTeam(SystemMessage.getSystemMessage(SystemMessageId.TEAM_ADJUSTED_BECAUSE_WRONG_POPULATION_RATIO));
				int needed = blueSize - (redSize + 1);
				for (int i = 0; i < needed + 1; i++) {
					Player plr = bluePlayers.get(i);
					if (plr == null) {
						continue;
					}
					changePlayerToTeam(plr, arena, 0);
				}
			}
		}
	}

	private void schedulePenaltyRemoval(int objId) {
		ThreadPoolManager.getInstance().scheduleGeneral(new PenaltyRemove(objId), 10000);
	}

	private class PenaltyRemove implements Runnable {
		Integer objectId;

		public PenaltyRemove(Integer id) {
			objectId = id;
		}

		@Override
		public void run() {
			try {
				registrationPenalty.remove(objectId);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
