/*------------------------------------------------------------------------------
Name:      CommandWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Contains the parsed command
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.authentication.SessionInfo;

import java.util.StringTokenizer;

/**
 * Holds the command partially preparsed, this is an immutable object. 
 * <p />
 * Examples what we need to parse:
 * <pre>
 *   /node/heron/?freeMem
 *   /node/heron/sysprop/?java.vm.version
 *   /node/heron/client/joe/ses17/?cb.queue.maxMsg
 * </pre>
 * <p />
 * See the <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.commands.html">command requirement</a>
 * for a detailed description.
 * @author ruff@swand.lake.de
 * @see classtest.CommandWrapperTest
 * @since 0.79f
 */
public final class CommandWrapper
{
   private final String ME;
   private final Global glob;
   private final LogChannel log;

   /** The original command (modified to be absolute) */
   private final String cmd;
   private final String myStrippedClusterNodeId;

   /** The first level -> "node" */
   String root = null;
   /** The second level -> "heron" */
   String clusterNodeId = null;
   /** The third level -> "?freeMem" or "sysprop" or "client" or "msg" */
   String third = null;
   /** The fourth level -> "client/joe" */
   String fourth = null;
   /** The fifth level -> "client/joe/ses17" or "client/joe/?maxSessions" */
   String fifth = null;
   /** The sixth level -> "client/joe/ses17/?cb.queue.maxMsg" */
   String sixth = null;

   /** The rest of the command -> "?java.vm.version"*/
   String tail = null;

   public CommandWrapper(Global glob, String cmd) throws XmlBlasterException {
      this.glob = glob;
      this.log = this.glob.getLog("admin");
      this.ME = "CommandWrapper-" + this.glob.getId();
      this.myStrippedClusterNodeId = getStrippedClusterNodeId();
      if (cmd == null)
         throw new XmlBlasterException(ME, "Your command is null, aborted request");
      if (!cmd.startsWith("/"))
         this.cmd = "/node/" + myStrippedClusterNodeId + "/" + cmd;
      else
         this.cmd = cmd;
      parse();
   }

   private void parse() throws XmlBlasterException {

      StringTokenizer st = new StringTokenizer(cmd, "/");
      int ii=1;
      while (st.hasMoreTokens()) {
         String token = (String)st.nextToken();
         if (log.TRACE) log.trace(ME, "Parsing '" + cmd + "' ii=" + ii + " token=" + token);
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
         throw new XmlBlasterException(ME, "Your command is invalid, missing levels: '" + cmd + "'");
      }
      if (!"node".equals(root)) {
         throw new XmlBlasterException(ME, "Your root node is invalid, only <node> is supported, sorry '" + cmd + "' rejected");
      }
      if (!glob.getId().equals(clusterNodeId) && !myStrippedClusterNodeId.equals(clusterNodeId)) {
         throw new XmlBlasterException(ME, "Query of foreign cluster node '" + clusterNodeId + "' is not implemented, sorry '" + cmd + "' rejected");
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
    * /node/heron/client/joe/ses17/?cb.queue.maxMsg
    * @return "node"
    */
   public final String getRoot() {
      return root;
   }

   /**
    * /node/heron/client/joe/ses17/?cb.queue.maxMsg
    * @return e.g. "heron"
    */
   public final String getClusterNodeId() {
      return clusterNodeId;
   }

   /**
    * /node/heron/client/joe/ses17/?cb.queue.maxMsg
    * @return The third level of a command like "client", "sysprop", "msg", "?uptime"
    */
   public final String getThirdLevel() {
      return third;
   }

   /**
    * /node/heron/client/joe/ses17/?cb.queue.maxMsg
    * @return "joe" in the above example
    */
   public final String getUserNameLevel() {
      return fourth;
   }

   /**
    * /node/heron/client/joe/ses17/?cb.queue.maxMsg
    * @return "ses17" in the above example
    */
   public final String getSessionIdLevel() {
      return fifth;
   }

   /**
    * /node/heron/client/joe/ses17/?cb.queue.maxMsg
    * @return "?cb.queue.maxMsg" in the above example
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
    * @return The original command, with added absolute path if the original was relative
    */
   public final String getCommand() {
      return cmd;
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
}

