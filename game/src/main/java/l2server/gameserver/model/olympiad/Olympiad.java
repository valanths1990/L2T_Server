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

/*
  @author godson
 */

package l2server.gameserver.model.olympiad;

import gnu.trove.TIntIntHashMap;
import l2server.Config;
import l2server.DatabasePool;
import l2server.gameserver.Announcements;
import l2server.gameserver.ThreadPoolManager;
import l2server.gameserver.instancemanager.GlobalVariablesManager;
import l2server.gameserver.instancemanager.ZoneManager;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.network.SystemMessageId;
import l2server.gameserver.network.serverpackets.SystemMessage;
import l2server.gameserver.util.Broadcast;
import l2server.util.loader.annotations.Load;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

public class Olympiad {
	private static Logger log = LoggerFactory.getLogger(Olympiad.class.getName());

	private Map<Integer, OlympiadNobleInfo> nobles;
	private TIntIntHashMap noblesRank;

	public static final String OLYMPIAD_HTML_PATH = "olympiad/";
	private static final String OLYMPIAD_LOAD_NOBLES = "SELECT olympiad_nobles.charId, olympiad_nobles.class_id, " +
			"characters.char_name, olympiad_nobles.olympiad_points, olympiad_nobles.competitions_done, " +
			"olympiad_nobles.competitions_won, olympiad_nobles.competitions_lost, olympiad_nobles.competitions_drawn, " +
			"olympiad_nobles.competitions_classed, olympiad_nobles.competitions_nonclassed, olympiad_nobles.competitions_teams, " +
			"olympiad_nobles.settled FROM olympiad_nobles, characters WHERE characters.charId = olympiad_nobles.charId";
	private static final String OLYMPIAD_SAVE_NOBLES =
			"INSERT INTO olympiad_nobles " + "(`charId`,`class_id`,`olympiad_points`,`competitions_done`,`competitions_won`,`competitions_lost`," +
					"`competitions_drawn`,`competitions_classed`,`competitions_nonclassed`,`settled`) VALUES (?,?,?,?,?,?,?,?,?,?)";
	private static final String OLYMPIAD_UPDATE_NOBLES = "UPDATE olympiad_nobles SET " +
			"olympiad_points = ?, competitions_done = ?, competitions_won = ?, competitions_lost = ?, competitions_drawn = ?, " +
			"competitions_classed = ?, competitions_nonclassed = ?, settled = ? WHERE charId = ?";
	private static final String OLYMPIAD_DELETE_NOBLE = "DELETE FROM olympiad_nobles WHERE charId = ? LIMIT 1";
	private static final String OLYMPIAD_GET_HEROES = "SELECT charId FROM olympiad_nobles " + "WHERE class_id = ? AND competitions_done >= " +
			(Config.isServer(Config.TENKAI_LEGACY) ? 5 : 10) + " AND competitions_won > 0 " +
			"ORDER BY olympiad_points DESC, competitions_done DESC, competitions_won DESC";
	private static final String GET_ALL_CLASSIFIED_NOBLES = "SELECT charId FROM olympiad_nobles_eom " +
			"WHERE competitions_done >= 10 AND competitions_won > 0 ORDER BY olympiad_points DESC, competitions_done DESC, competitions_won DESC";
	private static final String GET_EACH_CLASS_LEADER = "SELECT characters.char_name FROM olympiad_nobles_eom, characters " +
			"WHERE characters.charId = olympiad_nobles_eom.charId AND olympiad_nobles_eom.class_id = ? " +
			"AND olympiad_nobles_eom.competitions_done >= 10 AND olympiad_nobles_eom.competitions_won > 0 " +
			"ORDER BY olympiad_nobles_eom.olympiad_points DESC, olympiad_nobles_eom.competitions_done DESC, olympiad_nobles_eom.competitions_won DESC LIMIT 10";
	private static final String GET_EACH_CLASS_LEADER_CURRENT = "SELECT characters.char_name FROM olympiad_nobles, characters " +
			"WHERE characters.charId = olympiad_nobles.charId AND olympiad_nobles.class_id = ? " +
			"AND olympiad_nobles.competitions_done >= 10 AND olympiad_nobles.competitions_won > 0 " +
			"ORDER BY olympiad_nobles.olympiad_points DESC, olympiad_nobles.competitions_done DESC, olympiad_nobles.competitions_won DESC LIMIT 10";

