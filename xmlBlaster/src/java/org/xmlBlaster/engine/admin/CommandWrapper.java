/*------------------------------------------------------------------------------
Name:      CommandWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Contains the parsed command
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.QueryQosSaxFactory;
import org.xmlBlaster.engine.Global;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Holds the command partially preparsed, this is an immutable object. 
 * <p />
 * Examples what we need to parse:
 * <pre>
 *   /node/heron/?freeMem
 *   /node/heron/sysprop/?java.vm.version
 *   /node/heron/client/joe/ses17/?queue/callback/maxEntries
 * </pre>
 * <p />
 * See the <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.commands.html">command requirement</a>
 * for a detailed description.
 * @author xmlBlaster@marcelruff.info
 * @see org.xmlBlaster.test.classtest.CommandWrapperTest
 * @since 0.79f
 */
public final class CommandWrapper
{
   private final String ME;
   private final Global glob;
   private final LogChannel log;

   private final static String PROP_SEPARATOR = "&";
   private final static String XMLBLASTER_PREFIX = "xmlBlaster.";

   /** The original command (modified to be absolute) */
   private final String cmd;
   private final String myStrippedClusterNodeId;

   /** The first level -> "node" */
   String root = null;
   /** The second level -> "heron" */
   String clusterNodeId = null;
   /** The third level -> "?freeMem" or "sysprop" or "client" or "topic" */
   String third = null;
   /** The fourth level -> "client/joe" */
   String fourth = null;
   /** The fifth level -> "client/joe/ses17" or "client/joe/?maxSessions" */
   String fifth = null;
   /** The sixth level -> "client/joe/ses17/?queue/callback/maxEntries" */
   String sixth = null;

   /** The rest of the command -> "?java.vm.version"*/
   String tail = null;

   /** "sysprop/?call[auth]=true" this is "call[auth]" */
   String key = null;
   /** "sysprop/?call[auth]=true" this is "true" */
   String value = null;
   
   /** the qos properties */
   QueryQosData qosData;

   /** the key (the admin command itself: it could also be seen as the destination of the admin command) */
   QueryKeyData keyData;

   /** the properties on the right end */
   Map props = new HashMap();

   /**
    * this constructor is currently used for the get 
    * @param glob
    * @param keyData the oid must be adjusted inside if the qosData is null.
    * @param qosData it is null in case the request comes from a telnet or a snmp gateway
    * @throws XmlBlasterException
    */
   public CommandWrapper(Global glob, QueryKeyData keyData, QueryQosData qosData) throws XmlBlasterException {
      this(glob, CommandWrapper.stripCommand(glob, keyData.getOid()), qosData);
      // this is for the smnp and telnet 
      this.keyData = keyData;
      if (this.qosData == null) {
         fillKeyAndQos();
      }
   }

