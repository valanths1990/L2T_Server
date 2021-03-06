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

package handlers.targethandlers;

import l2server.gameserver.handler.ISkillTargetTypeHandler;
import l2server.gameserver.handler.SkillTargetTypeHandler;
import l2server.gameserver.model.Skill;
import l2server.gameserver.model.WorldObject;
import l2server.gameserver.model.actor.Creature;
import l2server.gameserver.model.actor.instance.StaticObjectInstance;
import l2server.gameserver.network.SystemMessageId;
import l2server.gameserver.network.serverpackets.SystemMessage;
import l2server.gameserver.templates.skills.SkillTargetType;
import l2server.gameserver.templates.skills.SkillType;

import java.util.logging.Logger;

/**
 * @author nBd
 */
public class TargetOne implements ISkillTargetTypeHandler {
	protected static final Logger log = Logger.getLogger(TargetOne.class.getName());
	
	@Override
	public WorldObject[] getTargetList(Skill skill, Creature activeChar, boolean onlyFirst, Creature target) {
		SkillType skillType = skill.getSkillType();
		
		boolean canTargetSelf = false;
		
		switch (skillType) {
			case BUFF:
			case HEAL:
				//case HOT:
			case HEAL_PERCENT:
			case MANARECHARGE:
			case MANAHEAL:
			case NEGATE:
			case CANCEL:
			case CANCEL_DEBUFF:
				//case REFLECT:
			case COMBATPOINTHEAL:
				//case MAGE_BANE:
				//case WARRIOR_BANE:
			case BETRAY:
			case HPMPHEAL_PERCENT:
			case HPMPCPHEAL_PERCENT:
			case HPCPHEAL_PERCENT:
			case BALANCE_LIFE:
				canTargetSelf = true;
				break;
			case TAKEFORT: {
				if (target instanceof StaticObjectInstance) {
					return new Creature[]{target};
				} else {
					log.info("TargetOne: Target is Incorrect for Player - " + activeChar.getName());
					activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TARGET_IS_INCORRECT));
					return null;
				}
			}
			default:
		}
		
		// Check for null target or any other invalid target
		if (target == null || target.isDead() || target == activeChar && !canTargetSelf) {
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.TARGET_IS_INCORRECT));
			return null;
		}
		
		// If a target is found, return it in a table else send a system message TARGET_IS_INCORRECT
		return new Creature[]{target};
	}
	
	@Override
	public Enum<SkillTargetType> getTargetType() {
		// TODO Auto-generated method stub
		return SkillTargetType.TARGET_ONE;
	}
	
	public static void main(String[] args) {
		SkillTargetTypeHandler.getInstance().registerSkillTargetType(new TargetOne());
	}
}
