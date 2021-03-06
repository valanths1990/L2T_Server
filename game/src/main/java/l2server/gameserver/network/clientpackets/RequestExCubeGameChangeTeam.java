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

package l2server.gameserver.network.clientpackets;

import l2server.gameserver.instancemanager.HandysBlockCheckerManager;
import l2server.gameserver.model.actor.instance.Player;

/**
 * Format: chdd
 * d: Arena
 * d: Team
 *
 * @author mrTJO
 */
public final class RequestExCubeGameChangeTeam extends L2GameClientPacket {
	
	int arena;
	int team;
	
	@Override
	protected void readImpl() {
		// client sends -1,0,1,2 for arena parameter
		arena = readD() + 1;
		team = readD();
	}
	
	@Override
	public void runImpl() {
		// do not remove players after start
		if (HandysBlockCheckerManager.getInstance().arenaIsBeingUsed(arena)) {
			return;
		}
		Player player = getClient().getActiveChar();
		
		switch (team) {
			case 0:
			case 1:
				// Change Player Team
				HandysBlockCheckerManager.getInstance().changePlayerToTeam(player, arena, team);
				break;
			case -1:
				// Remove Player (me)
				int team = HandysBlockCheckerManager.getInstance().getHolder(arena).getPlayerTeam(player);
				// client sends two times this packet if click on exit
				// client did not send this packet on restart
				if (team > -1) {
					HandysBlockCheckerManager.getInstance().removePlayer(player, arena, team);
				}
				break;
			default:
				log.warn("Wrong Cube Game Team ID: " + this.team);
				break;
		}
	}
}
