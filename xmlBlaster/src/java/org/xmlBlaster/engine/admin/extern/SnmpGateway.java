/*------------------------------------------------------------------------------
Name:      SnmpGateway.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main manager class for administrative commands
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin.extern;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.I_ExternGateway;

import org.xmlBlaster.engine.admin.extern.snmp.*;

import java.io.*;
import java.util.Vector;
import java.util.Enumeration;
import java.net.*;
import java.lang.Integer;
import jax.*; // import SNMP subagent specific classes

/**
 * The gateway from outside SNMP connections to inside CommandManager. 
 * <p />
 * @author ruff@swand.lake.de
 * @since 0.79f
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.snmp.html">admin.snmp requirement</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.commands.html">admin.commands requirement</a>
 */
public final class SnmpGateway implements I_ExternGateway // , SnmpInterface ?
{
   private String ME;

   private Global glob;
   private LogChannel log;
   private CommandManager manager;
   private String sessionId = null;

   /** port for agentX connection, where SNMP-agent listens for our sub agent */
   private int port = 705;
   private String hostname;

   private AgentXConnection connection;
   private AgentXSession session;
   private AgentXRegistration registration;

   /**
    * This is called after creation of the plugin. 
    * <p />
    * You should register yourself with commandManager.register() during initialization.
    *
    * @param glob The Global handle of this xmlBlaster server instance.
    * @param commandManager My manager
    * @return false Ignore this implementation (gateway is switched off)
    */
   public boolean initialize(Global glob, CommandManager commandManager) throws XmlBlasterException {
      this.glob = glob;
      this.log = this.glob.getLog("admin");
      this.ME = "SnmpGateway" + this.glob.getLogPrefixDashed();
      this.manager = commandManager;

      boolean useSnmp = glob.getProperty().get("admin.snmp", false);
      useSnmp = glob.getProperty().get("admin.snmp[" + glob.getId() + "]", useSnmp);
      if (!useSnmp) return false;

      boolean ret = initSubagent();
      log.trace(ME, "Started SNMP gateway for administration, try 'snmpget -v 1 -c public " + glob.getLocalIP() + " 1.3.6.1.4.1.11662.1.2.1.3' to access it.");
      return ret;
   }

   /**
    * Creates a server which is accessible with SNMP. 
    * This allows you to access xmlBlaster and query for example the free memory:
    * <pre>
    *  SNMP 192.168.1.2 2702
    *  mem
    * </pre>
    * Enter 'help' for all available commands.
    * @return true if subagent is configured and active
    */
   private boolean initSubagent() throws XmlBlasterException {
      // throw new XmlBlasterException(ME, "Initializing of SNMP subagent failed:" + e.toString());
      this.port = glob.getProperty().get("admin.snmp.port", this.port);
      this.port = glob.getProperty().get("admin.snmp.port[" + glob.getId() + "]", this.port);

      this.hostname = glob.getProperty().get("admin.snmp.hostname", glob.getBootstrapAddress().getHostname());
      this.hostname = glob.getProperty().get("admin.snmp.hostname[" + glob.getId() + "]", this.hostname);

      boolean debug = glob.getProperty().get("admin.snmp.debug", false);
      debug = glob.getProperty().get("admin.snmp.debug[" + glob.getId() + "]", debug);

      System.setProperty("jax.debug", ""+debug);

      if (this.port < 1) {
         log.warn(ME, "SNMP subagent is switched off, please provide admin.snmp.port > 0 to switch it on");
         return false;
      }

      try {
         log.info(ME, "Subagent connection over AGENTX to SNMP-agent on " + this.hostname + ":" + this.port);

         connection = new AgentXConnection(this.hostname, this.port);

         log.info(ME, "Subagent connection over AGENTX to SNMP-agent on " + this.hostname + ":" + this.port + " established");

         session = new AgentXSession();
         connection.openSession(session);

         registration = new AgentXRegistration(new AgentXOID(Constants.XMLBLASTER_OID_ROOT));
         session.register(registration);

         log.info(ME, "Subagent registered");

      } catch (Exception e) {
         String text = "Subagent connection over AGENTX to SNMP-agent on " + this.hostname + ":" + this.port + " failed:" + e.toString();
         log.error(ME, text);
         throw new XmlBlasterException(ME, text);
      }

      initMib();
      return true;
   }

