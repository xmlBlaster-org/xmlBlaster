/*------------------------------------------------------------------------------
Name:      CommandManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main manager class for administrative commands
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.RunlevelManager;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.I_RunlevelListener;
import org.xmlBlaster.authentication.SessionInfo;

import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;

/**
 * The manager instance for administrative commands. 
 * <p />
 * Each xmlBlaster server instance has one instance
 * of this class to manage its administrative behavior.
 * <p />
 * See the <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.commands.html">command requirement</a>
 * for a detailed description.
 * @author ruff@swand.lake.de
 * @since 0.79f
 */
public final class CommandManager implements I_RunlevelListener
{
   private final String ME;

   // The following 3 declarations are 'final' but the SUN JDK 1.3.1 does not like it
   private final Global glob;
   private final LogChannel log;
   private final SessionInfo sessionInfo;

   /** Map to internal handlers like sysprop,client,msg etc */
   private final Map handlerMap = new TreeMap();

   /** Map of external gateways to SNMP, telnet etc. */
   private final Map externMap = new TreeMap();

   /**
    * You need to call postInit() after all drivers are loaded.
    *
    * @param sessionInfo Internal handle to be used directly with RequestBroker
    *                    NOTE: We (the command code) are responsible for security checks
    *                    as we directly write into RequestBroker.
    */
   public CommandManager(Global glob, SessionInfo sessionInfo) {
      this.glob = glob;
      this.log = this.glob.getLog("admin");
      this.ME = "CommandManager" + this.glob.getLogPraefixDashed();
      this.sessionInfo = sessionInfo;
      glob.getRunlevelManager().addRunlevelListener(this);
   }

   /**
    * Create internal gateways. 
    */
   private void initializeInternal() {
      // TODO: Change to use plugin framework:

      org.xmlBlaster.engine.admin.intern.PropertyHandler propertyHandler = 
                             new org.xmlBlaster.engine.admin.intern.PropertyHandler();
      propertyHandler.initialize(glob, this); // This will call register()

      org.xmlBlaster.engine.admin.intern.CoreHandler coreHandler = 
                             new org.xmlBlaster.engine.admin.intern.CoreHandler();
      coreHandler.initialize(glob, this); // This will call register()

      org.xmlBlaster.engine.admin.intern.MsgHandler msgHandler = 
                             new org.xmlBlaster.engine.admin.intern.MsgHandler();
      msgHandler.initialize(glob, this); // This will call register()
   }

   private void initializeExternal() {
      // TODO: Change to use plugin framework:

      // Initialize telnet access ...
      try {
         org.xmlBlaster.engine.admin.extern.TelnetGateway telnetGateway = new org.xmlBlaster.engine.admin.extern.TelnetGateway();
   
         if (telnetGateway.initialize(glob, this) == true)
            externMap.put(telnetGateway.getName(), telnetGateway);
      }
      catch(XmlBlasterException e) {
         e.printStackTrace();
         log.error(ME, e.toString());
      }
      catch(Throwable e) {
         e.printStackTrace();
         log.error(ME, e.toString());
      }

      // Initialize SNMP access ...
      try {
         org.xmlBlaster.engine.admin.extern.SnmpGateway snmpGateway = new org.xmlBlaster.engine.admin.extern.SnmpGateway();

         if (snmpGateway.initialize(glob, this) == true)
            externMap.put(snmpGateway.getName(), snmpGateway);
      }
      catch(XmlBlasterException e) {
         log.error(ME, e.toString());
      }
      catch(Throwable e) {
         e.printStackTrace();
         log.error(ME, e.toString());
      }

      // Initialize MomClient access ...
      try {
         org.xmlBlaster.engine.admin.extern.MomClientGateway momClientGateway = new org.xmlBlaster.engine.admin.extern.MomClientGateway();

         if (momClientGateway.initialize(glob, this) == true) {
            externMap.put(momClientGateway.getName(), momClientGateway);
            glob.registerMomClientGateway(momClientGateway);
         }
      }
      catch(XmlBlasterException e) {
         log.error(ME, e.toString());
      }
      catch(Throwable e) {
         e.printStackTrace();
         log.error(ME, e.toString());
      }
   }

   /**
    * Register internal handler for specific tasks. 
    */
   public synchronized final void register(String key, I_CommandHandler handler) {
      if (key == null || handler == null) {
         Thread.currentThread().dumpStack();
         throw new IllegalArgumentException(ME + ": Please pass a valid key and handler");
      }
      handlerMap.put(key, handler);
      if (log.TRACE) log.trace(ME, "Registered '" + key + "' for handler=" + handler.getClass());
   } 

