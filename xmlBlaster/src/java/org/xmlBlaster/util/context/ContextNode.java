/*------------------------------------------------------------------------------
Name:      ContextNode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.context;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.Constants;
import java.util.ArrayList;

/**
 * This represents one node in the administrative hierarchy, and is a linked
 * list to its parent and its chields. 
 *
 * @author xmlBlaster@marcelruff.info
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.commands.html">The admin.commands requirement</a>
 */
public final class ContextNode
{
   /** Name for logging output */
   private static String ME = "ContextNode";
   public final static String SEP = "/";
   public final static String ROOT_MARKER_TAG = SEP+"xmlBlaster";
   public final static String CLUSTER_MARKER_TAG = "node";
   public final static String SUBJECT_MARKER_TAG = "client";
   public final static String SESSION_MARKER_TAG = "session";
   public final static String TOPIC_MARKER_TAG = "topic";
   private String className; // e.g. "node"
   private String instanceName; // e.g. "heron"
   private ContextNode parent;
   private ArrayList childs;

   //Placeholder for top level node
   public final static ContextNode ROOT_NODE = (ContextNode)null; // new ContextNode(null, "/xmlBlaster", "", (ContextNode)null);

   /**
    * @param className The tag name like 'node' or 'client'
    * @param instanceName The instance like 'heron' or 'joe'
    * @parma parent The parent node or null if root ContextNode ('node/heron' etc)
    */
   public ContextNode(Global glob, String className, String instanceName, ContextNode parent) {
      this.className = className;
      this.instanceName = instanceName;
      this.parent = parent;
   }

   public String getClassName() {
      return this.className;
   }

   public void setInstanceName(String instanceName) {
      this.instanceName = instanceName;
   }

   public String getInstanceName() {
      return this.instanceName;
   }

   /**
    * @return The parent node or null
    */
   public ContextNode getParent() {
      return this.parent;
   }

   public void addChild(ContextNode child) {
      if (this.childs == null) {
         this.childs = new ArrayList();
      }
      this.childs.add(child);
   }

   /**
    * @return All children, never null (but empty array)
    */
   public ContextNode[] getChildren() {
      if (this.childs == null) {
         return new ContextNode[0];
      }
      return (ContextNode[])this.childs.toArray(new ContextNode[this.childs.size()]);
   }

   /**
    * Access the absolute name in standard notation
    * @return e.g. "/xmlBlaster/node/heron/client/joe/session/2", never null
    */
   public String getAbsoluteName() {
      StringBuffer sb = new StringBuffer(256);
      if (this.parent == ROOT_NODE) {
         return sb.append(ROOT_MARKER_TAG).append(SEP).append(this.className).append(SEP).append(this.instanceName).toString();
      }
      return sb.append(this.parent.getAbsoluteName()).append(SEP).append(this.className).append(SEP).append(this.instanceName).toString();
   }

   /**
    * Access the absolute name in standard notation
    * @param schema Currently only "xpath"
    * @return e.g. "xpath:/xmlBlaster/node[@id='heron']/client[@id='joe']/session[@id='2']", never null
    */
   public String getAbsoluteName(String schema) {
      StringBuffer sb = new StringBuffer(256);
      if (this.parent == ROOT_NODE) {
         return sb.append(schema).append(":").append(ROOT_MARKER_TAG).append(SEP).append(this.className).append("[@id='").append(this.instanceName).append("']").toString();
      }
      return sb.append(this.parent.getAbsoluteName(schema)).append(SEP).append(this.className).append("[@id='").append(this.instanceName).append("']").toString();
   }

   /**
    * @return #getAbsoluteName()
    */
   public String toString() {
      return getAbsoluteName();
   }

   /**
    * @return e.g. "client/joe", never null
    */
   public String getRelativeName() {
      StringBuffer sb = new StringBuffer(256);
      return sb.append(this.className).append(SEP).append(this.instanceName).toString();
   }

   /**
    * @param schema Currently only "xpath"
    * @return e.g. "xpath:client[@id='joe']", never null
    */
   public String getRelativeName(String schema) {
      StringBuffer sb = new StringBuffer(256);
      return sb.append(schema).append(":").append(this.className).append("[@id='").append(this.instanceName).append("']").toString();
   }

   /**
    * @return true if relative name equals
    */
   public boolean equalsRelative(ContextNode contextNode) {
      return getRelativeName().equals(contextNode.getRelativeName());
   }

   public boolean equalsAbsolute(ContextNode contextNode) {
      return getAbsoluteName().equals(contextNode.getAbsoluteName());
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML dump of ContextNode
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML dump of ContextNode
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<ContextNode class='").append(this.className).append("' instance='").append(this.instanceName).append("'/>");
      return sb.toString();
   }

   /**
    * Method for testing only.<p />
    *
    * Invoke: java -Djava.compiler= org.xmlBlaster.util.context.ContextNode
    */
   public static void main(String args[]) {
      Global glob = new Global(args);
      try {
         ContextNode heron = new ContextNode(glob, ContextNode.CLUSTER_MARKER_TAG, "heron", null);
         System.out.println("AbsoluteName=" + heron.getAbsoluteName() + " RelativeName=" + heron.getRelativeName());
         ContextNode jack = new ContextNode(glob, ContextNode.SUBJECT_MARKER_TAG, "jack", heron);
         System.out.println("AbsoluteName=" + jack.getAbsoluteName() + " RelativeName=" + jack.getRelativeName());
         ContextNode ses2 = new ContextNode(glob, ContextNode.SESSION_MARKER_TAG, "2", jack);
         System.out.println("AbsoluteName=" + ses2.getAbsoluteName() + " RelativeName=" + ses2.getRelativeName());
         ContextNode hello = new ContextNode(glob, ContextNode.TOPIC_MARKER_TAG, "hello", heron);
         System.out.println("AbsoluteName=" + hello.getAbsoluteName() + " RelativeName=" + hello.getRelativeName());
      }
      catch (IllegalArgumentException e) {
         System.out.println("ERROR: " + e.toString());
      }
   }
}