   /**
    * TODO This constructor is still used for the 'set' command but should be replaced
    * @param glob
    * @param cmd
    * @param qosData
    * @throws XmlBlasterException
    * @deprecated the constructor taking a QueryKeyData should be used instead.
    */
   public CommandWrapper(Global glob, String cmd, QueryQosData qosData) throws XmlBlasterException {
      this.glob = glob;
      this.log = this.glob.getLog("admin");
      this.ME = "CommandWrapper-" + this.glob.getId();
      this.myStrippedClusterNodeId = getStrippedClusterNodeId();
      if (cmd == null)
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "Your command is null, aborted request");
      if (!cmd.startsWith("/"))
         this.cmd = "/node/" + myStrippedClusterNodeId + "/" + cmd;
      else
         this.cmd = cmd;
      this.qosData = qosData;
      parse();
   }

   private void parse() throws XmlBlasterException {
      String prefix = cmd;
      int questionIndex = cmd.indexOf("?");
      int equalsIndex = cmd.indexOf("=");
      if (questionIndex >= 0 && equalsIndex >= 0 && questionIndex < equalsIndex)  {
         parseKeyValue();
         prefix = cmd.substring(0,equalsIndex);
         if (log.TRACE) log.trace(ME, "prefix=" + prefix + " key=" + key + " value=" + value);
      }

      StringTokenizer st = new StringTokenizer(prefix, "/");
      int ii=1;
      while (st.hasMoreTokens()) {
         String token = (String)st.nextToken();
         if (log.TRACE) log.trace(ME, "Parsing '" + prefix + "' ii=" + ii + " token=" + token);
         if (ii==1)
            root = token;
         else if (ii == 2)
            clusterNodeId = token;
         else if (ii == 3)
            third = token;
         else if (ii == 4)
            fourth = token;
         else if (ii == 5)
            fifth = token;
         else if (ii == 6)
            sixth = token;
         else {
            break;
         }
         ii++;
      }
      if (root == null || clusterNodeId == null || third == null) {
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".parse", "Your command is invalid, missing levels: '" + cmd + "'");
      }
      if (!"node".equals(root)) {
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".parse", "Your root node is invalid, only <node> is supported, sorry '" + cmd + "' rejected");
      }
      if (!glob.getId().equals(clusterNodeId) && !myStrippedClusterNodeId.equals(clusterNodeId)) {
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".parse", "Query of foreign cluster node '" + clusterNodeId + "' is not implemented, sorry '" + cmd + "' rejected");
      }

      int offset = root.length() + clusterNodeId.length() + third.length() + 4;
      if (cmd.length() > offset)
         tail = cmd.substring(offset);
   }

   /**
    * Returns the cluster node id with removed "/" chars (if any where there). 
    * @return "http:myserver:3412" instead of "http://myserver:3412", names like "heron.mycomp.com" are untouched
    */
   public final String getStrippedClusterNodeId() {
      return org.jutils.text.StringHelper.replaceAll(glob.getId(), "/", "");
   }

   /**
    * /node/heron/client/joe/ses17/?callback/queue/maxEntries
    * @return "node"
    */
   public final String getRoot() {
      return root;
   }

   /**
    * /node/heron/client/joe/ses17/?queue/callback/maxEntries
    * @return e.g. "heron"
    */
   public final String getClusterNodeId() {
      return clusterNodeId;
   }

   /**
    * /node/heron/client/joe/ses17/?queue/callback/maxEntries
    * @return The third level of a command like "client", "sysprop", "topic", "?uptime"
    */
   public final String getThirdLevel() {
      return third;
   }

   /**
    * <pre>
    * /node/heron/client/joe/ses17/?queue/callback/maxEntries
    * /node/heron/topic/?hello
    * </pre>
    * @return "joe" or "?hello" in the above example
    */
   public final String getUserNameLevel() {
      return fourth;
   }

   /**
    * /node/heron/client/joe/ses17/?queue/callback/maxEntries
    * @return "ses17" in the above example
    */
   public final String getSessionIdLevel() {
      return getFifthLevel();
   }

   /**
    * /node/heron/client/joe/ses17/?queue/callback/maxEntries
    * @return "ses17" in the above example
    */
   public final String getFifthLevel() {
      return fifth;
   }

   /**
    * /node/heron/client/joe/ses17/?callback/queue/maxEntries
    * @return "?queue/callback/maxEntries" in the above example
    */
   public final String getSessionAttrLevel() {
      return sixth;
   }

   /**
    * @return The fourth level and deeper, e.g. "?java.vm.version", "joe/?sessionList" or null
    */
   public final String getTail() {
      return tail;
   }

   /**
    * @return If set() is invoked the value behind the "="
    * "sysprop/?trace[core]=true" would return 'true'
    * @exception XmlBlasterException if no value found
    */
   public final String getValue() throws XmlBlasterException {
      if (key == null && value == null)
         parseKeyValue();
      return value;
   }

   /**
    * @return If set() is invoked the value before the "="
    * "sysprop/?trace[core]=true" would return 'trace[core]'
    * @exception XmlBlasterException if no value found
    */
   public final String getKey() throws XmlBlasterException {
      if (key == null && value == null)
         parseKeyValue();
      return key;
   }

   private void parseKeyValue() throws XmlBlasterException {
      int qIndex = cmd.indexOf("?");
      if (qIndex < 1 || cmd.length() <= (qIndex+1)) {
         log.warn(ME, "parseKeyValue(): Invalid command '" + cmd + "', can't find '?'");
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".parseKeyValue", "Invalid command '" + cmd + "', can't find '?'");
      }
      String propString = cmd.substring(qIndex+1);
      StringTokenizer tokenizer = new StringTokenizer(propString.trim(), PROP_SEPARATOR);

      boolean keyAlreadyAssigned = false;


      
      while (tokenizer.hasMoreTokens()) {
         String pair = tokenizer.nextToken().trim();
         if (pair.length() < 1) continue;
         int equalsIndex = pair.indexOf("=");
         /*
         if (equalsIndex < 1 || pair.length() <= (equalsIndex+1)) {
            log.warn(ME, "parseKeyValue(): Invalid command '" + cmd + "', can't find assignment '='");
            //Thread.currentThread().dumpStack();
            throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".parseKeyValue", "Invalid command '" + cmd + "', can't find assignment '='");
         }
      
         String tmpKey = pair.substring(0,equalsIndex).trim();
         */
         String tmpKey = null;
         String tmpValue = null;
         if (equalsIndex < 1 || pair.length() <= (equalsIndex+1)) {
            tmpKey = pair.trim();
            tmpValue = null;
         }
         else {
            tmpKey = pair.substring(0,equalsIndex).trim();
            tmpValue = pair.substring(equalsIndex+1);
         } 

         if (tmpKey.indexOf(XMLBLASTER_PREFIX) < 0) {
            if (keyAlreadyAssigned)
               throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, "only one key which does not start with '" + XMLBLASTER_PREFIX + "' can be assigned");
            this.key = tmpKey;
            this.value = tmpValue;
         }
         else this.props.put(tmpKey, tmpValue);
      }
   }

   /**
    * @return The original command, with added absolute path if the original was relative, e.g.
    *         /node/heron/?runlevel
    */
   public final String getCommand() {
      return cmd;
   }

   /**
    * @return The original command, with added absolute path if the original was relative
    *         and stripped "=bla" at the end.
    */
   public final String getCommandStripAssign() throws XmlBlasterException {
      int equalsIndex = cmd.lastIndexOf("=");
      if (equalsIndex < 1 || cmd.length() <= (equalsIndex+1)) {
         log.warn(ME, "getCommandStripAssign(): Invalid command '" + cmd + "', can't find assignment '='");
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".getCommandStripAssign", "Invalid command '" + cmd + "', can't find assignment '='");
      }
      return cmd.substring(0,equalsIndex).trim();
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

      sb.append(offset).append("<commandWrapper>");
      sb.append(offset).append("  <cmd>").append(cmd).append("</cmd>");
      sb.append(offset).append("  <root>").append(root).append("</root>");
      sb.append(offset).append("  <nodeId>").append(clusterNodeId).append("</nodeId");
      sb.append(offset).append("  <third>").append(third).append("</third");
      sb.append(offset).append("  <tail>").append(third).append("</tail");
      sb.append(offset).append("</commandWrapper>");

      return sb.toString();
   }
   
   public QueryQosData getQueryQosData() {
      return this.qosData;
   }

   public QueryKeyData getQueryKeyData() {
      return this.keyData;
   }

   /**
    * Strips a given command (the oid of a queryKey before any any modification) by 
    * removing the starting '__cmd:' subtring. So for example the input string
    * '__cmd:/node/heron/?numClients' will be stripped to '/node/heron/?numClients'.
    * @param glob
    * @param command the input string (the original oid) to be stripped.
    * @return
    * @throws XmlBlasterException
    */
   public static String stripCommand(Global glob, String command) throws XmlBlasterException {
      if (command == null) {
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, "CommandWrapper.stripCommand", "Ignoring your empty command.");
      }
      command = command.trim();
      if (!command.startsWith("__cmd:") || command.length() < ("__cmd:".length() + 1)) {
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, "CommandWrapper.stripCommand", "Ignoring your empty command '" + command + "'.");
      }

      int dotIndex = command.indexOf(":");
      return command.substring(dotIndex+1).trim();  // "/node/heron/?numClients"
   }
   
   /**
    * This has to be invoked after the command has been parsed
    * @param value the non-null string containing the qos specification.
    *        for example if the entire command is
    * '__cmd:/node/heron/?numClients&xmlBlaster.qos=<qos>......</qos>'  
    * @param keyData must be non null
    * @param qosData
    */
   private void fillKeyAndQos() throws XmlBlasterException {
      if (this.qosData != null) return;
      
      String qosLitteral = (String)this.props.get("xmlBlaster.qos");
      if (qosLitteral == null) 
         this.qosData = new QueryQosData(this.glob, MethodName.GET);
      else {
         QueryQosSaxFactory factory = new QueryQosSaxFactory(this.glob);
         this.qosData = factory.readObject(qosLitteral);
      }
      int pos = this.sixth.indexOf('&');
      if (pos > -1) this.sixth = this.sixth.substring(0, pos);
      
      this.keyData.setOid("__cmd:/" + this.root + "/" + this.clusterNodeId + "/" + this.sixth);
   }

}

