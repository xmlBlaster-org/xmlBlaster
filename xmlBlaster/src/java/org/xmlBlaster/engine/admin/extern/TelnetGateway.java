/*------------------------------------------------------------------------------
Name:      TelnetGateway.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main manager class for administrative commands
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin.extern;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.I_ExternGateway;
import org.xmlBlaster.engine.admin.SetReturn;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.key.QueryKeyData;

import remotecons.RemoteServer;
import remotecons.ifc.CommandHandlerIfc;
import remotecons.wttools.ConnectionServer;

import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import java.io.IOException;

/**
 * The gateway from outside telnet connections to inside CommandManager. 
 * <p />
 * @author xmlBlaster@marcelruff.info
 * @since 0.79f
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.telnet.html">admin.telnet requirement</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.commands.html">admin.commands requirement</a>
 */
public final class TelnetGateway implements CommandHandlerIfc, I_ExternGateway, I_Timeout
{
   private String ME;
   private Global glob;
   private LogChannel log;
   private CommandManager commandManager;
   private int port;
   private RemoteServer rs;
   private final String CRLF = "\r\n";
   private static int instanceCounter = 0;
   private boolean isShutdown = true;

   private boolean isLogin = false;
   private ConnectReturnQosServer connectRetQos;
   private String loginName = "";
   private String sessionId;

   private Set telnetInstancesSet;

   private Timestamp timerKey;
   private long sessionTimeout = 3600000L; // autologout after 1 hour

   private ConnectionServer connectionServer;
   private AddressServer addressServer;

   private String lastCommand = "";

   /**
    * Default port to access xmlBlaster with telnet for administration (2702). 
    */
   public static final int TELNET_PORT = 2702;

   public TelnetGateway() {
      instanceCounter++;
   }

   /**
    * Creates the remote console server. 
    * <p />
    * Is called by CommandManager on startup
    * @return true if started and active
    */
   public boolean initialize(Global glob, CommandManager commandManager) throws XmlBlasterException {
      initializeVariables(glob, commandManager, true);
      return initListener();
   }

   /**
    * @param isBootstrap The first instance has no timer set
    * @return true if started and active
    */
   private boolean initializeVariables(Global glob, CommandManager commandManager, boolean isBootstrap) {
      this.glob = glob;
      this.log = this.glob.getLog("admin");
      this.ME = "TelnetGateway" + instanceCounter + this.glob.getLogPrefixDashed();
      this.telnetInstancesSet = new HashSet();
      this.commandManager = commandManager;
      this.sessionTimeout = glob.getProperty().get("admin.remoteconsole.sessionTimeout", sessionTimeout);
      this.sessionTimeout = glob.getProperty().get("admin.remoteconsole.sessionTimeout[" + glob.getId() + "]", sessionTimeout);
      this.port = glob.getProperty().get("admin.remoteconsole.port", TELNET_PORT); // 0 == off
      this.port = glob.getProperty().get("admin.remoteconsole.port[" + glob.getId() + "]", this.port);
      if (this.port <= 1000) {
         if (log.TRACE) log.trace(ME, "No telnet gateway configured, port=" + port + " try '-admin.remoteconsole.port " + TELNET_PORT + "' if you want one");
         return false;
      }

      if (!isBootstrap) { // Ignore the first bootstrap instance
         if (sessionTimeout > 0L) {
            log.info(ME, "New connection from telnet client accepted, session timeout is " + org.jutils.time.TimeHelper.millisToNice(sessionTimeout));
            timerKey = glob.getTelnetSessionTimer().addTimeoutListener(this, sessionTimeout, null);
         }
         else
            log.info(ME, "Session for " + loginName + " lasts forever, requested expiry timer was 0");
      }
      return true;
   }

   // Hack into remotecons to allow shutdown (marcel)
   public void register(remotecons.wttools.ConnectionServer server) {
      this.connectionServer = server;
   }

   private void stopTimer() {
      if (this.timerKey != null && glob.hasTelnetSessionTimer()) {
         this.glob.getTelnetSessionTimer().removeTimeoutListener(this.timerKey);
         this.timerKey = null;
      }
   }

   protected void finalize() {
      stopTimer();
      disconnect();
   }

