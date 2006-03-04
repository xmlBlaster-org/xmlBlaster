/*------------------------------------------------------------------------------
Name:      MsgHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementation for administrative message access
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin.intern;

import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.engine.admin.I_CommandHandler;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.CommandWrapper;
import org.xmlBlaster.protocol.I_XmlBlaster;

/**
 * Implementation of administrative access to xmlBlaster messages. 
 * @author xmlBlaster@marcelruff.info 
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
      this.ME = "MsgHandler" + this.glob.getLogPrefixDashed();
      // "topic" now handled by CoreHandler.java to have all MBean accessors, changed 2006-02-028, marcel
      //this.commandManager.register(ContextNode.TOPIC_MARKER_TAG, this);
      // For old behavior we have no "_topic":
      this.commandManager.register("_"+ContextNode.TOPIC_MARKER_TAG, this);
      log.trace(ME, "Message administration plugin is initialized for '_topic/?content' etc.");
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
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
    * @see org.xmlBlaster.engine.admin.I_CommandHandler#get(String,CommandWrapper)
    */
   public synchronized MsgUnit[] get(AddressServer addressServer, String sessionId, CommandWrapper cmd) throws XmlBlasterException {
      if (cmd == null)
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".get", "Please pass a command which is not null");

      String client = cmd.getThirdLevel();
      if (client == null || client.length() < 1)
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".get", "Please pass a command which has a valid message oid added, '" + cmd.getCommand() + "' is too short, aborted request.");

      if (client.startsWith("?")) {
         // for example "/node/heron/?freeMem"
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".get", "Please pass a command which has a valid message oid added, '" + cmd.getCommand() + "' is too short, aborted request.");
      }

      I_XmlBlaster xmlBlaster = glob.getAuthenticate().getXmlBlaster();

      //  /node/heron/topic/?hello
      //  /node/heron/topic/hello/?content
      String oidTmp = cmd.getUserNameLevel();
      if (oidTmp == null || oidTmp.length() < 1)
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".get", "Please pass a command which has a valid message oid added, '" + cmd.getCommand() + "' is too short, aborted request.");

      String oid = oidTmp;
      if (oidTmp.startsWith("?"))
         oid = oidTmp.substring(1);
      
      // TODO should use the key from commandManager
      String xmlKey = "<key oid='" + oid + "'/>";
      // String qos = "<qos/>";

      MsgUnitRaw[] msgUnitArrRaw = xmlBlaster.get(addressServer, sessionId, xmlKey, "<qos/>");//cmd.getQueryQosData().toXml());
      MsgUnit[] msgUnits = new MsgUnit[msgUnitArrRaw.length];
      MethodName method = MethodName.GET; // cmd.getMethod();
      for (int i=0; i < msgUnits.length; i++) {
         msgUnits[i] = new MsgUnit(this.glob, msgUnitArrRaw[i], method);
      }
      log.info(ME, cmd.getCommand() + " returned " + msgUnitArrRaw.length + " messages");

      if (log.DUMP) {
         for (int ii=0; ii<msgUnitArrRaw.length; ii++) {
            log.dump(ME, msgUnitArrRaw[ii].toXml());
         }
      }
      return msgUnits;
   }

   /**
    * Set a value. 
    * @param sessionId Is null if not logged in
    * @param cmd "/node/heron/topic/HelloMsgOid/?content=World"
    * @return null if not set
    */
   public String set(AddressServer addressServer, String sessionId, CommandWrapper cmd) throws XmlBlasterException {
      if (cmd == null)
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".set", "Please pass a command which is not null");

      String client = cmd.getThirdLevel();
      if (client == null || client.length() < 1)
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".set", "Please pass a command which has a valid message oid added, '" + cmd.getCommand() + "' is too short, aborted request.");

      if (client.startsWith("?")) {
         // for example "/node/heron/topic/Hello/?content=World!"
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".set", "Please pass a command which has a valid message oid added, '" + cmd.getCommand() + "' is too short, aborted request.");
      }

      I_XmlBlaster xmlBlaster = glob.getAuthenticate().getXmlBlaster();

      //  /node/heron/topic/?hello
      //  /node/heron/topic/hello/?content=Hello world
      String oidTmp = cmd.getUserNameLevel();
      if (oidTmp == null || oidTmp.length() < 1)
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".set", "Please pass a command which has a valid message oid added, '" + cmd.getCommand() + "' is too short, aborted request.");

      String oid = oidTmp;
      if (oidTmp.startsWith("?"))
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".set", "Please pass a command which has a valid message oid added, '" + cmd.getCommand() + "' is too short, aborted request.");
      
      String xmlKey = "<key oid='" + oid + "'/>";
      String qos = "<qos/>";

      // The returned array is a clone, we may manipulate it
      MsgUnitRaw[] msgUnitArrRaw = xmlBlaster.get(addressServer, sessionId, xmlKey, qos);

      if (msgUnitArrRaw.length < 1) {
         log.info(ME, cmd.getCommand() + " Message oid=" + oid + " not found");
         return null;
      }
         
      //  /node/heron/topic/hello/?content=Hello world
      String key = cmd.getKey();     // -> "content"
      //String value = cmd.getValue(); // -> "Hello world"

      if (!key.equalsIgnoreCase("content"))
         throw new XmlBlasterException(glob, ErrorCode.USER_ADMIN_INVALID, ME,
                      "Only the 'content' can currently be changed on messages," + 
                      " try something like '?content=Hello world'");

      /*
      for (int ii=0; ii<msgUnitArr.length; ii++) {
         // Shallow clone: Setting new content. We keep the original key, but kill the original QoS
         PublishQos publishQos = new PublishQos(glob);
         msgUnitArrRaw[ii] = new MsgUnitRaw(msgUnitArr[ii].getKey(), value.getBytes(), publishQos.toXml());
      }
      */
      String[] retArr = xmlBlaster.publishArr(addressServer, sessionId, msgUnitArrRaw);

      log.info(ME, cmd.getCommand() + " published " + msgUnitArrRaw.length + " messages");
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
