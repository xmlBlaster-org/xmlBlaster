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
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.CommandWrapper;
import org.xmlBlaster.engine.admin.I_ExternGateway;
import org.xmlBlaster.engine.admin.SetReturn;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;

import remotecons.RemoteServer;
import remotecons.ifc.CommandHandlerIfc;
import remotecons.wttools.ConnectionServer;

import java.util.LinkedList;
import java.util.StringTokenizer;
import java.io.IOException;

/**
 * The gateway from outside telnet connections to inside CommandManager. 
 * <p />
 * @author ruff@swand.lake.de
 * @since 0.79f
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.telnet.html">admin.telnet requirement</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.command.html">admin.command requirement</a>
 */
public final class TelnetGateway implements CommandHandlerIfc, I_ExternGateway, I_Timeout
{
   private String ME;
   private Global glob;
   private LogChannel log;
   private CommandManager commandManager;
   private int port;
   private RemoteServer rs = null;
   private final String CRLF = "\r\n";
   private static int instanceCounter = 0;

   private boolean isLogin = false;
   private ConnectReturnQos connectRetQos = null;
   private String loginName = "";
   private String sessionId = null;

   private Timeout expiryTimer = new Timeout("TelnetSessionTimer");
   private Timestamp timerKey = null;
   private long sessionTimeout = 3600000L; // autologout after 1 hour

   private ConnectionServer connectionServer = null;

   private String lastCommand = "";

   /**
    * Default port to access xmlBlaster with telnet for administration (2702). 
    */
   public static final int TELNET_PORT = 2702;


   /**
    * Creates the remote console server. 
    */
   public boolean initialize(Global glob, CommandManager commandManager) throws XmlBlasterException {
      initializeVariables(glob, commandManager);
      return initListener();
   }

   private void initializeVariables(Global glob, CommandManager commandManager) {
      this.glob = glob;
      this.log = this.glob.getLog("admin");
      this.instanceCounter++;
      this.ME = "TelnetGateway" + this.instanceCounter + this.glob.getLogPraefixDashed();
      this.commandManager = commandManager;
      this.sessionTimeout = glob.getProperty().get("admin.remoteconsole.sessionTimeout", sessionTimeout);
      this.sessionTimeout = glob.getProperty().get("admin.remoteconsole.sessionTimeout[" + glob.getId() + "]", sessionTimeout);

      if (this.instanceCounter > 1) { // Ignore the first bootstrap instance
         if (sessionTimeout > 0L) {
            log.info(ME, "New connection from telnet client accepted, session timeout is " + org.jutils.time.TimeHelper.millisToNice(sessionTimeout));
            timerKey = this.expiryTimer.addTimeoutListener(this, sessionTimeout, null);
         }
         else
            log.info(ME, "Session for " + loginName + " lasts forever, requested expiry timer was 0");
      }
   }

   // Hack into remotecons to allow shutdown (marcel)
   public void register(remotecons.wttools.ConnectionServer server) {
      this.connectionServer = server;
   }