   /**
    * We are notified when this session expires. 
    * @param userData You get bounced back your userData which you passed
    *                 with Timeout.addTimeoutListener()
    */
   public final void timeout(Object userData)
   {
      synchronized (this) {
         timerKey = null;
         if (isLogin)
            log.warn(ME, "Session timeout " + org.jutils.time.TimeHelper.millisToNice(sessionTimeout) + " for telnet client " + loginName + " occurred, autologout.");
         else
            log.warn(ME, "Session timeout " + org.jutils.time.TimeHelper.millisToNice(sessionTimeout) + " for not authorized telnet client occurred, autologout.");
      }
      //disconnect(); This happens automatically in Authenticate.java at the same time
      connectRetQos = null;
      if (connectionServer != null)
         connectionServer.shutdown();  // Hack into remotecons to allow shutdown (marcel)
   }

   private synchronized void disconnect() {
      if (isLogin) {
         if (connectRetQos != null) {
            try {
               glob.getAuthenticate().disconnect(this.addressServer, connectRetQos.getSecretSessionId(), null);
            }
            catch (org.xmlBlaster.util.XmlBlasterException e) {
               log.warn(ME, e.getMessage());
            }
            log.info(ME, "Logout of '" + loginName + "', telnet connection destroyed");
            connectRetQos = null;
         }
         else
            log.info(ME, "Connection from not authorized telnet client destroyed");
      }
      isLogin = false;
   }

   private boolean initListener() throws XmlBlasterException { 
      if (this.port > 1000) {
         createRemoteConsole(port);
         log.info(ME, "Started remote console server for administration, try 'telnet " + glob.getLocalIP() + " " + port + "' to access it and type 'help'.");
         return true;
      }

      if (log.TRACE) log.trace(ME, "No telnet gateway configured, port=" + port + " try '-admin.remoteconsole.port " + TELNET_PORT + "' if you want one");
      return false;
   }

   /**
    * Creates a server which is accessible with telnet. 
    * This allows you to access xmlBlaster and query for example the free memory:
    * <pre>
    *  telnet 192.168.1.2 2702
    *  mem
    * </pre>
    * Enter 'help' for all available commands.
    */
   private void createRemoteConsole(int port) throws XmlBlasterException {
      if (port > 1000) {
         rs = new RemoteServer();
         rs.setServer_port(port);
         rs.setAs_daemon(true);
         LinkedList ll = new LinkedList();
         ll.add(this);
         try {
           rs.initialize(ll);
         } catch (IOException e) {
           //e.printStackTrace();
           if (log.TRACE) log.trace(ME, "Initializing of remote console on port=" + port + " failed:" + e.toString());
           throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Initializing of remote telnet console on port=" + port + " failed:" + e.toString());
         }
      }
   }

