/*------------------------------------------------------------------------------
Name:      RunlevelManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.jutils.time.TimeHelper;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.engine.cluster.ClusterManager;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.protocol.ProtocolManager;
import org.xmlBlaster.authentication.Authenticate;

import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Iterator;


/**
 * This starts/stops xmlBlaster with different run levels. 
 * <p>
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.runlevel.html">engine.runlevel requirement</a>
 */
public final class RunlevelManager
{
   private String ME = "RunlevelManager";
   private final Global glob;
   private final LogChannel log;
   private int currRunlevel = 0;
   private int instanceCounter = 0;

   public static final int RUNLEVEL_HALTED_PRE = -1;
   public static final int RUNLEVEL_HALTED = 0;
   public static final int RUNLEVEL_HALTED_POST = 1;

   public static final int RUNLEVEL_STANDBY_PRE = 2;
   public static final int RUNLEVEL_STANDBY = 3;
   public static final int RUNLEVEL_STANDBY_POST = 4;

   public static final int RUNLEVEL_CLEANUP_PRE = 5;
   public static final int RUNLEVEL_CLEANUP = 6;
   public static final int RUNLEVEL_CLEANUP_POST = 7;

   public static final int RUNLEVEL_RUNNING_PRE = 8;
   public static final int RUNLEVEL_RUNNING = 9;
   public static final int RUNLEVEL_RUNNING_POST = 10;


   /**
    * For listeners who want to be informed about runlevel changes. 
    */
   private final Set runlevelListenerSet = Collections.synchronizedSet(new HashSet());

   /**
    * One instance of this represents one xmlBlaster server. 
    * <p />
    * You need to call initPluginManagers() after creation.
    */
   public RunlevelManager(Global glob) {
      this.glob = glob;
      this.log = glob.getLog("core");
      instanceCounter++;
      this.ME = "RunlevelManager"/* + instanceCounter*/ + this.glob.getLogPraefixDashed();
      if (log.CALL) log.call(ME, "Incarnated run level manager #" + instanceCounter);
   }

   /**
    * Sets the cluster node ID as soon as it is known. 
    */
   public void setId(String id) {
      this.ME = "RunlevelManager" + this.glob.getLogPraefixDashed();
   }

   /**
    * Incarnate the different managers which handle run levels. 
    */
   public void initPluginManagers() throws XmlBlasterException {
      // TODO: This should be configurable
      new Authenticate(glob);
      glob.getProtocolManager(); // force incarnation
      if (log.CALL) log.call(ME, "Initialized run level manager #" + instanceCounter);
   }

   /**
    * Adds the specified runlevel listener to receive runlevel change events. 
    */
   public void addRunlevelListener(I_RunlevelListener l) {
      if (l == null) {
         return;
      }
      synchronized (runlevelListenerSet) {
         runlevelListenerSet.add(l);
      }
   }

   /**
    * Removes the specified listener.
    */
   public void removeRunlevelListener(I_RunlevelListener l) {
      if (l == null) {
         return;
      }
      synchronized (runlevelListenerSet) {
         runlevelListenerSet.remove(l);
      }
   }

   /**
    * Allows to pass the newRunlevel as a String like "RUNLEVEL_STANDBY" or "6"
    * @see #changeRunlevel(int, boolean)
    */
   public final int changeRunlevel(String newRunlevel, boolean force) throws XmlBlasterException {
      if (newRunlevel == null || newRunlevel.length() < 1) {
         String text = "Runlevel " + newRunlevel + " is not allowed, please chooes one of " +
                       RUNLEVEL_HALTED + "|" + RUNLEVEL_STANDBY + "|" +
                       RUNLEVEL_CLEANUP + "|" + RUNLEVEL_RUNNING;
         log.warn(ME, text);
         throw new XmlBlasterException(ME, text);
      }

      int level = 0;
      try {
         level = Integer.parseInt(newRunlevel.trim());
         return glob.getRunlevelManager().changeRunlevel(level, true);
      }
      catch(NumberFormatException e) {
         level = toRunlevelInt(newRunlevel);
         return glob.getRunlevelManager().changeRunlevel(level, true);
      }
   }

