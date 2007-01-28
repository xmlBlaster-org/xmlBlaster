/*------------------------------------------------------------------------------
Name:      SessionName.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.context.ContextNode;

/**
 * Handles unified naming convention of login names and user sessions. 
 *
 * Instances are immutable.
 * @author xmlBlaster@marcelruff.info
 * @see org.xmlBlaster.test.classtest.SessionNameTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.failsafe.html">The client.failsafe requirement</a>
 */
public final class SessionName implements java.io.Serializable
{
   private static final long serialVersionUID = 2684742715895586788L;
   /** Name for logging output */
   private static String ME = "SessionName";
   private transient final Global glob;
   public final static String ROOT_MARKER_TAG = "/"+ContextNode.CLUSTER_MARKER_TAG; //"/node";
   public final static String SUBJECT_MARKER_TAG = ContextNode.SUBJECT_MARKER_TAG; //"client";
   /** The absolute name */
   private String absoluteName;
   private NodeId nodeId;
   private String relativeName;
   private final String subjectId; // == loginName
   private long pubSessionId;
   private boolean nodeIdExplicitlyGiven = false;
   
   // TODO:
   // On release 2.0 we should change the default to the new "client/joe/session/1" notation.
   // Note that C++ clients compiled before V1.4 and java clients before V1.3 can't handle
   // the new notation
   private static boolean useSessionMarker=false;
   
   static {
      // To switch back to old "client/joe/1" markup, default is now "client/joe/session/1"
      useSessionMarker = Global.instance().getProperty().get("xmlBlaster/useSessionMarker", useSessionMarker);
   }

   public static boolean useSessionMarker() {
      return useSessionMarker;
   }

   /**
    * Create and parse a unified name. 
    * <p>
    * @param name Examples:
    * <pre>
    *  /node/heron/client/joe/2
    *  client/joe/2
    *  joe/2
    *  joe
    *  
    *  /node/heron/client/joe/session/2
    *  client/joe/session/2
    *  joe/session/2
    * </pre>
    * @exception IllegalArgumentException if your name can't be parsed
    */
   public SessionName(Global glob, String name) {
      this(glob, (NodeId)null, name);
   }

   /**
    * @param nodeId if not null it has precedence to the nodeId which is probably found in name
    * @param name Examples:
    * <pre>
    *  /node/heron/client/joe/2
    *  client/joe/2
    *  joe/2
    *  joe
    * </pre>
    * @exception IllegalArgumentException if your name can't be parsed
    */
   public SessionName(Global glob, NodeId nodeId, String name) {
      this.glob = (glob == null) ? Global.instance() : glob;

      if (name == null) {
         throw new IllegalArgumentException(ME+": Your given name is null");
      }

      String relative = name;

      // parse absolute part
      if (name.startsWith("/")) {
         String[] arr = ReplaceVariable.toArray(name, "/");
         if (arr.length == 0) {
            throw new IllegalArgumentException(ME+": '" + name + "': The root tag must be '/node'.");
         }
         if (arr.length > 0) {
            if (!"node".equals(arr[0]))
               throw new IllegalArgumentException(ME+": '" + name + "': The root tag must be '/node'.");
         }
         if (arr.length > 1) {
            if (nodeId != null) {
               this.nodeId = nodeId; // given nodeId is strongest
               this.nodeIdExplicitlyGiven = true;
            }
            //else if (glob.isServer()) {
            //   this.nodeId = glob.getNodeId(); // always respect the given name
            //}
            else {
               this.nodeId = new NodeId(arr[1]); // the parsed nodeId
               this.nodeIdExplicitlyGiven = true;
               if ("unknown".equals(this.nodeId.getId())) this.nodeId = null;
            }
         }
         if (arr.length > 2) {
            if (!SUBJECT_MARKER_TAG.equals(arr[2]))
               throw new IllegalArgumentException(ME+": '" + name + "': 'client' tag is missing.");
         }

         relative = "";
         for (int i=3; i<arr.length; i++) {
            relative += arr[i];
            if (i < (arr.length-1))
               relative += "/";
         }
      }

      if (this.nodeId == null) {
         if (nodeId != null) {
            this.nodeId = nodeId; // given nodeId is strongest
            this.nodeIdExplicitlyGiven = true;
         }
         else if (this.glob.isServerSide()) { // if nodeId still not known we set it to the servers nodeId
            this.nodeId = glob.getNodeId();
            this.nodeIdExplicitlyGiven = false;
         }
         //else {
         //   this.nodeId = nodeId;
         //}
      }

      // parse relative part
      if (relative.length() < 1) {
         throw new IllegalArgumentException(ME+": '" + name + "': No relative information found.");
      }

      int ii=0;
      String[] arr = org.xmlBlaster.util.ReplaceVariable.toArray(relative, ContextNode.SEP); //"/"
      if (arr.length > ii) {
         String tmp = arr[ii++];
         if (SUBJECT_MARKER_TAG.equals(tmp)) { // "client"
            if (arr.length > ii) {
               this.subjectId = arr[ii++];
            }
            else {
               throw new IllegalArgumentException(ME+": '" + name + "': No relative information found.");
            }
         }
         else {
            this.subjectId = tmp;
         }
      }
      else {
         throw new IllegalArgumentException(ME+": '" + name + "': No relative information found.");
      }
      if (arr.length > ii) {
         String tmp = arr[ii++];
         if (ContextNode.SESSION_MARKER_TAG.equals(tmp)) {
            if (arr.length > ii) {
               tmp = arr[ii++];
            }
         }
         this.pubSessionId = Long.parseLong(tmp);
      }
   }