   /**
    * Enforced by "remotecons.CommandHandlerIfc"
    */
   public String handleCommand(String cmd) {
      try {
         if (cmd == null || cmd.length() < 1) {
            lastCommand = "";
            return getErrorText("Ignoring your empty command.");
         }
         cmd = cmd.trim();
         if (cmd.length() < 1) {
            lastCommand = "";
            return getErrorText("Ignoring your empty command.");
         }

         if (cmd.equalsIgnoreCase("quit")) {
            lastCommand = "";
            stopTimer();
            disconnect();
            return null; // handled by internal Handler
         }

         // Commands without login:

         if (cmd.equalsIgnoreCase("time")) {
            lastCommand = cmd;
            return ""+new java.util.Date()+CRLF;
         }

         if (cmd.toUpperCase().startsWith("MEM")) {
            lastCommand = cmd;
            Runtime rt = Runtime.getRuntime();
            return ""+rt.totalMemory()+"/"+rt.freeMemory()+CRLF;
         }

         StringTokenizer st = new StringTokenizer(cmd, " ");
         if (!st.hasMoreTokens()) {
            lastCommand = cmd;
            return getErrorText("Ignoring your empty command.");
         }
         String cmdType = (String)st.nextToken();
         cmdType = cmdType.trim();

         if (!st.hasMoreTokens()) {
            if (cmdType.equalsIgnoreCase("GET") ||
                cmdType.equalsIgnoreCase("SET") ||
                cmdType.equalsIgnoreCase("CONNECT")) {
               lastCommand = cmd;
               return getErrorText("Ignoring your empty command '" + cmd + "'");
            }
            if (cmdType.equalsIgnoreCase("echo")) {
               lastCommand = cmd;
               return null;
            }
         }

         String query = cmd.substring(cmdType.length()).trim();

         if (cmdType.equalsIgnoreCase("CONNECT")) {
            if (!st.hasMoreTokens()) {
               lastCommand = cmd;
               return getErrorText("Please give me a login name and password to connect: '" + cmd + " <name> <passwd>'");
            }
            String loginName = (String)st.nextToken();
            if (!st.hasMoreTokens()) {
               lastCommand = cmd;
               return getErrorText("Please give me a password to connect: '" + cmd + " <passwd>'");
            }
            String passwd = (String)st.nextToken();
            connect(loginName, passwd); // throws Exception or sets isLogin=true  
            log.info(ME, "Successful login for telnet client '" + loginName + "', session timeout is " +
                     org.jutils.time.TimeHelper.millisToNice(sessionTimeout));
            lastCommand = cmd;
            return "Successful login for user " + loginName + ", session timeout is " +
                     org.jutils.time.TimeHelper.millisToNice(sessionTimeout) + CRLF;
         }

         if (!isLogin) {
            lastCommand = cmd;
            return getErrorText("Please login first with 'connect <loginName> <password>'");
         }

         // Commands with login only:

         if (cmd.equalsIgnoreCase("gc")) {
            lastCommand = cmd;
            System.gc();
            return "OK\r\n";
         }

         if (cmd.toUpperCase().startsWith("EXIT")) {
            lastCommand = cmd;
            return
              "\r\nYou are going to shutdown remote JVM!\r\n"+
              "Are you sure to do this and stop xmlBlaster? (yes/no): ";
         }

         if (cmd.equalsIgnoreCase("yes")) {
            if (lastCommand.trim().startsWith("exit")) {
               System.exit(0);
            }
         }

         if (cmd.equalsIgnoreCase("no")) {
            if (lastCommand.trim().toUpperCase().startsWith("EXIT")) {
               lastCommand = "";
               return CRLF;
            }
         }

         lastCommand = "";

         if (log.TRACE) log.trace(ME, "Invoking cmdType=" + cmdType + " query=" + query + " from '" + cmd + "'");

         if (cmdType.equalsIgnoreCase("GET")) {
            QueryKeyData keyData = new QueryKeyData(this.glob);
            keyData.setOid("__cmd:" + query);
            MsgUnit[] msgs = commandManager.get(this.addressServer, sessionId, keyData, null);
            if (msgs.length == 0) return "NO ENTRY FOUND: " + cmd + CRLF;
            StringBuffer sb = new StringBuffer(msgs.length * 40);
            for (int ii=0; ii<msgs.length; ii++) {
               MsgUnit msg = msgs[ii];
               if (msg.getQos().startsWith("text/plain"))
                  sb.append(msg.getKey()).append("=").append(msg.getContentStr()).append(CRLF);
               else
                  sb.append(msg.toXml());
            }
            return sb.toString() + CRLF;
         }
         else if (cmdType.equalsIgnoreCase("SET")) {
            SetReturn ret = commandManager.set(this.addressServer, sessionId, query);
            if (ret == null) return "NO ENTRY SET: " + ret.commandWrapper.getCommand() + CRLF;
            return ret.commandWrapper.getCommandStripAssign() + "=" + ret.returnString + CRLF;
         }
         else {
            return null;
            //return getErrorText("Ignoring unknown command '" + cmdType + "' of '" + cmd + "'" + CRLF);
         }
      }
      catch (XmlBlasterException e) {
         if (log.TRACE) log.trace(ME+".telnet", e.toString());
         return CRLF + e.toString() + CRLF + CRLF;
      }
   }

   private final String getErrorText(String error) {
      String text = "ERROR-XmlBlaster telnet server: " + error + CRLF;
      if (isLogin) {
         text += "Try a 'get sysprop/?user.home' or 'set sysprop/?trace[core]=true' or just 'help'" + CRLF + CRLF;
      }
      else {
         text += "Try 'help'" + CRLF + CRLF;
      }
      log.info(ME, error);
      return text;
   }

