/*------------------------------------------------------------------------------
Name:      MsgHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementation for administrative message access
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin.intern;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.admin.I_CommandHandler;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.CommandWrapper;
import org.xmlBlaster.engine.admin.I_AdminNode;
import org.xmlBlaster.engine.admin.I_AdminSubject;
import org.xmlBlaster.engine.admin.I_AdminSession;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.authentication.SessionInfo;


/**
 * Implementation of administrative access to xmlBlaster messages. 
 * @author ruff@swand.lake.de 
 * @since 0.79g
 */
final public class MsgHandler implements I_CommandHandler, I_Plugin {

   private String ME = "MsgHandler";
   private Global glob = null;
   private LogChannel log = null;
   private CommandManager commandManager = null;

   /**
    * This is called after creation of the plugin. 
    * @param glob The Global handle of this xmlBlaster server instance.
    * @param commandManager My big brother taking care of me
    */
   public void initialize(Global glob, CommandManager commandManager) {
      this.glob = glob;
      this.log = this.glob.getLog("admin");
      this.commandManager = commandManager;
      this.ME = this.ME + "-" + glob.getId();
      this.commandManager.register("msg", this);
      log.info(ME, "Message administration plugin is initialized");
   }

   /**
    * This method is called by the PluginManager.
    * <p />
    * This xmlBlaster.properties entry example
    * <pre>
    *   LoadBalancerPlugin[MsgHandler][1.0]=org.xmlBlaster.engine.command.simpledomain.MsgHandler,DEFAULT_MAX_LEN=200
    * </pre>
    * passes 
    * <pre>
    *   options[0]="DEFAULT_MAX_LEN"
    *   options[1]="200"
    * </pre>
    * <p/>
    * @param Global   An xmlBlaster instance global object holding logging and property informations
    * @param String[] Some arguments from xmlBlaster.properties.
    */
   public void init(org.xmlBlaster.util.Global glob, String[] options) throws XmlBlasterException {
      if (options != null) {
         for (int ii=0; ii<options.length-1; ii++) {
            if (options[ii].equalsIgnoreCase("DUMMY")) {
               //DUMMY = (new Long(options[++ii])).longValue();  // ...
            }
         }
      }
   }

   /**
    * Return plugin type for Plugin loader
    * @return "MsgHandler"
    */
   public String getType() {
      return "MsgHandler";
   }

   /**
    * Return plugin version for Plugin loader
    * @return "1.0"
    */
   public String getVersion() {
      return "1.0";
   }

   /**
    * Get a human readable name of this filter implementation
    * @return "MsgHandler"
    */
   public String getName() {
      return "MsgHandler";
   }

   /**
    * Your plugin should process the command. 
    * <p />
    * @param sessionId Is null if not logged in
    * @param cmd "/node/heron/msg/HelloMsgOid/?content"
    * @return "key=value" or null if not found, e.g. "/node/heron/sysprop/?user.home=/home/joe"
    * @see org.xmlBlaster.engine.admin.I_CommandHandler#get(String,CommandWrapper)
    */
   public synchronized MessageUnit[] get(String sessionId, CommandWrapper cmd) throws XmlBlasterException {
      if (cmd == null)
         throw new XmlBlasterException(ME, "Please pass a command which is not null");

      String client = cmd.getThirdLevel();
      if (client == null || client.length() < 1)
         throw new XmlBlasterException(ME, "Please pass a command which has a valid message oid added, '" + cmd.getCommand() + "' is too short, aborted request.");

      if (client.startsWith("?")) {
         // for example "/node/heron/?freeMem"
         throw new XmlBlasterException(ME, "Please pass a command which has a valid message oid added, '" + cmd.getCommand() + "' is too short, aborted request.");
      }

      I_XmlBlaster xmlBlaster = glob.getAuthenticate().getXmlBlaster();

      //  /node/heron/msg/?hello
      //  /node/heron/msg/hello/?content
      String oidTmp = cmd.getUserNameLevel();
      if (oidTmp == null || oidTmp.length() < 1)
         throw new XmlBlasterException(ME, "Please pass a command which has a valid message oid added, '" + cmd.getCommand() + "' is too short, aborted request.");

      String oid = oidTmp;
      if (oidTmp.startsWith("?"))
         oid = oidTmp.substring(1);
      
      String xmlKey = "<key oid='" + oid + "'/>";
      String qos = "<qos/>";

      MessageUnit[] msgUnitArr = xmlBlaster.get(sessionId, xmlKey, qos);
         
      log.info(ME, cmd.getCommand() + " returned " + msgUnitArr.length + " messages");

         if (log.DUMP) {
            for (int ii=0; ii<msgUnitArr.length; ii++) {
               log.dump(ME, msgUnitArr[ii].toXml());
            }
         }

      return msgUnitArr;
   }