   /**
    * Create a new instance based on the given sessionName but with added/changed pubSessionId
    */
   public SessionName(Global glob, SessionName sessionName, long pubSessionId) {
      this(glob, sessionName.getAbsoluteName());
      this.pubSessionId = pubSessionId;
   }

   /**
    * If the nodeId is not known, the relative name is returned
    * @return e.g. "/node/heron/client/joe/2", never null
    */
   public String getAbsoluteName() {
      if (this.absoluteName == null) {
         StringBuffer buf = new StringBuffer(256);
         //buf.append("/node/").append((this.nodeId==null)?"unknown":this.nodeId.getId()).append("/");
         if (this.nodeId!=null) {
            buf.append("/node/").append(this.nodeId.getId()).append("/");
         }
         buf.append(getRelativeName());
         this.absoluteName = buf.toString();
      }
      return this.absoluteName;
   }

   /**
    * @return #getAbsoluteName()
    */
   public String toString() {
      return getAbsoluteName();
   }

   /**
    * @return e.g. "client/joe/session/2" or "client/joe", never null
    */
   public String getRelativeName() {
      return getRelativeName(false);
   }
   
   /**
    * @param forceSessionMarker If false the configured syntax is chosen, if true wie force the /session/ markup
    * @return e.g. "client/joe/session/2" or "client/joe", never null
    */
   public String getRelativeName(boolean forceSessionMarker) {
      if (this.relativeName == null || forceSessionMarker) {
         StringBuffer buf = new StringBuffer(126);
         // For example "client/joe/session/-1"
         buf.append(ContextNode.SUBJECT_MARKER_TAG).append("/").append(subjectId);
         if (isSession()) {
            buf.append("/");
            if (useSessionMarker || forceSessionMarker)
               buf.append(ContextNode.SESSION_MARKER_TAG).append("/");
            buf.append(""+this.pubSessionId);
         }
         this.relativeName = buf.toString();
      }
      return this.relativeName;
   }

   /**
    * Check if the address string given to our constructor had an explicit specified nodeId
    */
   public boolean isNodeIdExplicitlyGiven() {
      return this.nodeIdExplicitlyGiven;
   }

   /**
    * @return e.g. "heron", or null
    */
   public NodeId getNodeId() {
      return this.nodeId;
   }

   /**
    * @return e.g. "heron", or null
    */
   public String getNodeIdStr() {
      return (this.nodeId == null) ? null : this.nodeId.getId();
   }

   /**
    * @return e.g. "joe", never null
    */
   public String getLoginName() {
      return this.subjectId;
   }

   public final boolean isInternalLoginName() {
      // assumes that plugins use "_" and core use "__" (same start as plugins!)
      return this.subjectId.startsWith(Constants.INTERNAL_LOGINNAME_PREFIX_FOR_PLUGINS);
   }

   public final boolean isPluginInternalLoginName() {
      return this.subjectId.startsWith(Constants.INTERNAL_LOGINNAME_PREFIX_FOR_PLUGINS) &&
             !isCoreInternalLoginName();
   }

   public final boolean isCoreInternalLoginName() {
      return this.subjectId.startsWith(Constants.INTERNAL_LOGINNAME_PREFIX_FOR_CORE);
   }

   /**
    * @return The public session identifier e.g. "2" or 0 if in subject context
    */
   public long getPublicSessionId() {
      return this.pubSessionId;
   }

   /**
    * Check if we hold a session or a subject
    */
   public boolean isSession() {
      return this.pubSessionId != 0L;
   }

   //public void mutateToSubject() {
   //   this.pubSessionId = 0L;
   //   this.relativeName = null;
   //}

   /** @return true it publicSessionId is given by xmlBlaster server (if < 0) */
   public boolean isPubSessionIdInternal() {
      return this.pubSessionId < 0L;
   }

   /** @return true it publicSessionId is given by user/client (if > 0) */
   public boolean isPubSessionIdUser() {
      return this.pubSessionId > 0L;
   }

   /**
    * @return true if relative name equals
    */
   public boolean equalsRelative(SessionName sessionName) {
      return getRelativeName().equals(sessionName.getRelativeName());
   }

   public boolean equalsAbsolute(SessionName sessionName) {
      return getAbsoluteName().equals(sessionName.getAbsoluteName());
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of SessionName as a XML ASCII string
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of SessionName as a XML ASCII string
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<SessionName id='").append(getAbsoluteName());
      sb.append("' isSession='").append(isSession()).append("'>");
      sb.append(offset).append(" <nodeId>").append(getNodeIdStr()).append("</nodeId>");
      sb.append(offset).append(" <relativeName>").append(getRelativeName()).append("</relativeName>");
      sb.append(offset).append(" <loginName>").append(getLoginName()).append("</loginName>");
      sb.append(offset).append(" <pubSessionId>").append(getPublicSessionId()).append("</pubSessionId>");
      sb.append(offset).append("</SessionName>");

      return sb.toString();
   }

   /**
    * Method for testing only.<p />
    *
    * Invoke: java -Djava.compiler= org.xmlBlaster.util.SessionName -name client/jack/1
    */
   public static void main(String args[]) {
      Global glob = new Global(args);
      try {
         String name = (args.length>=2) ? args[1] : "jack";
         SessionName sessionName = new SessionName(glob, name);
         System.out.println("AbsoluteName=" + sessionName.getAbsoluteName() + " RelativeName=" + sessionName.getRelativeName());
      }
      catch (IllegalArgumentException e) {
         System.out.println("ERROR: " + e.toString());
      }
   }
}