   /**
    * Change the run level to the given newRunlevel. 
    * <p />
    * See RUNLEVEL_HALTED etc.
    * <p />
    * Note that there are four main run levels:
    * <ul>
    *   <li>RUNLEVEL_HALTED</li>
    *   <li>RUNLEVEL_STANDBY</li>
    *   <li>RUNLEVEL_CLEANUP</li>
    *   <li>RUNLEVEL_RUNNING</li>
    * </ul>
    * and every RUNLEVEL sends a pre and a post run level event, to allow
    * the listeners to prepare or log before or after successfully changing levels.<br />
    * NOTE that the pre/post events are <b>no</b> run level states - they are just events.
    * @param newRunlevel The new run level we want to switch to
    * @param force Ignore exceptions during change, currently only force == true is supported
    * @return numErrors
    * @exception XmlBlasterException for invalid run level
    */
   public final int changeRunlevel(int newRunlevel, boolean force) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Changing from run level " + currRunlevel + " to run level " + newRunlevel + " with force=" + force);
      long start = System.currentTimeMillis();
      int numErrors = 0;
      if (currRunlevel == newRunlevel) {
         return numErrors;
      }
      int from = currRunlevel;
      int to = newRunlevel;

      log.info(ME, "Change request from run level " + toRunlevelStr(from) + " to run level " + toRunlevelStr(to) + " ...");

      if (!isMajorLevel(to)) {
         String text = "Runlevel " + to + " is not allowed, please chooes one of " +
                       RUNLEVEL_HALTED + "|" + RUNLEVEL_STANDBY + "|" +
                       RUNLEVEL_CLEANUP + "|" + RUNLEVEL_RUNNING;
         log.error(ME, text);
         throw new XmlBlasterException(ME, text);
      }