   /**
    * @param sessionId Is null if not logged in
    * @param cmd The query string
    * @return The found data or an array of size 0 if not found. 
    */
   public synchronized final MessageUnit[] get(String sessionId, String cmd) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "get(" + cmd + ")");
      if (cmd == null || cmd.length() < 1)
         throw new IllegalArgumentException("Please pass a command which is not null");
      try {
         CommandWrapper w = new CommandWrapper(glob, cmd);
         String key = w.getThirdLevel();
         if (w.getThirdLevel().startsWith("?")) {
            key = "DEFAULT";  // One handler needs to register itself with "DEFAULT"
         }
         Object obj = handlerMap.get(key);
         if (obj == null) {
            throw new XmlBlasterException(ME, "Sorry can't process your command '" + cmd + "', '" + w.getThirdLevel() + "' has no registered handler (key=" + key + ")");
         }
         I_CommandHandler handler = (I_CommandHandler)obj;
         MessageUnit[] ret = handler.get(sessionId, w);
         if (ret == null) ret = new MessageUnit[0];
         return ret;
         //return (ret==null) ? "<qos><state id='NOT_FOUND' info='" + w.getCommand() + " has no results.'/></qos>" : ret;
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException(ME+".InternalError", e.toString());
      }
   }

   /**
    * @param sessionId Is null if not logged in
    * @param cmd The query string
    * @return The SetReturn object:<br />
    *         setReturn.returnString contains the actually set value or is null if not set. 
    */
   public synchronized final SetReturn set(String sessionId, String cmd) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "set(" + cmd + ")");
      if (cmd == null || cmd.length() < 1)
         throw new IllegalArgumentException("Please pass a command which is not null");
      try {
         CommandWrapper w = new CommandWrapper(glob, cmd);
         String key = w.getThirdLevel();
         if (w.getThirdLevel().startsWith("?")) {
            key = "DEFAULT";  // One handler needs to register itself with "DEFAULT"
         }
         Object obj = handlerMap.get(key);
         if (obj == null) {
            throw new XmlBlasterException(ME, "Sorry can't process your command '" + cmd + "', the third level '" + w.getThirdLevel() + "' has no registered handler (key=" + key + ")");
         }
         I_CommandHandler handler = (I_CommandHandler)obj;
         return new SetReturn(w, handler.set(sessionId, w));
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException(ME+".InternalError", e.toString());
      }
   }

   /**
    */
   public String help() {
      return "  XmlBlaster administration, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.commands.html";
   }

   /**
    */
   public String help(String cmd) {
      return help();
   }

   public void shutdown(boolean force) {
      if (externMap != null && externMap.size() > 0) {
         Iterator it = externMap.values().iterator();
         while (it.hasNext()) {
            I_ExternGateway gw = (I_ExternGateway)it.next();
            gw.shutdown();
         }
         externMap.clear();
      }

      if (handlerMap != null && handlerMap.size() > 0) {
         while (true) {
            Iterator it = handlerMap.keySet().iterator();
            if (!it.hasNext())
               break;
            String key = (String)it.next();
            I_CommandHandler cmd = (I_CommandHandler)handlerMap.get(key);
            cmd.shutdown();         // The shutdown should deregister so this iterator is invalid
            handlerMap.remove(key); // To be shure we kill again
            // If a handler has registered multiple times, it should be able to handle multiple shutdowns
         }
         handlerMap.clear();
      }
   }

   /**
    * A human readable name of the listener for logging. 
    * <p />
    * Enforced by I_RunlevelListener
    */
   public String getName() {
      return ME;
   }

   /**
    * Invoked on run level change, see RunlevelManager.RUNLEVEL_HALTED and RunlevelManager.RUNLEVEL_RUNNING
    * <p />
    * Enforced by I_RunlevelListener
    */
   public void runlevelChange(int from, int to, boolean force) throws org.xmlBlaster.util.XmlBlasterException {
      //if (log.CALL) log.call(ME, "Changing from run level=" + from + " to level=" + to + " with force=" + force);
      if (to == from)
         return;

      if (to > from) { // startup
         if (to == RunlevelManager.RUNLEVEL_STANDBY) {
            initializeInternal();
            initializeExternal();
            log.info(ME, "Administration manager is ready");
         }
      }

      if (to < from) { // shutdown
         if (to == RunlevelManager.RUNLEVEL_HALTED) {
            shutdown(force);
         }
      }
   }

   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * @param extraOffset indenting of tags for nice output
    */
   public synchronized final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1024);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<commandManager>");
      /*
      if (externMap != null && externMap.size() > 0) {
         Iterator it = externMap.values().iterator();
         while (it.hasNext()) {
            I_ExternGatewax gw = (I_ExternGateway)it.next();
            sb.append(gw.toXml(extraOffset + "   "));
         }
         externMap.clear();
      }
      if (handlerMap != null && handlerMap.size() > 0) {
         Iterator it = handlerMap.values().iterator();
         while (it.hasNext()) {
            I_CommandHandler cmd = (I_CommandHandler)it.next();
            sb.append(cmd.toXml(extraOffset + "   "));
         }
      }
      */
      sb.append(offset).append("</commandManager>");

      return sb.toString();
   }
}

