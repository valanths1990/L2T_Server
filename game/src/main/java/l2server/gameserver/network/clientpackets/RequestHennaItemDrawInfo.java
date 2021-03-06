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

import l2server.gameserver.datatables.HennaTable;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.network.serverpackets.HennaItemDrawInfo;
import l2server.gameserver.templates.item.HennaTemplate;

/**
 * This class ...
 *
 * @version $Revision$ $Date$
 */
public final class RequestHennaItemDrawInfo extends L2GameClientPacket {
	
	private int symbolId;
	
	// format  cd
	
	@Override
	protected void readImpl() {
		symbolId = readD();
	}
	
	@Override
	protected void runImpl() {
		Player activeChar = getClient().getActiveChar();
		if (activeChar == null) {
			return;
		}
		
		HennaTemplate henna = HennaTable.getInstance().getTemplate(symbolId);
		if (henna == null) {
			return;
		}
		
		activeChar.sendPacket(new HennaItemDrawInfo(henna, activeChar));
	}
}
