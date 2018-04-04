/*
 * $Header: WorldObjectMap.java, 22/07/2005 14:15:11 luisantonioa Exp $
 *
 * $Author: luisantonioa $
 * $Date: 22/07/2005 14:15:11 $
 * $Revision: 1 $
 * $Log: WorldObjectMap.java,v $
 * Revision 1  22/07/2005 14:15:11  luisantonioa
 * Added copyright notice
 *
 *
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

package l2server.gameserver.util;

import l2server.gameserver.model.WorldObject;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class ...
 *
 * @version $Revision: 1.2 $ $Date: 2004/06/27 08:12:59 $
 */

public class WorldObjectMap<T extends WorldObject> extends L2ObjectMap<T> {
	Map<Integer, T> objectMap = new ConcurrentHashMap<>();

	/* (non-Javadoc)
	 * @see l2server.gameserver.util.L2ObjectMap#size()
	 */
	@Override
	public int size() {
		return objectMap.size();
	}

	/* (non-Javadoc)
	 * @see l2server.gameserver.util.L2ObjectMap#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return objectMap.isEmpty();
	}

	/* (non-Javadoc)
	 * @see l2server.gameserver.util.L2ObjectMap#clear()
	 */
	@Override
	public void clear() {
		objectMap.clear();
	}

	/* (non-Javadoc)
	 * @see l2server.gameserver.util.L2ObjectMap#put(T)
	 */
	@Override
	public void put(T obj) {
		if (obj != null) {
			objectMap.put(obj.getObjectId(), obj);
		}
	}

	/* (non-Javadoc)
	 * @see l2server.gameserver.util.L2ObjectMap#remove(T)
	 */
	@Override
	public void remove(T obj) {
		if (obj != null) {
			objectMap.remove(obj.getObjectId());
		}
	}

	/* (non-Javadoc)
	 * @see l2server.gameserver.util.L2ObjectMap#get(int)
	 */
	@Override
	public T get(int id) {
		return objectMap.get(id);
	}

	/* (non-Javadoc)
	 * @see l2server.gameserver.util.L2ObjectMap#contains(T)
	 */
	@Override
	public boolean contains(T obj) {
		if (obj == null) {
			return false;
		}
		return objectMap.get(obj.getObjectId()) != null;
	}

	/* (non-Javadoc)
	 * @see l2server.gameserver.util.L2ObjectMap#iterator()
	 */
	@Override
	public Iterator<T> iterator() {
		return objectMap.values().iterator();
	}
}