   /**
    * Enforced by "remotecons.CommandHandlerIfc"
    */
   public String help() {
      return CRLF +
             "  XmlBlaster telnet administration" + CRLF +
             "   connect [name] [passwd]  Login with your login name and password" + CRLF +
             "   get [query]              Get property or xmlBlaster state" + CRLF +
             "   set [query]              Set a property or change xmlBlaster setting" + CRLF +
             "   time                     Display current time on server" + CRLF +
             "   gc                       Run System.gc() command on remote system" + CRLF +
             "   mem [total|free]         Display amount of memory on remote system" + CRLF +
             "   exit                     Call System.exit(0) on remote system" + CRLF +
             "  For query syntax see" + CRLF +
             "  http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.telnet.html" + CRLF +
             "  http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.commands.html" + CRLF + CRLF;
   }
   /**
    * Enforced by "remotecons.CommandHandlerIfc"
    */
   public String help(String cmd) {
      return "";
   }

   public CommandHandlerIfc getInstance() {
      //log.error(ME, "DEBUG ONLY: Entering getInstance(isShutdown="+isShutdown+", port="+this.port+")");
      if (isShutdown) return this; // Called on shutdown, we need to investigate and redesign the whole baby

      if (this.port <= 1000) {
         return null;
      }

      //!!!! register to CommandManager as it needs to destroy the timer?? what in cluster env?

      TelnetGateway telnetGateway = new TelnetGateway();
      telnetGateway.initializeVariables(glob, commandManager, false);
      this.telnetInstancesSet.add(telnetGateway); 
      return telnetGateway;
   }

   public String getName() {
      return "TelnetGateway";
   }

   /**
    * Login to xmlBlaster server. 
    */
   public void connect(String loginName, String passwd) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering login(loginName=" + loginName/* + ", qos=" + qos_literal */ + ")");

      if (loginName==null || passwd==null) {
         log.error(ME+"InvalidArguments", "login failed: please use no null arguments for login()");
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".connect", "login failed: please use 'connect loginName password'");
      }

      org.xmlBlaster.client.qos.ConnectQos clientConnectQos = new org.xmlBlaster.client.qos.ConnectQos(glob, loginName, passwd);
      clientConnectQos.setSessionTimeout(sessionTimeout);
      ConnectQosServer connectQos = new ConnectQosServer(glob, clientConnectQos.getData());
      this.addressServer = new AddressServer(glob, "NATIVE", glob.getId(), (java.util.Properties)null);
      connectQos.setAddressServer(this.addressServer);
      this.connectRetQos = glob.getAuthenticate().connect(this.addressServer, connectQos);
      this.loginName = loginName;
      this.sessionId = connectRetQos.getSecretSessionId();
      isLogin = true;

      if (connectQos.getSessionTimeout() > 0L) {
         stopTimer();
         if (log.TRACE) log.trace(ME, "Setting expiry timer for " + loginName + " to " + connectQos.getSessionTimeout() + " msec");
         timerKey = this.glob.getTelnetSessionTimer().addTimeoutListener(this, connectQos.getSessionTimeout(), null);
      }
      else
         log.info(ME, "Session for " + loginName + " lasts forever, requested expiry timer was 0");
   }

   public void shutdown() {
      //Thread.currentThread().dumpStack();
      if (log.CALL) log.call(ME, "Invoking shutdown()");
      isLogin = false;
      if (this.glob.hasTelnetSessionTimer()) {
         stopTimer();
         this.glob.removeTelnetSessionTimer();
      }
      disconnect();
      if (this.telnetInstancesSet != null) {
         Iterator it = telnetInstancesSet.iterator();
         while(it.hasNext()) {
            TelnetGateway gw = (TelnetGateway)it.next();
            gw.shutdown();
         }
         this.telnetInstancesSet.clear();
         //telnetInstancesSet = null;
      }
      if (rs != null) {
         rs.disable();
         rs = null;
         if (log.TRACE) log.trace(ME, "Shutdown done, telnet disabled.");
      }
      isShutdown = true;
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
      String offset = "\n ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<telnetGateway");
      sb.append(" port='").append(this.port).append("'");
      sb.append(" loginName='").append(this.loginName).append("'");
      sb.append(" numInstances='").append((this.telnetInstancesSet != null)?this.telnetInstancesSet.size():0).append("'");
      sb.append(">");
      if (this.glob.hasTelnetSessionTimer()) {
         sb.append(offset).append(" <hasTimer/>");
      }
      sb.append(offset).append("</telnetGateway>");

      return sb.toString();
   }
}