	private static final String OLYMPIAD_MONTH_CLEAR = "DELETE FROM olympiad_nobles_eom";
	private static final String OLYMPIAD_MONTH_CREATE = "INSERT INTO olympiad_nobles_eom SELECT * FROM olympiad_nobles";
	private static final String OLYMPIAD_DELETE_ALL = "DELETE FROM olympiad_nobles";

	private static final int[] HERO_IDS = {
			// Regular Classes
			88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117,
			118, 131, 132, 133, 134, 186, 187,

			// Awakened Classes
			148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173, 174,
			175, 176, 177, 178, 179, 180, 181, 188, 189};

	public static final int COMP_START = Config.ALT_OLY_START_TIME; // 6PM
	public static final int COMP_MIN = Config.ALT_OLY_MIN; // 00 mins
	public static final long COMP_PERIOD = Config.ALT_OLY_CPERIOD; // 6 hours
	public static final long WEEKLY_PERIOD = Config.ALT_OLY_WPERIOD; // 1 week
	public static final long VALIDATION_PERIOD = 86400000; // 24 hours

	public static final int DEFAULT_POINTS = 10;
	public static final int WEEKLY_POINTS = 10;

	public static final int BASE_INSTANCE_ID = 5000;

	public static final int MAX_WEEKLY_MATCHES = 30;

	public static final String CHAR_ID = "charId";
	public static final String CLASS_ID = "class_id";
	public static final String CHAR_NAME = "char_name";
	public static final String POINTS = "olympiad_points";
	public static final String COMP_DONE = "competitions_done";
	public static final String COMP_WON = "competitions_won";
	public static final String COMP_LOST = "competitions_lost";
	public static final String COMP_DRAWN = "competitions_drawn";
	public static final String COMP_CLASSED = "competitions_classed";
	public static final String COMP_NONCLASSED = "competitions_nonclassed";
	public static final String SETTLED = "settled";

	protected long olympiadEnd;
	protected long validationEnd;

	protected long nextWeeklyChange;
	protected int currentCycle;
	private long compEnd;
	private Calendar compStart;
	protected static boolean inCompPeriod = false;
	protected static boolean compStarted = false;
	protected ScheduledFuture<?> scheduledCompStart;
	protected ScheduledFuture<?> scheduledCompEnd;
	protected ScheduledFuture<?> scheduledOlympiadEnd;
	protected ScheduledFuture<?> scheduledWeeklyTask;
	protected ScheduledFuture<?> gameManager = null;
	protected ScheduledFuture<?> gameAnnouncer = null;

	public static Olympiad getInstance() {
		return SingletonHolder.instance;
	}

	private Olympiad() {
		initialize();
	}
	
	@Load(dependencies = GlobalVariablesManager.class)
	public void initialize() {
		if (Config.IS_CLASSIC) {
			return;
		}
		
		load();
		
		init();
	}
	
	private void load() {
		nobles = new HashMap<>();

		String olyData = GlobalVariablesManager.getInstance().getStoredVariable("olympiadData");

		if (olyData != null) {
			currentCycle = Integer.valueOf(olyData.split(";")[0]);
			olympiadEnd = Long.valueOf(olyData.split(";")[1]);
			validationEnd = Long.valueOf(olyData.split(";")[2]);
			nextWeeklyChange = Long.valueOf(olyData.split(";")[3]);
		} else {
			currentCycle = 0;
			olympiadEnd = 0;
			validationEnd = 0;
			nextWeeklyChange = 0;
		}

		if (olympiadEnd == 0 || olympiadEnd < Calendar.getInstance().getTimeInMillis()) {
			setNewOlympiadEnd();
		} else {
			scheduleWeeklyChange();
		}

		Connection con = null;

		try {
			con = DatabasePool.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(OLYMPIAD_LOAD_NOBLES);
			ResultSet rset = statement.executeQuery();

			while (rset.next()) {
				int charId = rset.getInt(CHAR_ID);
				OlympiadNobleInfo oni = new OlympiadNobleInfo(charId,
						rset.getString(CHAR_NAME),
						rset.getInt(CLASS_ID),
						rset.getInt(POINTS),
						rset.getInt(COMP_DONE),
						rset.getInt(COMP_WON),
						rset.getInt(COMP_LOST),
						rset.getInt(COMP_DRAWN),
						rset.getInt(COMP_CLASSED),
						rset.getInt(COMP_NONCLASSED),
						rset.getBoolean(SETTLED));

				nobles.put(charId, oni);
			}

			rset.close();
			statement.close();
		} catch (Exception e) {
			log.warn("Error loading noblesse data from database: ", e);
		} finally {
			DatabasePool.close(con);
		}

		synchronized (this) {
			log.debug("Loading Olympiad System....");

			long milliToEnd = getMillisToOlympiadEnd();
			log.info("" + Math.round(milliToEnd / 60000) + " minutes until period ends");

			milliToEnd = getMillisToWeekChange();
			log.info("Next weekly change is in " + Math.round(milliToEnd / 60000) + " minutes");
		}

		log.info("Loaded " + nobles.size() + " Nobles");
	}

