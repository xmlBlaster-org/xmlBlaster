/*------------------------------------------------------------------------------
Name:      MomClientGateway.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Access with messages to administration tasks
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin.extern;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.key.KeyData;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.engine.qos.PublishQosServer;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.I_ExternGateway;
import org.xmlBlaster.engine.admin.SetReturn;
import org.xmlBlaster.authentication.SessionInfo;

/**
 * The gateway allows to do administration with xmlBlaster messages. 
 * <p />
 * XmlBlaster messages having an oid that starts with "__cmd:" are
 * handled as administration messages.<br />
 * Example:<br />
 * <pre>
 *   &lt;key oid="__cmd:/node/heron/sysprop/?trace[core]"/>
 * </pre>
 * @author xmlBlaster@marcelruff.info
 * @since 0.79f
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.html">admin requirement</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.commands.html">admin.commands requirement</a>
 */
public final class MomClientGateway implements I_ExternGateway
{
   private String ME;
   private Global glob;
   private LogChannel log;
   private CommandManager commandManager;
   private static int instanceCounter = 0;

   /**
    * Creates the remote console server. 
    */
   public boolean initialize(Global glob, CommandManager commandManager) throws XmlBlasterException {
      this.glob = glob;
      this.log = this.glob.getLog("admin");

      boolean useMessages = glob.getProperty().get("admin.messages", true);
      useMessages = glob.getProperty().get("admin.messages[" + glob.getId() + "]", useMessages);
      if (!useMessages) return false;

      instanceCounter++;
      this.ME = "MomClientGateway" + instanceCounter + this.glob.getLogPrefixDashed();
      this.commandManager = commandManager;
      return true;
   }

   /**
    * Called by RequestBroker on get() of command messages. 
    * @param sessionInfo The client
    * @param command The key oid, for example "__cmd:/node/heron/?numClients"
    */
   public MsgUnit[] getCommand(SessionInfo sessionInfo, QueryKeyData keyData, QueryQosData qosData) throws XmlBlasterException {
      //String cmdType = "get";

      if (log.CALL) log.call(ME, "Invoking getCommand(" + keyData.getOid() + ")");
      /*
      CommandWrapper cw = new CommandWrapper(glob, query);

      String topCommand = cw.getThirdLevel();
      log.info(ME, "Processing command " + cw.toXml());

      if (topCommand == null)
         throw new XmlBlasterException(ME, "Ignoring your command '" + command + "', can't understand it.");

      if (topCommand.equalsIgnoreCase("time")) {
         MsgUnitRaw[] msgs = new MsgUnitRaw[1];
         msgs[0] = new MsgUnitRaw(glob, "<key oid='" + command + "'", ""+new java.util.Date(), "<qos/>");
         return msgs;
      }
      */

      MsgUnit[] msgs = commandManager.get(sessionInfo.getAddressServer(), sessionInfo.getSecretSessionId(), keyData, qosData);
      /*
      for (int ii=0; ii<msgs.length; ii++) {
         MsgUnitRaw msg = msgs[ii];
         if (msg.getQos().startsWith("text/plain")) { // A virtual msgUnit from a key/value property
            PublishKey publishKey = new PublishKey(glob, "__cmd:" + msg.getKey(), "text/plain");
            PublishQos publishQos = new PublishQos(glob);
            // A shallow copy, null -> use the given msg.getContent()
            msgs[ii] = new MsgUnit(msg, publishKey.getData(), null, publishQos.getData());
         }
      }
      */
      return msgs;
   }

   /**
    * Called by RequestBroker on publish() of command messages. 
    * @param sessionInfo The client
    * @param xmlKey The key oid, for example "__cmd:/node/heron/?numClients=5"
    */
   public String setCommand(SessionInfo sessionInfo, KeyData xmlKey, MsgUnit msgUnit,
                    PublishQosServer publishQos, boolean isClusterUpdate) throws XmlBlasterException {
      String command = xmlKey.getOid();
      if (command == null) {
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".setCommand", "Ignoring your empty command.");
      }
      command = command.trim();
      if (log.CALL) log.call(ME, "Invoking setCommand(" + command + ")");
      if (!command.startsWith("__cmd:") || command.length() < ("__cmd:".length() + 1)) {
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".setCommand", "Ignoring your empty command '" + command + "'.");
      }

      int dotIndex = command.indexOf(":");
      String query = command.substring(dotIndex+1).trim();  // "/node/heron/?numClients=5"

      if (query.indexOf("=") == -1) {
         query = query + "=" + msgUnit.getContentStr();     // "/node/heron/?numClients=99"
      }

      SetReturn ret = commandManager.set(sessionInfo.getAddressServer(), sessionInfo.getSecretSessionId(), query);

      if (ret == null)
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".setCommand", "Your command '" + ret.commandWrapper.getCommand() + "' failed, reason is unknown");

      String info = ret.commandWrapper.getCommandStripAssign() + "=" + ret.returnString;
      StringBuffer buf = new StringBuffer(160);
      buf.append("<qos><state id='").append(Constants.STATE_OK).append("'");
      if (info.indexOf("'") == -1)
         buf.append(" info='").append(info).append("'");
      buf.append("/><key oid='").append(xmlKey.getOid()).append("'/></qos>");
      return buf.toString();
   }

   public String getName() {
      return "MomClientGateway";
   }

   public void shutdown() {
      if (log.TRACE) log.trace(ME, "Shutdown.");
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

      sb.append(offset).append("<momClientGateway>");
      sb.append(offset).append("</momClientGateway>");

      return sb.toString();
   }
}