   private void initMib() {
      NodeScalarImpl nodeScalarImpl;
      NodeEntryImpl nodeEntryImpl;
      NodeTable nodeTable;
      NodeTableSubject nodeTableSubject;
      NodeTableObserver nodeTableObserver;
      ConnectionTableSubject connectionTableSubject;
      ConnectionTableObserver connectionTableObserver;

      /*
      nodeScalarImpl = new NodeScalarImpl();
      session.addGroup(nodeScalarImpl);

      // create concrete subjects and observers (observer pattern)
      nodeTableSubject = new NodeTableSubject();
      nodeTableObserver = new NodeTableObserver(nodeTableSubject, session);
      connectionTableSubject = new ConnectionTableSubject();
      connectionTableObserver = new ConnectionTableObserver(connectionTableSubject, session);

      nodeEntryImpl = new NodeEntryImpl(1, "node1", "host1", 111, 1161, 80, "err1.log", 1);

      // add entries to concrete subjects using the observer pattern
      nodeTableSubject.addEntry("node11", "host11", 111, 1161, 80, "err1.log", 1);
      nodeTableSubject.addEntry("node22", "host22", 222, 1162, 20, "err2.log", 2);
      connectionTableSubject.addEntry(nodeTableObserver, "node11", "hostAAA", 4711, "192.47.11", 5);
      connectionTableSubject.addEntry(nodeTableObserver, "node22", "hostBBB", 3333, "192.3.3.3.3",675);
      */
   }

   /**
    * @param cmd The snmp command
    */
   public String getCommand(String cmd) {
      try {
         if (cmd == null || cmd.length() < 1) {
            return getErrorText("Ignoring your empty command.");
         }
         cmd = cmd.trim();
         if (cmd.length() < 1) {
            return getErrorText("Ignoring your empty command.");
         }

         // !!! Transfer MIB command to internal command !!!!
         // e.g. query="/node/heron/?freeMem"
         String query = cmd; // TODO mapping

         if (log.TRACE) log.trace(ME, "Invoking SNMP cmd=" + cmd + " as query=" + query);

         MessageUnit[] msgs = manager.get(sessionId, query);
         if (msgs.length == 0)
            return "NOT FOUND";
         else {
            String retValue = "";
            for (int ii=0; ii<msgs.length; ii++) {
               MessageUnit msg = msgs[ii];
               if (msg.getQos().startsWith("text/plain")) {
                  retValue = msg.getContentStr() + ", "; // How to handle multi return with SNMP ???
                  // msg.getXmlKey() and msg.getContentStr() contain the data
               }
            }
            return retValue;
         }
      }
      catch (XmlBlasterException e) {
         if (log.TRACE) log.trace(ME+".SNMP", e.toString());
         return e.toString();
      }
   }

   private final String getErrorText(String error) {
      String text = "ERROR-XmlBlaster SNMP server: " + error + "Try a 'snmpget ...'.";
      log.info(ME, error);
      return text;
   }

   public String help() {
      return "XmlBlaster SNMP administration, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.snmp.html";
   }

   public String help(String cmd) {
      return help();
   }

   public String getName() {
      return "SnmpGateway";
   }

   public void shutdown() {
      if (session != null) {
         try {
            session.unregister(registration);
            session.close(AgentXSession.REASON_SHUTDOWN);
            session = null;
         } catch (Exception e) {
            log.warn(ME, "Problems on shutdown: " + e.toString());
         }
      }
      if (connection != null) {
         try {
            connection.close();
            connection = null;
         } catch (Exception e) {
            log.warn(ME, "Problems on disconnect: " + e.toString());
         }
      }
      log.info(ME, "Subagent connection over AGENTX to SNMP-agent on " + this.hostname + ":" + this.port + " is shutdown");
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

      sb.append(offset).append("<SnmpGateway>");
      sb.append(offset).append("</SnmpGateway>");

      return sb.toString();
   }
}

