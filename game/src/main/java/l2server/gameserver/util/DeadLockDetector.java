/* This program is free software: you can redistribute it and/or modify it under
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

import l2server.Config;
import l2server.gameserver.Announcements;
import l2server.gameserver.Shutdown;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.*;

/**
 * @author -Nemesiss- L2M
 */
public class DeadLockDetector extends Thread {
	private static Logger log = LoggerFactory.getLogger(DeadLockDetector.class.getName());

	private static final int sleepTime = Config.DEADLOCK_CHECK_INTERVAL * 1000;

	private final ThreadMXBean tmx;

	public DeadLockDetector() {
		super("DeadLockDetector");
		tmx = ManagementFactory.getThreadMXBean();
	}

	@Override
	public final void run() {
		boolean deadlock = false;
		while (!deadlock) {
			try {
				long[] ids = tmx.findDeadlockedThreads();

				if (ids != null) {
					deadlock = true;
					ThreadInfo[] tis = tmx.getThreadInfo(ids, true, true);
					String info = "DeadLock Found!\n";
					for (ThreadInfo ti : tis) {
						info += ti.toString();
					}

					for (ThreadInfo ti : tis) {
						LockInfo[] locks = ti.getLockedSynchronizers();
						MonitorInfo[] monitors = ti.getLockedMonitors();
						if (locks.length == 0 && monitors.length == 0) {
							continue;
						}

						ThreadInfo dl = ti;
						info += "Java-level deadlock:\n";
						info += "\t" + dl.getThreadName() + " is waiting to lock " + dl.getLockInfo().toString() + " which is held by " +
								dl.getLockOwnerName() + "\n";
						while ((dl = tmx.getThreadInfo(new long[]{dl.getLockOwnerId()}, true, true)[0]).getThreadId() != ti.getThreadId()) {
							info += "\t" + dl.getThreadName() + " is waiting to lock " + dl.getLockInfo().toString() + " which is held by " +
									dl.getLockOwnerName() + "\n";
						}
					}
					log.warn(info);

					if (Config.RESTART_ON_DEADLOCK) {
						Announcements an = Announcements.getInstance();
						an.announceToAll("Server has stability issues - restarting now.");
						Shutdown.getInstance().startShutdown("DeadLockDetector - Auto Restart", 60, true);
					}
				}
				Thread.sleep(sleepTime);
			} catch (Exception e) {
				log.warn("DeadLockDetector: ", e);
			}
		}
	}
}