	public void loadNoblesRank() {
		noblesRank = new TIntIntHashMap();
		TIntIntHashMap tmpPlace = new TIntIntHashMap();

		Connection con = null;
		try {
			con = DatabasePool.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(GET_ALL_CLASSIFIED_NOBLES);
			ResultSet rset = statement.executeQuery();

			int place = 1;
			while (rset.next()) {
				tmpPlace.put(rset.getInt(CHAR_ID), place++);
			}

			rset.close();
			statement.close();
		} catch (Exception e) {
			log.warn("Error loading noblesse data from database for Ranking: ", e);
		} finally {
			DatabasePool.close(con);
		}
		int rank1 = (int) Math.round(tmpPlace.size() * 0.01);
		int rank2 = (int) Math.round(tmpPlace.size() * 0.10);
		int rank3 = (int) Math.round(tmpPlace.size() * 0.25);
		int rank4 = (int) Math.round(tmpPlace.size() * 0.50);
		if (rank1 == 0) {
			rank1 = 1;
			rank2++;
			rank3++;
			rank4++;
		}
		for (int charId : tmpPlace.keys()) {
			if (tmpPlace.get(charId) <= rank1) {
				noblesRank.put(charId, 1);
			} else if (tmpPlace.get(charId) <= rank2) {
				noblesRank.put(charId, 2);
			} else if (tmpPlace.get(charId) <= rank3) {
				noblesRank.put(charId, 3);
			} else if (tmpPlace.get(charId) <= rank4) {
				noblesRank.put(charId, 4);
			} else {
				noblesRank.put(charId, 5);
			}
		}
	}

	protected void init() {
		compStart = Calendar.getInstance();
		// Make sure that it is on weekend
		int day = compStart.get(Calendar.DAY_OF_WEEK);
		if (day != Calendar.FRIDAY && day != Calendar.SATURDAY && day != Calendar.SUNDAY) {
			while (compStart.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
				compStart.add(Calendar.DAY_OF_MONTH, 1);
			}
		}

		compStart.set(Calendar.HOUR_OF_DAY, COMP_START);
		compStart.set(Calendar.MINUTE, COMP_MIN);

		compEnd = compStart.getTimeInMillis() + COMP_PERIOD;

		if (scheduledOlympiadEnd != null) {
			scheduledOlympiadEnd.cancel(true);
		}

		scheduledOlympiadEnd = ThreadPoolManager.getInstance().scheduleGeneral(new OlympiadEndTask(), getMillisToOlympiadEnd());

		updateCompStatus();

		loadNoblesRank();
	}

	protected class OlympiadEndTask implements Runnable {
		@Override
		public void run() {
			SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.OLYMPIAD_PERIOD_S1_HAS_ENDED);
			sm.addNumber(currentCycle);
			Announcements.getInstance().announceToAll(sm);

			if (scheduledWeeklyTask != null) {
				scheduledWeeklyTask.cancel(true);
			}

			saveNobleData();

			HeroesManager.getInstance().computeNewHeroes(getHeroesToBe());

			saveOlympiadStatus();
			updateMonthlyData();

			Calendar validationEnd = Calendar.getInstance();
			Olympiad.this.validationEnd = validationEnd.getTimeInMillis() + VALIDATION_PERIOD;

