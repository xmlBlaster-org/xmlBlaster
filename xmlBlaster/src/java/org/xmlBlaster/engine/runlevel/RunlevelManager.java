/*------------------------------------------------------------------------------
Name:      RunlevelManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.runlevel;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.jutils.time.TimeHelper;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.admin.extern.JmxMBeanHandle;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.authentication.Authenticate;

import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Collections;
import java.util.Iterator;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;


/**
 * This starts/stops xmlBlaster with different run levels. 
 * <p>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.runlevel.html">engine.runlevel requirement</a>
 */
public final class RunlevelManager implements RunlevelManagerMBean
{
   private String ME = "RunlevelManager";
   private final ServerScope glob;
   private static Logger log = Logger.getLogger(RunlevelManager.class.getName());
   private int currRunlevel = 0;

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

   private final I_RunlevelListener[] DUMMY_ARR = new I_RunlevelListener[0];

   /** My JMX registration */
   private JmxMBeanHandle mbeanHandle;
   private ContextNode contextNode;

   /**
    * For listeners who want to be informed about runlevel changes. 
    */
   private final Set runlevelListenerSet = Collections.synchronizedSet(new HashSet());


   /**
    * One instance of this represents one xmlBlaster server. 
    * <p />
    * You need to call initPluginManagers() after creation.
    */
   public RunlevelManager(ServerScope glob) {
      this.glob = glob;

      this.ME = "RunlevelManager" + this.glob.getLogPrefixDashed();
      if (log.isLoggable(Level.FINER)) log.finer("Incarnated run level manager");
      // For JMX instanceName may not contain ","
      this.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG,
            "RunlevelManager", this.glob.getScopeContextNode());
   }
   
   // Is done from external engine.Global to avoid 
   public void initJmx() {
      try {
         this.mbeanHandle = this.glob.registerMBean(this.contextNode, this);
      }
      catch(XmlBlasterException e) {
         log.severe(e.getMessage());
      }
   }
   
   public ContextNode getContextNode() {
      return this.contextNode;
   }

   /**
    * Sets the cluster node ID as soon as it is known. 
    */
   public void setId(String id) {
      this.ME = "RunlevelManager" + this.glob.getLogPrefixDashed();
   }

   /**
    * Incarnate the different managers which handle run levels. 
    */
   public void initPluginManagers() throws XmlBlasterException {
      // TODO: This should be configurable
      new Authenticate(glob);
      // glob.getProtocolManager(); // force incarnation
      if (log.isLoggable(Level.FINER)) log.finer("Initialized run level manager");
   }

   /**
    * Adds the specified runlevel listener to receive runlevel change events.
    * Multiple registrations fo the same listener will overwrite the old one. 
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
         String text = "Runlevel " + newRunlevel + " is not allowed, please choose one of " +
                       RUNLEVEL_HALTED + "|" + RUNLEVEL_STANDBY + "|" +
                       RUNLEVEL_CLEANUP + "|" + RUNLEVEL_RUNNING;
         if (log.isLoggable(Level.FINE)) log.fine(text);
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, text);
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
    * JMX: Change the run level of xmlBlaster. 
    * @param 0 is halted and 9 is fully operational
    */
   public String setRunlevel(String level) throws Exception {
      try {
         int numErrors = changeRunlevel(level, true);
         return "Changed to run level " +toRunlevelStr(getCurrentRunlevel())+" '" + level + "'" +
          ((numErrors>0) ? (" with "+numErrors+" errors") : "");
      }
      catch (XmlBlasterException e) {
         throw new Exception(e.getMessage());
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
      if (log.isLoggable(Level.FINER)) log.finer("Changing from run level " + currRunlevel + " to run level " + newRunlevel + " with force=" + force);
      long start = System.currentTimeMillis();
      int numErrors = 0;
      if (currRunlevel == newRunlevel) {
         return numErrors;
      }
      int from = currRunlevel;
      int to = newRunlevel;

      log.info("Change request from run level " + toRunlevelStr(from) + " to run level " + toRunlevelStr(to) + " ...");

      if (!isMajorLevel(to)) {
         String text = "Runlevel " + to + " is not allowed, please choose one of " +
                       RUNLEVEL_HALTED + "|" + RUNLEVEL_STANDBY + "|" +
                       RUNLEVEL_CLEANUP + "|" + RUNLEVEL_RUNNING;
         if (log.isLoggable(Level.FINE)) log.fine(text);
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME, text);
      }

      if (from < to) { // startup
         for (int ii=from; ii<to; ii++) {
            int dest = ii+1;
            try {
               startupPlugins(ii, dest);
               fireRunlevelEvent(ii, dest, force);
            }
            finally {
               currRunlevel = dest; // pre/post events are not marked as run levels
               if (dest > from && isMajorLevel(dest)) {
                  long elapsed = System.currentTimeMillis() - start;
                  if (numErrors == 0)
                     log.info("Successful startup to run level " + toRunlevelStr(dest) + TimeHelper.millisToNice(elapsed));
                  else
                     log.info("Startup to run level " + toRunlevelStr(dest) + " done with " + numErrors + " errors.");
               }
            }
         }
         if (to == RUNLEVEL_RUNNING) { // Main.java to display banner
            fireRunlevelEvent(RUNLEVEL_RUNNING, RUNLEVEL_RUNNING_POST, force);
         }
      }
      else if (from > to) { // shutdown
         for (int ii=from; ii>to; ii--) {
            int dest = ii-1;
            try {
               shutdownPlugins(ii, dest);
               fireRunlevelEvent(ii, dest, force);
            }
            finally {
               currRunlevel = dest;
               if (dest < from && isMajorLevel(dest)) {
                  long elapsed = System.currentTimeMillis() - start;
                  if (numErrors == 0)
                     log.info("Successful shutdown to run level=" + toRunlevelStr(dest) + TimeHelper.millisToNice(elapsed));
                  else
                     log.info("Shutdown to run level=" + toRunlevelStr(dest) + " done with " + numErrors + " errors.");
               }
            }
         }
      }

      if (log.isLoggable(Level.FINER)) log.finer("Leaving changeRunlevel with runlevel = " + toRunlevelStr(currRunlevel)); 
      return numErrors;
   }


   /**
    *
    */
   private void startupPlugins(int from, int to) throws XmlBlasterException {
      TreeSet pluginSet = this.glob.getPluginHolder().getStartupSequence(this.glob.getStrippedId(), from+1, to);
      if (log.isLoggable(Level.FINER)) this.log.finer("startupPlugins. the size of the plugin set is '" + pluginSet.size() + "'");
      Iterator iter = pluginSet.iterator();
      while (iter.hasNext()) {
         PluginConfig pluginConfig = (PluginConfig)iter.next();
         if (pluginConfig == null) { 
            log.warning("startupPlugins. the pluginConfig object is null");
            continue;
         }
         if (!pluginConfig.isCreate()) {
            log.fine("startupPlugins. the plugin + " + pluginConfig.getId() + " is ignored, create='false'");
            continue;
         }
         if (log.isLoggable(Level.FINEST)) this.log.finest("startupPlugins " + pluginConfig.toXml());
         try {
            PluginInfo pluginInfo = pluginConfig.getPluginInfo();
            if (log.isLoggable(Level.FINER)) {
               if (pluginInfo != null) {
                  log.finer("startupPlugins pluginInfo object: " + pluginInfo.getId() + " classname: " + pluginInfo.getClassName());
               }
               else log.finer("startupPlugins: the pluginInfo is null");
            }
            this.glob.getPluginManager().getPluginObject(pluginInfo);
            log.info("startupPlugins: run level '" + from + "' to '" + to + "' plugin '" + pluginConfig.getId() + "' successful loaded");
         }
         catch (Throwable ex) {
            ErrorCode code = pluginConfig.getUpAction().getOnFail();
            if (code == null) {
               log.warning("startupPlugins. Exception when loading the plugin '" + pluginConfig.getId() + "' reason: " + ex.toString());
               ex.printStackTrace();
            }
            else {
               throw new XmlBlasterException(this.glob, code, ME + ".startupPlugins",  "Can't load plugin '" + pluginConfig.getId() + "'", ex);
            }
         }
      }
   }


   /**
    *
    */
   private void shutdownPlugins(int from, int to) throws XmlBlasterException {
      TreeSet pluginSet = this.glob.getPluginHolder().getShutdownSequence(this.glob.getStrippedId(), to, from-1);
      Iterator iter = pluginSet.iterator();
      while (iter.hasNext()) {
         PluginConfig pluginConfig = (PluginConfig)iter.next();
         if (pluginConfig == null || !pluginConfig.isCreate())
            continue;

         try {
            PluginInfo pluginInfo = pluginConfig.getPluginInfo();
            I_Plugin plugin = this.glob.getPluginManager().getPluginObject(pluginInfo);
            plugin.shutdown();
            this.glob.getPluginManager().removeFromPluginCache(pluginInfo.getId());
            log.info("fireRunlevelEvent: run level '" + from + "' to '" + to + "' plugin '" + pluginConfig.getId() + "' shutdown");
         }
         catch (Throwable ex) {
            ErrorCode code = pluginConfig.getDownAction().getOnFail();
            if (code == null) {
               log.warning(".fireRunlevelEvent. Exception when shutting down the plugin '" + pluginConfig.getId() + "' reason: " + ex.toString());
            }
            else {
               throw new XmlBlasterException(this.glob, code, ME + ".fireRunlevelEvent",  ".fireRunlevelEvent. Exception when shutting down the plugin '" + pluginConfig.getId() + "'", ex);
            }
         }
      }
   }


   /**
    * The static plugins are loaded from (exclusive) to (inclusive) when startup and
    * the same when shutting down. For example if you define LOAD on r 3, and STOP on
    * r 2, then LOAD is fired when from=2,to=3 and STOP when from=3,to=2
    */
   private final int fireRunlevelEvent(int from, int to, boolean force) throws XmlBlasterException {
      int numErrors = 0;

      // Take a snapshot of current listeners (to avoid ConcurrentModificationException in iterator)
      I_RunlevelListener[] listeners;
      synchronized (runlevelListenerSet) {
         if (runlevelListenerSet.size() == 0)
            return numErrors;
         listeners = (I_RunlevelListener[])runlevelListenerSet.toArray(DUMMY_ARR);
      }

      for (int ii=0; ii<listeners.length; ii++) {
         I_RunlevelListener li = listeners[ii];
         try {
            li.runlevelChange(from, to, force);
            if (log.isLoggable(Level.FINE)) {
               if (isMajorLevel(to)) {
                  if (from < to)
                     log.fine(li.getName() + " successful startup to run level=" + to + ", errors=" + numErrors + ".");
                  else
                     log.fine(li.getName() + " successful shutdown to run level=" + to + ", errors=" + numErrors + ".");
               }
            }
         }
         catch (XmlBlasterException e) {
            if (e.isInternal()) {
               log.severe("Changing from run level=" + from + " to level=" + to + " failed for component " + li.getName() + ": " + e.getMessage());
            }
            else {
               log.warning("Changing from run level=" + from + " to level=" + to + " failed for component " + li.getName() + ": " + e.getMessage());
            }
            numErrors++;
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
      return currRunlevel <= RUNLEVEL_HALTED;
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
    * @param level For example 7
    * @return "RUNLEVEL_UNKNOWN" if no valid run level, else for
    * example "STANDBY_POST"
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
    * @param level For example "STANDBY" or 7
    * @param defaultPriority Value to use if not parseable
    * @return -10 if no valid run level
    */
   public final static int toRunlevelInt(String level) {
      if (level == null) return -10;
      level = level.trim();
      try {
         return Integer.parseInt(level);
      }
      catch(NumberFormatException e) {}

      if (level.equalsIgnoreCase("HALTED_PRE"))
         return RUNLEVEL_HALTED_PRE;
      else if (level.equalsIgnoreCase("HALTED"))
         return RUNLEVEL_HALTED;
      else if (level.equalsIgnoreCase("HALTED_POST"))
         return RUNLEVEL_HALTED_POST;
      else if (level.equalsIgnoreCase("STANDBY_PRE"))
         return RUNLEVEL_STANDBY_PRE;
      else if (level.equalsIgnoreCase("STANDBY"))
         return RUNLEVEL_STANDBY;
      else if (level.equalsIgnoreCase("STANDBY_POST"))
         return RUNLEVEL_STANDBY_POST;
      else if (level.equalsIgnoreCase("CLEANUP_PRE"))
         return RUNLEVEL_CLEANUP_PRE;
      else if (level.equalsIgnoreCase("CLEANUP"))
         return RUNLEVEL_CLEANUP;
      else if (level.equalsIgnoreCase("CLEANUP_POST"))
         return RUNLEVEL_CLEANUP_POST;
      else if (level.equalsIgnoreCase("RUNNING_PRE"))
         return RUNLEVEL_RUNNING_PRE;
      else if (level.equalsIgnoreCase("RUNNING"))
         return RUNLEVEL_RUNNING;
      else if (level.equalsIgnoreCase("RUNNING_POST"))
         return RUNLEVEL_RUNNING_POST;
      else
         return -10;
   }
   
   public void shutdown() {
      if (this.mbeanHandle != null)
         this.glob.unregisterMBean(this.mbeanHandle);
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminUsage#usage()
    */
   public java.lang.String usage() {
      return ServerScope.getJmxUsageLinkInfo(this.getClass().getName(), null);
   }
   /* (non-Javadoc)
    * @see org.xmlBlaster.util.admin.I_AdminUsage#getUsageUrl()
    */
   public java.lang.String getUsageUrl() {
      return ServerScope.getJavadocUrl(this.getClass().getName(), null);
   }
   /* (non-Javadoc)
    * JMX dummy to have a copy/paste functionality in jconsole
    * @see org.xmlBlaster.util.admin.I_AdminUsage#setUsageUrl(java.lang.String)
    */
   public void setUsageUrl(java.lang.String url) {}
}