   private void stopTimer() {
      if (timerKey != null) {
         this.expiryTimer.removeTimeoutListener(timerKey);
         timerKey = null;
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
               glob.getAuthenticate().disconnect(connectRetQos.getSessionId(), null);
            }
            catch (org.xmlBlaster.util.XmlBlasterException e) {
               log.warn(e.id, e.reason);
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
      port = glob.getProperty().get("admin.remoteconsole.port", TELNET_PORT); // 0 == off
      port = glob.getProperty().get("admin.remoteconsole.port[" + glob.getId() + "]", port);
      if (port > 1000) {
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
           e.printStackTrace();
           if (log.TRACE) log.trace(ME, "Initializing of remote console on port=" + port + " failed:" + e.toString());
           throw new XmlBlasterException(ME, "Initializing of remote telnet console on port=" + port + " failed:" + e.toString());
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

         if (cmd.trim().equalsIgnoreCase("quit")) {
            lastCommand = "";
            stopTimer();
            disconnect();
            return null; // handled by internal Handler
         }

         // Commands without login:

         if (cmd.trim().equalsIgnoreCase("time")) {
            lastCommand = cmd;
            return ""+new java.util.Date()+CRLF;
         }

         if (cmd.trim().toUpperCase().startsWith("MEM")) {
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

         if (!st.hasMoreTokens()) {
            if (cmdType.trim().equalsIgnoreCase("GET") ||
                cmdType.trim().equalsIgnoreCase("SET") ||
                cmdType.trim().equalsIgnoreCase("CONNECT")) {
               lastCommand = cmd;
               return getErrorText("Ignoring your empty command '" + cmd + "'");
            }
            if (cmdType.trim().equalsIgnoreCase("echo")) {
               lastCommand = cmd;
               return null;
            }
         }

         String query = cmd.substring(cmdType.length()).trim();

         if (cmdType.trim().equalsIgnoreCase("CONNECT")) {
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
            log.info(ME, "Successful login for telnet client " + loginName + "', session timeout is " +
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

         if (cmd.trim().equalsIgnoreCase("gc")) {
            lastCommand = cmd;
            System.gc();
            return "OK\r\n";
         }

         if (cmd.trim().toUpperCase().startsWith("EXIT")) {
            lastCommand = cmd;
            return
              "\r\nYou are going to shutdown remote JVM!\r\n"+
              "Are you sure to do this and stop xmlBlaster? (yes/no): ";
         }

         if (cmd.trim().equalsIgnoreCase("yes")) {
            if (lastCommand.trim().startsWith("exit")) {
               System.exit(0);
            }
         }

         if (cmd.trim().equalsIgnoreCase("no")) {
            if (lastCommand.trim().toUpperCase().startsWith("EXIT")) {
               lastCommand = "";
               return CRLF;
            }
         }

         lastCommand = "";

         if (log.TRACE) log.trace(ME, "Invoking cmdType=" + cmdType + " query=" + query + " from '" + cmd + "'");

         if (cmdType.trim().equalsIgnoreCase("GET")) {
            MessageUnit[] msgs = commandManager.get(sessionId, query);
            if (msgs.length == 0) return "NO ENTRY FOUND: " + cmd + CRLF;
            StringBuffer sb = new StringBuffer(msgs.length * 40);
            for (int ii=0; ii<msgs.length; ii++) {
               MessageUnit msg = msgs[ii];
               if (msg.getQos().startsWith("text/plain"))
                  sb.append(msg.getXmlKey()).append("=").append(msg.getContentStr()).append(CRLF);
               else
                  sb.append(msg.toXml());
            }
            return sb.toString() + CRLF;
         }
         else if (cmdType.trim().equalsIgnoreCase("SET")) {
            SetReturn ret = commandManager.set(sessionId, query);
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
             "   connect [name] [passwd]  Login with you login name and password" + CRLF +
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
      //if (log.TRACE) log.trace(ME, "getInstance() is returning myself");
      TelnetGateway telnetGateway = new TelnetGateway();
      telnetGateway.initializeVariables(glob, commandManager);
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
         throw new XmlBlasterException("loginFailed.InvalidArguments", "login failed: please use 'connect loginName password'");
      }

      try {
         ConnectQos connectQos = new ConnectQos(glob, loginName, passwd);
         connectQos.setSessionTimeout(sessionTimeout);
         this.connectRetQos = glob.getAuthenticate().connect(connectQos);
         this.loginName = loginName;
         this.sessionId = connectRetQos.getSessionId();
         isLogin = true;

         if (connectQos.getSessionTimeout() > 0L) {
            stopTimer();
            if (log.TRACE) log.trace(ME, "Setting expiry timer for " + loginName + " to " + connectQos.getSessionTimeout() + " msec");
            timerKey = this.expiryTimer.addTimeoutListener(this, connectQos.getSessionTimeout(), null);
         }
         else
            log.info(ME, "Session for " + loginName + " lasts forever, requested expiry timer was 0");
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }

   public void shutdown() {
      if (rs != null) {
         rs.disable();
         rs = null;
         if (log.TRACE) log.trace(ME, "Shutdown done, telnet disabled.");
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

      sb.append(offset).append("<telnetGateway>");
      sb.append(offset).append("</telnetGateway>");

      return sb.toString();
   }
}

