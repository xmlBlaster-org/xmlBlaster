/*------------------------------------------------------------------------------
Name:      SessionName.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.Global;
import org.jutils.text.StringHelper;

/**
 * Handles unified naming convention of login names and user sessions. 
 *
 * @author ruff@swand.lake.de
 * @see org.xmlBlaster.test.classtest.SessionNameTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.failsave.html">The client.failsave requirement</a>
 */
public final class SessionName
{
   /** Name for logging output */
   private static String ME = "SessionName";
   private final Global glob;
   public final static String ROOT_MARKER_TAG = "/node";
   public final static String SUBJECT_MARKER_TAG = "client";
   /** The absolute name */
   private String absoluteName;
   private String nodeId;
   private String relativeName;
   private final String subjectId;
   private String pubSessionId;

   /**
    * Create and parse a unified name. 
    * <p>
    * @param name Examples:
    * <pre>
    *  /node/heron/client/joe/2
    *  client/joe/2
    *  joe/2
    *  joe
    * </pre>
    * @exception IllegalArgumentException if your name can't be parsed
    */
   public SessionName(Global glob, String name) {
      this(glob, (String)null, name);
   }

   public SessionName(Global glob, String nodeId, String name) {
      this.glob = (glob == null) ? Global.instance() : glob;

      if (name == null) {
         throw new IllegalArgumentException(ME+": Your given name is null");
      }

      String relative = name;

      // parse absolute part
      if (name.startsWith("/")) {
         String[] arr = StringHelper.toArray(name, "/");
         if (arr.length == 0) {
            throw new IllegalArgumentException(ME+": '" + name + "': The root tag must be '/node'.");
         }
         if (arr.length > 0) {
            if (!"node".equals(arr[0]))
               throw new IllegalArgumentException(ME+": '" + name + "': The root tag must be '/node'.");
         }
         if (arr.length > 1) {
            this.nodeId = arr[1];
            if ("unknown".equals(this.nodeId)) this.nodeId = null;
         }
         if (arr.length > 2) {
            if (!"client".equals(arr[2]))
               throw new IllegalArgumentException(ME+": '" + name + "': 'client' tag is missing.");
         }

         relative = "";
         for (int i=3; i<arr.length; i++) {
            relative += arr[i];
            if (i < (arr.length-1))
               relative += "/";
         }
      }

      if (nodeId != null) {
         if (nodeId.startsWith("/node/"))
            this.nodeId = nodeId.substring("/node/".length());
         else {
            this.nodeId = nodeId;
         }
         if (this.nodeId.indexOf("/") > -1) {
            throw new IllegalArgumentException(ME+": '" + name + "': The nodeId '" + this.nodeId + "' must not contain a '/'");
         }
      }

      // parse relative part
      if (relative.length() < 1) {
         throw new IllegalArgumentException(ME+": '" + name + "': No relative information found.");
      }

      int ii=0;
      String[] arr = StringHelper.toArray(relative, "/");
      if (arr.length > ii) {
         String tmp = arr[ii++];
         if ("client".equals(tmp)) {
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
      if (arr.length > ii) this.pubSessionId = arr[ii++];
   }

   /**
    * Create a new instance based on the given sessionName but with added/changed pubSessionId
    */
   public SessionName(Global glob, SessionName sessionName, String pubSessionId) {
      this(glob, sessionName.getAbsoluteName());
      this.pubSessionId = pubSessionId;
   }

   /**
    * @return e.g. "/node/heron/client/joe/2", never null
    */
   public String getAbsoluteName() {
      if (this.absoluteName == null) {
         StringBuffer buf = new StringBuffer(256);
         buf.append("/node/").append((this.nodeId==null)?"unknown":this.nodeId).append("/");
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
    * @return e.g. "client/joe/2" or "client/joe", never null
    */
   public String getRelativeName() {
      if (this.relativeName == null) {
         StringBuffer buf = new StringBuffer(126);
         buf.append("client/").append(subjectId);
         if (pubSessionId != null) buf.append("/").append(pubSessionId);
         this.relativeName = buf.toString();
      }
      return this.relativeName;
   }

   /**
    * @return e.g. "heron", or null
    */
   public String getNodeId() {
      return this.nodeId;
   }

   /**
    * @return e.g. "joe", never null
    */
   public String getLoginName() {
      return this.subjectId;
   }

   /**
    * @return The public session identifier e.g. "2" or null if in subject context
    */
   public String getPubSessionId() {
      return this.pubSessionId;
   }

   /**
    * Check if we hold a session or a subject
    */
   public boolean isSession() {
      return this.pubSessionId != null;
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
    * Method for testing only.<p />
    *
    * Invoke: java -Djava.compiler= org.xmlBlaster.util.SessionName
    */
   public static void main(String args[]) {
      Global glob = new Global(args);
      try {
         SessionName sessionName = new SessionName(glob, "jack");
         System.out.println("AbsoluteName=" + sessionName.getAbsoluteName() + " RelativeName=" + sessionName.getRelativeName());
      }
      catch (IllegalArgumentException e) {
         System.out.println("ERROR: " + e.toString());
      }
   }
}
