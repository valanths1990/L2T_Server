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

import l2server.Config;
import l2server.gameserver.datatables.*;
import l2server.gameserver.datatables.SubPledgeSkillTree.SubUnitSkill;
import l2server.gameserver.model.*;
import l2server.gameserver.model.actor.Npc;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.model.actor.instance.TransformManagerInstance;
import l2server.gameserver.network.serverpackets.AcquireSkillInfo;
import l2server.gameserver.network.serverpackets.ExAcquireSkillInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class ...
 *
 * @version $Revision: 1.5.2.1.2.5 $ $Date: 2005/04/06 16:13:48 $
 */
public class RequestAcquireSkillInfo extends L2GameClientPacket {
	private static Logger log = LoggerFactory.getLogger(RequestAcquireSkillInfo.class.getName());


	private int id;
	private int level;
	private int skillType;
	
	@Override
	protected void readImpl() {
		id = readD();
		level = readD();
		skillType = readD();
	}
	
	@Override
	protected void runImpl() {
		if (id <= 0 || level <= 0) // minimal sanity check
		{
			return;
		}
		
		final Player activeChar = getClient().getActiveChar();
		
		if (activeChar == null) {
			return;
		}
		
		final Npc trainer = activeChar.getLastFolkNPC();
		
		final Skill skill = SkillTable.getInstance().getInfo(id, level);
		
		boolean canteach = false;
		
		if (skill == null) {
			if (Config.DEBUG) {
				log.warn("skill id " + id + " level " + level + " is undefined. aquireSkillInfo failed.");
			}
			
			return;
		}
		
		if (skillType == 0) {
			if (trainer instanceof TransformManagerInstance) {
				int itemId = 0;
				L2TransformSkillLearn[] skillst = SkillTreeTable.getInstance().getAvailableTransformSkills(activeChar);
				
				for (L2TransformSkillLearn s : skillst) {
					if (s.getId() == id && s.getLevel() == level) {
						canteach = true;
						itemId = s.getItemId();
						break;
					}
				}
				
				if (!canteach) {
					return; // cheater
				}
				
				int requiredSp = 0;
				AcquireSkillInfo asi = new AcquireSkillInfo(skill.getId(), skill.getLevelHash(), requiredSp, 0);
				
				// all transformations require scrolls
				asi.addRequirement(99, itemId, 1, 50);
				sendPacket(asi);
				return;
			}
			
			L2SkillLearn skillToLearn = null;
			L2SkillLearn[] skills = SkillTreeTable.getInstance().getAvailableClassSkills(activeChar);
			
			for (L2SkillLearn s : skills) {
				if (s.getId() == id && s.getLevel() == level) {
					skillToLearn = s;
					break;
				}
			}
			
			if (skillToLearn == null) {
				return; // cheater
			}
			
			sendPacket(new ExAcquireSkillInfo(skillToLearn, activeChar));
		} else if (skillType == 2) {
			int requiredRep = 0;
			L2PledgeSkillLearn[] skills = PledgeSkillTree.getInstance().getAvailableSkills(activeChar);
			
			for (L2PledgeSkillLearn s : skills) {
				if (s.getId() == id && s.getLevel() == level) {
					canteach = true;
					requiredRep = s.getRepCost();
					break;
				}
			}
			
			if (!canteach) {
				return; // cheater
			}
			
			AcquireSkillInfo asi = new AcquireSkillInfo(skill.getId(), skill.getLevelHash(), requiredRep, 2);
			sendPacket(asi);
		} else if (skillType == 3) {
			if (trainer instanceof L2SquadTrainer) {
				SubUnitSkill sus = SubPledgeSkillTree.getInstance().getSkill(SkillTable.getSkillHashCode(skill));
				AcquireSkillInfo asi = new AcquireSkillInfo(skill.getId(), skill.getLevelHash(), sus.getReputation(), 3);
				asi.addRequirement(0, sus.getItemId(), sus.getCount(), 0);
				sendPacket(asi);
			}
		} else if (skillType == 4) {
			int cost = CertificateSkillTable.getInstance().getSubClassSkillCost(skill.getId());
			if (cost > 0) {
				AcquireSkillInfo asi = new AcquireSkillInfo(skill.getId(), skill.getLevelHash(), 0, 4);
				asi.addRequirement(99, CertificateSkillTable.SUBCLASS_CERTIFICATE, cost, 50);
				sendPacket(asi);
			}
		} else if (skillType == 5) {
			int cost = CertificateSkillTable.getInstance().getDualClassSkillCost(skill.getId());
			if (cost > 0) {
				AcquireSkillInfo asi = new AcquireSkillInfo(skill.getId(), skill.getLevelHash(), 0, 5);
				asi.addRequirement(99, CertificateSkillTable.DUALCLASS_CERTIFICATE, cost, 50);
				sendPacket(asi);
			}
		} else if (skillType == 6) {
			int costid = 0;
			int costcount = 0;
			L2SkillLearn[] skillsc = SkillTreeTable.getInstance().getAvailableSpecialSkills(activeChar);
			for (L2SkillLearn s : skillsc) {
				Skill sk = SkillTable.getInstance().getInfo(s.getId(), s.getLevel());
				
				if (sk == null || sk != skill) {
					continue;
				}
				
				canteach = true;
				costid = 0;
				costcount = 0;
			}
			
			AcquireSkillInfo asi = new AcquireSkillInfo(skill.getId(), skill.getLevelHash(), 0, 6);
			asi.addRequirement(5, costid, costcount, 0);
			sendPacket(asi);
		} else
		// Common Skills
		{
			L2SkillLearn skillToLearn = null;
			L2SkillLearn[] skillsc = SkillTreeTable.getInstance().getAvailableSkills(activeChar);
			
			for (L2SkillLearn s : skillsc) {
				Skill sk = SkillTable.getInstance().getInfo(s.getId(), s.getLevel());
				
				if (sk == null || sk != skill) {
					continue;
				}
				
				skillToLearn = s;
			}
			
			if (skillToLearn == null) {
				return;
			}
			
			sendPacket(new ExAcquireSkillInfo(skillToLearn, activeChar));
		}
	}
}