      if (from < to) { // startup
         for (int ii=from; ii<=to; ii++) {
            int dest = ii+1;
            try {
               fireRunlevelEvent(ii, dest, force);
            }
            finally {
               if (dest > from && isMajorLevel(dest)) {
                  currRunlevel = dest; // pre/post events are not marked as run levels
                  long elapsed = System.currentTimeMillis() - start;
                  if (numErrors == 0)
                     log.info(ME, "Successful startup to run level " + toRunlevelStr(dest) + TimeHelper.millisToNice(elapsed));
                  else
                     log.info(ME, "Startup to run level " + toRunlevelStr(dest) + " done with " + numErrors + " errors.");
               }
            }
         }
      }
      else if (from > to) { // shutdown
         for (int ii=from; ii>=to; ii--) {
            int dest = ii-1;
            try {
               fireRunlevelEvent(ii, dest, force);
            }
            finally {
               if (dest < from && isMajorLevel(dest)) {
                  currRunlevel = dest;
                  long elapsed = System.currentTimeMillis() - start;
                  if (numErrors == 0)
                     log.info(ME, "Successful shutdown to run level=" + toRunlevelStr(dest) + TimeHelper.millisToNice(elapsed));
                  else
                     log.info(ME, "Shutdown to run level=" + toRunlevelStr(dest) + " done with " + numErrors + " errors.");
               }
            }
         }
      }
      if (log.CALL) log.call(ME, "Leaving changeRunlevel with runlevel = " + toRunlevelStr(currRunlevel)); 
      return numErrors;
   }

   private final int fireRunlevelEvent(int from, int to, boolean force) throws XmlBlasterException {
      int numErrors = 0;
      synchronized (runlevelListenerSet) {
         if (runlevelListenerSet.size() == 0)
            return numErrors;
         Iterator iterator = runlevelListenerSet.iterator();
         while (iterator.hasNext()) {
            I_RunlevelListener li = (I_RunlevelListener)iterator.next();
            try {
               li.runlevelChange(from, to, force);
               if (log.TRACE) {
                  if (isMajorLevel(to)) {
                     if (from < to)
                        log.trace(ME, li.getName() + " successful startup to run level=" + to + ", errors=" + numErrors + ".");
                     else
                        log.trace(ME, li.getName() + " successful shutdown to run level=" + to + ", errors=" + numErrors + ".");
                  }
               }
            }
            catch (XmlBlasterException e) {
               log.warn(ME, "Changing from run level=" + from + " to level=" + to + " failed for component " + li.getName() + ": " + e.toString());
               numErrors++;
            }
         }
      }
      return numErrors;
   }

   /**
    * See java for runlevels
    */
   public final int getCurrentRunlevel() {
      return currRunlevel;
   }

   public boolean isHalted() {
      return currRunlevel == RUNLEVEL_HALTED;
   }
   public boolean isStandby() {
      return currRunlevel == RUNLEVEL_STANDBY;
   }
   public boolean isCleanup() {
      return currRunlevel == RUNLEVEL_CLEANUP;
   }
   public boolean isRunning() {
      return currRunlevel == RUNLEVEL_RUNNING;
   }
   /**
    * @return true if one of the major run levels. false if pre or post event level
    */
   public boolean isMajorLevel() {
      return isMajorLevel(currRunlevel);
   }

   //======== static methods ============

   private static final boolean isMajorLevel(int level) {
      if (level == RUNLEVEL_HALTED || level == RUNLEVEL_STANDBY ||
          level == RUNLEVEL_CLEANUP || level == RUNLEVEL_RUNNING)
         return true;
      return false;
   }

   /**
    * @return true if one of the major levels
    */
   public static final boolean checkRunlevel(int level) {
      return isMajorLevel(level);
   }

   /**
    * Parses given string to extract the priority of a message
    * @param prio For example "HIGH" or 7
    * @param defaultPriority Value to use if not parseable
    * @return "RUNLEVEL_UNKNOWN" if no valid run level
    */
   public final static String toRunlevelStr(int level) {
      if (level == RUNLEVEL_HALTED_PRE)
         return "HALTED_PRE";
      else if (level == RUNLEVEL_HALTED)
         return "HALTED";
      else if (level == RUNLEVEL_HALTED_POST)
         return "HALTED_POST";
      else if (level == RUNLEVEL_STANDBY_PRE)
         return "STANDBY_PRE";
      else if (level == RUNLEVEL_STANDBY)
         return "STANDBY";
      else if (level == RUNLEVEL_STANDBY_POST)
         return "STANDBY_POST";
      else if (level == RUNLEVEL_CLEANUP_PRE)
         return "CLEANUP_PRE";
      else if (level == RUNLEVEL_CLEANUP)
         return "CLEANUP";
      else if (level == RUNLEVEL_CLEANUP_POST)
         return "CLEANUP_POST";
      else if (level == RUNLEVEL_RUNNING_PRE)
         return "RUNNING_PRE";
      else if (level == RUNLEVEL_RUNNING)
         return "RUNNING";
      else if (level == RUNLEVEL_RUNNING_POST)
         return "RUNNING_POST";
      else
         return "RUNLEVEL_UNKNOWN(" + level + ")";
   }

   /**
    * Parses given string to extract the priority of a message
    * @param prio For example "HIGH" or 7
    * @param defaultPriority Value to use if not parseable
    * @return -10 if no valid run level
    */
   public final static int toRunlevelInt(String level) {
      if (level.equalsIgnoreCase("HALTED_PRE"))
         return RUNLEVEL_HALTED_PRE;
         /*
      else if (level == RUNLEVEL_HALTED)
         return "HALTED";
      else if (level == RUNLEVEL_HALTED_POST)
         return "HALTED_POST";
      else if (level == RUNLEVEL_STANDBY_PRE)
         return "STANDBY_PRE";
      else if (level == RUNLEVEL_STANDBY)
         return "STANDBY";
      else if (level == RUNLEVEL_STANDBY_POST)
         return "STANDBY_POST";
      else if (level == RUNLEVEL_CLEANUP_PRE)
         return "CLEANUP_PRE";
      else if (level == RUNLEVEL_CLEANUP)
         return "CLEANUP";
      else if (level == RUNLEVEL_CLEANUP_POST)
         return "CLEANUP_POST";
      else if (level == RUNLEVEL_RUNNING_PRE)
         return "RUNNING_PRE";
      else if (level == RUNLEVEL_RUNNING)
         return "RUNNING";
      else if (level == RUNLEVEL_RUNNING_POST)
         return "RUNNING_POST";
         */
      else
         return -10;
   }
}
