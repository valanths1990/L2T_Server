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

package retail.PriestOfBlessing;

import l2server.gameserver.instancemanager.QuestManager;
import l2server.gameserver.model.actor.Npc;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.model.quest.Quest;
import l2server.gameserver.model.quest.QuestState;
import l2server.gameserver.model.quest.State;
import l2server.gameserver.network.SystemMessageId;
import l2server.gameserver.network.serverpackets.NpcHtmlMessage;
import l2server.gameserver.network.serverpackets.SystemMessage;
import l2server.gameserver.util.Util;
import l2server.util.Rnd;

/**
 * * @author Gnacik
 */
public class PriestOfBlessing extends Quest {
	// Spawn state
	private static boolean spawned = false;
	// NPC
	private static final int priest = 32783;
	// Prices
	private static final int _price_voice = 100000;
	//
	private static final int _nevit_voice = 17094;
	//
	private static final int[] _prices_hourglass = {4000, 30000, 110000, 310000, 970000, 2160000, 5000000};
	//
	private static final int[][] hourglasses =
			{{17095, 17096, 17097, 17098, 17099}, {17100, 17101, 17102, 17103, 17104}, {17105, 17106, 17107, 17108, 17109},
					{17110, 17111, 17112, 17113, 17114}, {17115, 17116, 17117, 17118, 17119}, {17120, 17121, 17122, 17123, 17124},
					{17125, 17126, 17127, 17128, 17129}};
	// Spawns
	private static final int[][] spawns =
			{{-83112, 150922, -3120, 2280}, {-13931, 121938, -2984, 30212}, {87127, -141330, -1336, 49153}, {43520, -47590, -792, 43738},
					{148060, -55314, -2728, 40961}, {82433, 53285, -1488, 22942}, {111171, 221053, -3544, 2058}, {15907, 142901, -2688, 14324}};

	@Override
	public String onAdvEvent(String event, Npc npc, Player player) {
		String htmltext = event;

		QuestState st = player.getQuestState(getName());
		Quest q = QuestManager.getInstance().getQuest(getName());
		if (st == null || q == null) {
			return null;
		}

		if (event.equalsIgnoreCase("buy_voice")) {
			if (st.getQuestItemsCount(57) >= _price_voice) {
				String value = q.loadGlobalQuestVar(player.getAccountName() + "_voice");
				long _reuse_time = value == "" ? 0 : Long.parseLong(value);

				if (System.currentTimeMillis() > _reuse_time) {
					st.setState(State.STARTED);
					st.takeItems(57, _price_voice);
					st.giveItems(_nevit_voice, 1);
					q.saveGlobalQuestVar(player.getAccountName() + "_voice", Long.toString(System.currentTimeMillis() + 20 * 3600000));
				} else {
					long remainingTime = (_reuse_time - System.currentTimeMillis()) / 1000;
					int hours = (int) (remainingTime / 3600);
					int minutes = (int) (remainingTime % 3600 / 60);
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.AVAILABLE_AFTER_S1_S2_HOURS_S3_MINUTES);
					sm.addItemName(_nevit_voice);
					sm.addNumber(hours);
					sm.addNumber(minutes);
					player.sendPacket(sm);
				}
				return null;
			} else {
				htmltext = "32783-adena.htm";
			}
		} else if (event.equalsIgnoreCase("buy_hourglass")) {
			int index = getHGIndex(player.getLevel());
			int _price_hourglass = _prices_hourglass[index];

			if (st.getQuestItemsCount(57) >= _price_hourglass) {
				String value = q.loadGlobalQuestVar(player.getAccountName() + "_hg_" + index);
				long _reuse_time = value.isEmpty() ? 0 : Long.parseLong(value);

				if (System.currentTimeMillis() > _reuse_time) {
					int[] hg = hourglasses[index];
					int _nevit_hourglass = hg[Rnd.get(0, hg.length - 1)];

					st.setState(State.STARTED);
					st.takeItems(57, _price_hourglass);
					st.giveItems(_nevit_hourglass, 1);
					q.saveGlobalQuestVar(player.getAccountName() + "_hg_" + index, Long.toString(System.currentTimeMillis() + 20 * 3600000));
				} else {
					long remainingTime = (_reuse_time - System.currentTimeMillis()) / 1000;
					int hours = (int) (remainingTime / 3600);
					int minutes = (int) (remainingTime % 3600 / 60);
					SystemMessage sm = SystemMessage.getSystemMessage(SystemMessageId.AVAILABLE_AFTER_S1_S2_HOURS_S3_MINUTES);
					sm.addString("Nevit's Hourglass");
					sm.addNumber(hours);
					sm.addNumber(minutes);
					player.sendPacket(sm);
				}
				return null;
			} else {
				htmltext = "32783-adena.htm";
			}
		}
		return htmltext;
	}

	@Override
	public String onFirstTalk(Npc npc, Player player) {
		QuestState st = player.getQuestState(getName());
		if (st == null) {
			Quest q = QuestManager.getInstance().getQuest(getName());
			st = q.newQuestState(player);
		}
		NpcHtmlMessage html = new NpcHtmlMessage(0);
		String content = getHtm(player.getHtmlPrefix(), "32783.htm");
		html.setHtml(content);
		html.replace("%donate%", Util.formatAdena(_prices_hourglass[getHGIndex(player.getLevel())]));
		player.sendPacket(html);
		return null;
	}

	private int getHGIndex(int lvl) {
		int index = 0;
		if (lvl < 20) {
			index = 0;
		} else if (lvl < 40) {
			index = 1;
		} else if (lvl < 52) {
			index = 2;
		} else if (lvl < 61) {
			index = 3;
		} else if (lvl < 76) {
			index = 4;
		} else if (lvl < 80) {
			index = 5;
		} else if (lvl < 86) {
			index = 6;
		}
		return index;
	}

	public PriestOfBlessing(int questId, String name, String descr) {
		super(questId, name, descr);

		addStartNpc(priest);
		addFirstTalkId(priest);
		addTalkId(priest);
		if (!spawned) {
			for (int[] spawn : spawns) {
				addSpawn(priest, spawn[0], spawn[1], spawn[2], spawn[3], false, 0);
			}
			spawned = true;
		}
	}

	public static void main(String[] args) {
		new PriestOfBlessing(-1, "PriestOfBlessing", "retail");
	}
}