			currentCycle++;
			deleteNobles();
			setNewOlympiadEnd();
			init();
		}
	}

	public void endOlympiads() {
		inCompPeriod = true;

		Announcements.getInstance().announceToAll(SystemMessage.getSystemMessage(SystemMessageId.THE_OLYMPIAD_GAME_HAS_STARTED));
		log.info("Olympiad Game Started");

		gameManager = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(OlympiadGameManager.getInstance(), 30000, 30000);
		if (Config.ALT_OLY_ANNOUNCE_GAMES) {
			gameAnnouncer = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new OlympiadAnnouncer(), 30000, 500);
		}

		/*
		SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.OLYMPIAD_PERIOD_S1_HAS_ENDED);
		sm.addNumber(currentCycle);
		Announcements.getInstance().announceToAll(sm);

		if (scheduledWeeklyTask != null)
			scheduledWeeklyTask.cancel(true);

		saveNobleData();

		HeroesManager.getInstance().computeNewHeroes(getHeroesToBe());

		saveOlympiadStatus();
		updateMonthlyData();

		Calendar validationEnd = Calendar.getInstance();
		validationEnd = validationEnd.getTimeInMillis() + VALIDATION_PERIOD;

		currentCycle++;
		deleteNobles();
		setNewOlympiadEnd();
		init();*/
	}

	protected int getNobleCount() {
		return nobles.size();
	}

	public OlympiadNobleInfo getNobleInfo(int playerId) {
		return nobles.get(playerId);
	}

	public void addNoble(int playerId, OlympiadNobleInfo set) {
		nobles.put(playerId, set);
	}

	public void removeNoble(int playerId) {
		nobles.remove(playerId);
		
		Connection con = DatabasePool.getInstance().getConnection();
		try {
			PreparedStatement statement = con.prepareStatement(OLYMPIAD_DELETE_NOBLE);
			statement.setInt(1, playerId);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				con.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void updateCompStatus() {
		// compStarted = false;

		long milliToStart = getMillisToCompBegin();

		double numSecs = milliToStart / 1000 % 60;
		double countDown = (milliToStart / 1000 - numSecs) / 60;
		int numMins = (int) Math.floor(countDown % 60);
		countDown = (countDown - numMins) / 60;
		int numHours = (int) Math.floor(countDown % 24);
		int numDays = (int) Math.floor((countDown - numHours) / 24);

		log.info("Competition Period Starts in " + numDays + " days, " + numHours + " hours and " + numMins + " mins.");

		log.info("Event starts/started : " + compStart.getTime());

		scheduledCompStart = ThreadPoolManager.getInstance().scheduleGeneral(new CompStartTask(), getMillisToCompBegin());
	}

	private long getMillisToOlympiadEnd() {
		// if (olympiadEnd > Calendar.getInstance().getTimeInMillis())
		return olympiadEnd - Calendar.getInstance().getTimeInMillis();
		// return 10L;
	}

	public void manualSelectHeroes() {
		if (scheduledOlympiadEnd != null) {
			scheduledOlympiadEnd.cancel(true);
		}

		scheduledOlympiadEnd = ThreadPoolManager.getInstance().scheduleGeneral(new OlympiadEndTask(), 0);
	}

	protected long getMillisToValidationEnd() {
		if (validationEnd > Calendar.getInstance().getTimeInMillis()) {
			return validationEnd - Calendar.getInstance().getTimeInMillis();
		}
		return 10L;
	}

	protected void setNewOlympiadEnd() {
		SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.OLYMPIAD_PERIOD_S1_HAS_STARTED);
		sm.addNumber(currentCycle);

		Announcements.getInstance().announceToAll(sm);

		Calendar endTime = Calendar.getInstance();
		if (Config.isServer(Config.TENKAI_LEGACY)) {
			endTime.add(Calendar.DAY_OF_WEEK, 7);
			endTime.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
		} else {
			endTime.add(Calendar.MONTH, 1);
			endTime.set(Calendar.DAY_OF_MONTH, 1);
		}
		endTime.set(Calendar.AM_PM, Calendar.AM);
		endTime.set(Calendar.HOUR, 12);
		endTime.set(Calendar.MINUTE, 0);
		endTime.set(Calendar.SECOND, 0);
		olympiadEnd = endTime.getTimeInMillis();

		Calendar nextChange = Calendar.getInstance();
		nextWeeklyChange = nextChange.getTimeInMillis() + WEEKLY_PERIOD;
		scheduleWeeklyChange();
	}

	public boolean inCompPeriod() {
		return inCompPeriod;
	}

	private long getMillisToCompBegin() {
		if (compStart.getTimeInMillis() < Calendar.getInstance().getTimeInMillis() && compEnd > Calendar.getInstance().getTimeInMillis()) {
			return 10L;
		}

		if (compStart.getTimeInMillis() > Calendar.getInstance().getTimeInMillis()) {
			return compStart.getTimeInMillis() - Calendar.getInstance().getTimeInMillis();
		}

		return setNewCompBegin();
	}

	private long setNewCompBegin() {
		compStart = Calendar.getInstance();
		// Make sure that it is on weekend
		int day = compStart.get(Calendar.DAY_OF_WEEK);
		if (day != Calendar.FRIDAY && day != Calendar.SATURDAY) {
			while (compStart.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
				compStart.add(Calendar.DAY_OF_MONTH, 1);
			}
		}

		compStart.set(Calendar.HOUR_OF_DAY, COMP_START);
		compStart.set(Calendar.MINUTE, COMP_MIN);

		compStart.add(Calendar.HOUR_OF_DAY, 24);
		compEnd = compStart.getTimeInMillis() + COMP_PERIOD;

		log.info("New Schedule @ " + compStart.getTime());

		return compStart.getTimeInMillis() - Calendar.getInstance().getTimeInMillis();
	}

	protected long getMillisToCompEnd() {
		// if (compEnd > Calendar.getInstance().getTimeInMillis())
		return compEnd - Calendar.getInstance().getTimeInMillis();
		// return 10L;
	}

	private long getMillisToWeekChange() {
		if (nextWeeklyChange > Calendar.getInstance().getTimeInMillis()) {
			return nextWeeklyChange - Calendar.getInstance().getTimeInMillis();
		}
		return 10L;
	}

	private void scheduleWeeklyChange() {
		scheduledWeeklyTask = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(() -> {
			addWeeklyPoints();
			log.info("Added weekly points to nobles");

			Calendar nextChange = Calendar.getInstance();
			nextWeeklyChange = nextChange.getTimeInMillis() + WEEKLY_PERIOD;
		}, getMillisToWeekChange(), WEEKLY_PERIOD);
	}

	protected synchronized void addWeeklyPoints() {
		for (Integer nobleId : nobles.keySet()) {
			OlympiadNobleInfo nobleInfo = nobles.get(nobleId);
			nobleInfo.addWeeklyPoints(WEEKLY_POINTS);
		}
	}

	public int getCurrentCycle() {
		return currentCycle;
	}

	public boolean playerInStadia(Player player) {
		return ZoneManager.getInstance().getOlympiadStadium(player) != null;
	}

	/**
	 * Save noblesse data to database
	 */
	protected synchronized void saveNobleData() {
		if (nobles == null || nobles.isEmpty()) {
			return;
		}
		
		Connection con = DatabasePool.getInstance().getConnection();
		PreparedStatement statement;

		for (Integer nobleId : nobles.keySet()) {
			try {
				OlympiadNobleInfo nobleInfo = nobles.get(nobleId);
				if (nobleInfo == null) {
					continue;
				}

				if (nobleInfo.isToSave()) {
					statement = con.prepareStatement(OLYMPIAD_SAVE_NOBLES);
					statement.setInt(1, nobleId);
					statement.setInt(2, nobleInfo.getClassId());
					statement.setInt(3, nobleInfo.getPoints());
					statement.setInt(4, nobleInfo.getMatches());
					statement.setInt(5, nobleInfo.getVictories());
					statement.setInt(6, nobleInfo.getDefeats());
					statement.setInt(7, nobleInfo.getDraws());
					statement.setInt(8, nobleInfo.getClassedMatches());
					statement.setInt(9, nobleInfo.getNonClassedMatches());
					statement.setBoolean(10, nobleInfo.isSettled());

					nobleInfo.setToSave(false);
				} else {
					statement = con.prepareStatement(OLYMPIAD_UPDATE_NOBLES);
					statement.setInt(1, nobleInfo.getPoints());
					statement.setInt(2, nobleInfo.getMatches());
					statement.setInt(3, nobleInfo.getVictories());
					statement.setInt(4, nobleInfo.getDefeats());
					statement.setInt(5, nobleInfo.getDraws());
					statement.setInt(6, nobleInfo.getClassedMatches());
					statement.setInt(7, nobleInfo.getNonClassedMatches());
					statement.setBoolean(8, nobleInfo.isSettled());
					statement.setInt(9, nobleId);
				}

				statement.execute();
				statement.close();
			} catch (SQLException e) {
				log.error("Failed to save noblesse data to database: ", e);

				OlympiadNobleInfo nobleInfo = nobles.get(nobleId);
				if (nobleInfo != null) {
					nobleInfo.setToSave(false);
				}
			}
		}
		
		DatabasePool.close(con);
	}

	/**
	 * Save olympiad.properties file with current olympiad status and update noblesse table in database
	 */
	public void saveOlympiadStatus() {
		saveNobleData();
		String data = currentCycle + ";" + olympiadEnd + ";" + validationEnd + ";" + nextWeeklyChange;
		GlobalVariablesManager.getInstance().storeVariable("olympiadData", data);
	}

	protected void updateMonthlyData() {
		Connection con = null;
		try {
			con = DatabasePool.getInstance().getConnection();
			PreparedStatement statement;

			statement = con.prepareStatement(OLYMPIAD_MONTH_CLEAR);
			statement.execute();
			statement.close();
			statement = con.prepareStatement(OLYMPIAD_MONTH_CREATE);
			statement.execute();
			statement.close();
		} catch (SQLException e) {
			log.error("Failed to update monthly noblese data: ", e);
		} finally {
			DatabasePool.close(con);
		}
	}

	private Map<Integer, OlympiadNobleInfo> getHeroesToBe() {
		Map<Integer, OlympiadNobleInfo> heroesToBe = new LinkedHashMap<>();
		Connection con = null;

		try {
			con = DatabasePool.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(OLYMPIAD_GET_HEROES);
			for (int classId : HERO_IDS) {
				statement.setInt(1, classId);
				ResultSet rset = statement.executeQuery();
				statement.clearParameters();

				if (rset.next()) {
					OlympiadNobleInfo hero = getNobleInfo(rset.getInt(CHAR_ID));
					heroesToBe.put(classId, hero);
				}

				rset.close();
			}
			statement.close();
		} catch (SQLException e) {
			log.warn("Couldnt load heros from DB");
		} finally {
			DatabasePool.close(con);
		}

		return heroesToBe;
	}

	// TODO: That's a fucking ugly patch
	public int getPosition(Player player) {
		int position = 1;
		for (String name : getClassLeaderBoard(player.getBaseClass())) {
			if (name.equalsIgnoreCase(player.getName())) {
				return position;
			}

			position++;
		}

		return 0;
	}

	public List<String> getClassLeaderBoard(int classId) {
		// if (period != 1) return;

		List<String> names = new ArrayList<>();

		Connection con = null;

		try {
			con = DatabasePool.getInstance().getConnection();
			PreparedStatement statement;
			if (Config.ALT_OLY_SHOW_MONTHLY_WINNERS) {
				statement = con.prepareStatement(GET_EACH_CLASS_LEADER);
			} else {
				statement = con.prepareStatement(GET_EACH_CLASS_LEADER_CURRENT);
			}

			statement.setInt(1, classId);
			ResultSet rset = statement.executeQuery();

			while (rset.next()) {
				names.add(rset.getString(CHAR_NAME));
			}

			statement.close();
			rset.close();

			return names;
		} catch (SQLException e) {
			log.warn("Couldnt load olympiad leaders from DB");
		} finally {
			DatabasePool.close(con);
		}

		return names;
	}

	public int getTokensCount(Player player, boolean clear) {
		if (noblesRank.isEmpty()) {
			player.sendMessage("Noble Ranks Empty");
			return 0;
		}
        /*
		if (!player.getName().equals("iStab"))
		{
			player.sendMessage("Try again shortly.");
			return 0;
		}*/
		/*
		if (player.getInventory().getItemByItemId(13722) != null && player.getInventory().getItemByItemId(13722).getCount() > 1000)
		{
			player.sendMessage("You already received your Olympiad Tokens.");
			return 0;
		}*/

		int objId = player.getObjectId();

		//OlympiadNobleInfo noble = nobles.get(objId);
		//if (noble == null || noble.isSettled())
		//	return 0;

		int rank = noblesRank.containsKey(objId) ? noblesRank.get(objId) : 0;
		int points = getLastNobleOlympiadPoints(objId);
		if (points == 0) {
			player.sendMessage("You already retrieved your Tokens.");
			return 0;
		}

		if (player.isHero()) {
			points += Config.ALT_OLY_HERO_POINTS;
		}
		switch (rank) {
			case 1:
				points += Config.ALT_OLY_RANK1_POINTS;
				break;
			case 2:
				points += Config.ALT_OLY_RANK2_POINTS;
				break;
			case 3:
				points += Config.ALT_OLY_RANK3_POINTS;
				break;
			case 4:
				points += Config.ALT_OLY_RANK4_POINTS;
				break;
			case 5:
				points += Config.ALT_OLY_RANK5_POINTS;
		}

		if (clear) {
			Connection con = null;

			try {
				con = DatabasePool.getInstance().getConnection();
				PreparedStatement statement;
				statement = con.prepareStatement("UPDATE olympiad_nobles_eom SET gotTokens = 1 WHERE charId = ?");
				statement.setInt(1, objId);
				statement.execute();
				statement.close();
			} catch (Exception e) {
				log.warn("Could not save nobless settled:", e);
			} finally {
				DatabasePool.close(con);
			}
		}

		points *= Config.ALT_OLY_TOKENS_PER_POINT;

		Broadcast.toGameMasters(player.getName() + " retrieved " + points + " Olympiad Tokens.");
		return points;
	}

	public int getLastNobleOlympiadPoints(int objId) {
		int result = 0;
		Connection con = null;
		try {
			con = DatabasePool.getInstance().getConnection();
			PreparedStatement statement;
			statement = con.prepareStatement("SELECT olympiad_points FROM olympiad_nobles_eom WHERE charId = ? AND gotTokens = 0");
			statement.setInt(1, objId);
			ResultSet rs = statement.executeQuery();
			if (rs.first()) {
				result = rs.getInt(1);
			}
			rs.close();
			statement.close();
		} catch (Exception e) {
			log.warn("Could not load last olympiad points:", e);
		} finally {
			DatabasePool.close(con);
		}

		return result;
	}

	protected void deleteNobles() {
		Connection con = null;

		try {
			con = DatabasePool.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(OLYMPIAD_DELETE_ALL);
			statement.execute();
			statement.close();
		} catch (SQLException e) {
			log.warn("Couldn't delete nobles from DB");
		} finally {
			DatabasePool.close(con);
		}

		nobles.clear();
	}

	public void olyBan(int playerId) {
		OlympiadNobleInfo playerStat = getNobleInfo(playerId);
		if (playerStat != null) {
			playerStat.setPoints(Math.abs(playerStat.getPoints()) * -1);
		}
	}

	public void olyUnban(int playerId) {
		OlympiadNobleInfo playerStat = getNobleInfo(playerId);
		if (playerStat != null) {
			playerStat.setPoints(Math.abs(playerStat.getPoints()));
		}
	}

	private class CompStartTask implements Runnable {
		@Override
		public void run() {
			inCompPeriod = true;

			Announcements.getInstance().announceToAll(SystemMessage.getSystemMessage(SystemMessageId.THE_OLYMPIAD_GAME_HAS_STARTED));
			log.info("Olympiad Game Started");

			gameManager = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(OlympiadGameManager.getInstance(), 30000, 30000);
			if (Config.ALT_OLY_ANNOUNCE_GAMES) {
				gameAnnouncer = ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new OlympiadAnnouncer(), 30000, 500);
			}

			long compEndEnd = getMillisToCompEnd();
			if (compEndEnd > 600000) {
				ThreadPoolManager.getInstance()
						.scheduleGeneral(() -> Announcements.getInstance()
										.announceToAll(SystemMessage.getSystemMessage(SystemMessageId.OLYMPIAD_REGISTRATION_PERIOD_ENDED)),
								compEndEnd - 600000);
			}

			scheduledCompEnd = ThreadPoolManager.getInstance().scheduleGeneral(new CompEndTask(), compEndEnd);
		}
	}

	private class CompEndTask implements Runnable {
		@Override
		public void run() {
			inCompPeriod = false;
			Announcements.getInstance().announceToAll(SystemMessage.getSystemMessage(SystemMessageId.THE_OLYMPIAD_GAME_HAS_ENDED));
			log.info("Olympiad Game Ended");

			while (OlympiadGameManager.getInstance().isBattleStarted()) // cleared in game manager
			{
				try {
					// wait 1 minute for end of pending games
					Thread.sleep(60000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if (gameManager != null) {
				gameManager.cancel(false);
				gameManager = null;
			}

			if (gameAnnouncer != null) {
				gameAnnouncer.cancel(false);
				gameAnnouncer = null;
			}

			saveOlympiadStatus();

			init();
		}
	}

	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder {
		protected static final Olympiad instance = new Olympiad();
	}
}