   /**
    * Set a value. 
    * @param sessionId Is null if not logged in
    * @param cmd "/node/heron/msg/HelloMsgOid/?content=World"
    * @return null if not set
    */
   public String set(String sessionId, CommandWrapper cmd) throws XmlBlasterException {
      if (cmd == null)
         throw new XmlBlasterException(ME, "Please pass a command which is not null");

      String client = cmd.getThirdLevel();
      if (client == null || client.length() < 1)
         throw new XmlBlasterException(ME, "Please pass a command which has a valid message oid added, '" + cmd.getCommand() + "' is too short, aborted request.");

      if (client.startsWith("?")) {
         // for example "/node/heron/msg/Hello/?content=World!"
         throw new XmlBlasterException(ME, "Please pass a command which has a valid message oid added, '" + cmd.getCommand() + "' is too short, aborted request.");
      }

      I_XmlBlaster xmlBlaster = glob.getAuthenticate().getXmlBlaster();

      //  /node/heron/msg/?hello
      //  /node/heron/msg/hello/?content=Hello world
      String oidTmp = cmd.getUserNameLevel();
      if (oidTmp == null || oidTmp.length() < 1)
         throw new XmlBlasterException(ME, "Please pass a command which has a valid message oid added, '" + cmd.getCommand() + "' is too short, aborted request.");

      String oid = oidTmp;
      if (oidTmp.startsWith("?"))
         throw new XmlBlasterException(ME, "Please pass a command which has a valid message oid added, '" + cmd.getCommand() + "' is too short, aborted request.");
      
      String xmlKey = "<key oid='" + oid + "'/>";
      String qos = "<qos/>";

      MessageUnit[] msgUnitArr = xmlBlaster.get(sessionId, xmlKey, qos);

      if (msgUnitArr.length < 1) {
         log.info(ME, cmd.getCommand() + " Message oid=" + oid + " not found");
         return null;
      }
         
      //  /node/heron/msg/hello/?content=Hello world
      String key = cmd.getKey();     // -> "content"
      String value = cmd.getValue(); // -> "Hello world"

      if (!key.equalsIgnoreCase("content"))
         throw new XmlBlasterException(ME, "Only the 'content' can currently be changed on messages," + 
                      " try something like '?content=Hello world'");

      for (int ii=0; ii<msgUnitArr.length; ii++) {
         msgUnitArr[ii].setContent(value.getBytes());
         msgUnitArr[ii].setQos("<qos/>"); // We kill the original QoS
      }
      String[] retArr = xmlBlaster.publishArr(sessionId, msgUnitArr);

      log.info(ME, cmd.getCommand() + " published " + msgUnitArr.length + " messages");
      StringBuffer sb = new StringBuffer(retArr.length * 60);
      for (int ii=0; ii<retArr.length; ii++) {
         sb.append(retArr[ii]);
      }
      return sb.toString();
   }

   public String help() {
      return "Administration of xmlBlaster messages.";
   }

   public String help(String cmd) {
      return help();
   }

   public void shutdown() {
      if (log.TRACE) log.trace(ME, "Shutdown ignored, nothing to do");
   }
} // end of class MsgHandler
