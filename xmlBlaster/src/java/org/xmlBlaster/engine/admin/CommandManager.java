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
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.admin.extern.TelnetGateway;

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
public final class CommandManager
{
   private String ME;

   // The following 3 declarations are 'final' but the SUN JDK 1.3.1 does not like it
   private final Global glob;
   private final LogChannel log;
   private final SessionInfo sessionInfo;

   private final Map handlerMap = new TreeMap();

   // external gateways:
   private TelnetGateway telnetGateway = null;

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
      this.ME = "CommandManager-" + this.glob.getId();
      this.sessionInfo = sessionInfo;

      initializeInternal();
      initializeExternal();
      log.info(ME, "Administration manager is ready");
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
   }

   private void initializeExternal() {
      // TODO: Change to use plugin framework:

      // Initialize telnet access ...
      try {
         int port = glob.getProperty().get("admin.remoteconsole.port", 0); // 2702;
         port = glob.getProperty().get("admin.remoteconsole.port[" + glob.getId() + "]", port);
         if (port > 1000)
            telnetGateway = new TelnetGateway(glob, this, port);
         else {
            if (log.TRACE) log.trace(ME, "No telnet gateway configured, port=" + port + " try '-admin.remoteconsole.port 2702' if you want one");
         }
      }
      catch(XmlBlasterException e) {
         log.error(ME, e.toString());
      }

      // Initialize SNMP access ...

   }

   public synchronized final void register(String key, I_CommandHandler handler) {
      if (key == null || handler == null) {
         Thread.currentThread().dumpStack();
         throw new IllegalArgumentException(ME + ": Please pass a valid key and handler");
      }
      handlerMap.put(key, handler);
      if (log.TRACE) log.trace(ME, "Registered '" + key + "' for handler=" + handler.getClass());
   } 

   /**
    * @return The found data or an empty string if not found. 
    */
   public synchronized final String get(String cmd) throws XmlBlasterException {
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
            throw new XmlBlasterException(ME, "Sorry can't process your command '" + cmd + "', the third level '" + w.getThirdLevel() + "' has no registered handler (key=" + key + ")");
         }
         I_CommandHandler handler = (I_CommandHandler)obj;
         String ret = handler.get(w);
         return (ret==null) ? "<qos><state id='NOT_FOUND' info='" + w.getCommand() + " has no results.'/></qos>" : ret;
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
      return "\n\rXmlBlaster administration, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.commands.html\n\r";
   }

   /**
    */
   public String help(String cmd) {
      return help();
   }

   public void shutdown() {
      if (telnetGateway != null) {
         telnetGateway.shutdown();
         telnetGateway = null; 
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

