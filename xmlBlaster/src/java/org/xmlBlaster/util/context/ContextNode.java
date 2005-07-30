/*------------------------------------------------------------------------------
Name:      ContextNode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.context;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;
import java.util.ArrayList;
import javax.management.ObjectName;

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
   /** Use to mark a cluster node name */
   public final static String CLUSTER_MARKER_TAG = "node";
   /** Use to mark a client */
   public final static String SUBJECT_MARKER_TAG = "client";
   /** Use to mark a login session */
   public final static String SESSION_MARKER_TAG = "session";
   /** Use for client side XmlBlasterAccess */
   public final static String CONNECTION_MARKER_TAG = "connection";
   /** Use to mark a queue */
   public final static String QUEUE_MARKER_TAG = "queue";
   /** Use to mark a topic */
   public final static String TOPIC_MARKER_TAG = "topic";
   /** Use to mark a system and configuration properties and command line arguments */
   public final static String TOPIC_SYSPROP_TAG = "sysprop";
   /** Use to mark logging settings */
   public final static String TOPIC_LOGGING_TAG = "logging";

   /** For XPath conforming query */
   public final static String SCHEMA_XPATH = "xpath";
   /** For JMX conforming ObjectName string */
   public final static String SCHEMA_JMX = "jmx";
   /** For URL conforming string */
   public final static String SCHEMA_URL = "url";

   private String className; // e.g. "node"
   private String instanceName; // e.g. "heron"
   private ContextNode parent;
   private ArrayList childs;

   //Placeholder for top level node
   public final static ContextNode ROOT_NODE = (ContextNode)null; // new ContextNode(null, "/xmlBlaster", "", (ContextNode)null);

   /**
    * @param className The tag name like 'node' (ContextNode.CLUSTER_MARKER_TAG) or 'client' (ContextNode.SUBJECT_MARKER_TAG)
    * @param instanceName The instance like 'heron' or 'joe', can be null
    * @param parent The parent node or null if root ContextNode ('node/heron' etc)
    */
   public ContextNode(Global glob, String className, String instanceName, ContextNode parent) {
      if (className == null) {
         throw new IllegalArgumentException("ContextNode: Missing className argument");
      }
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

   /**
    * @return Can be null
    */
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
         sb.append(ROOT_MARKER_TAG).append(SEP).append(this.className);
         if (this.instanceName != null) {
            sb.append(SEP).append(this.instanceName);
         }
         return sb.toString();
      }
      sb.append(this.parent.getAbsoluteName()).append(SEP).append(this.className);
      if (this.instanceName != null) {
         sb.append(SEP).append(this.instanceName);
      }
      return sb.toString();
   }

   /**
    * Access the absolute name in standard notation
    * @param schema Currently only "xpath"
    * @return e.g. "xpath:/xmlBlaster/node[@id='heron']/client[@id='joe']/session[@id='2']", never null
    */
   public String getAbsoluteName(String schema) {
      StringBuffer sb = new StringBuffer(256);
      if (SCHEMA_JMX.equalsIgnoreCase(schema)) {
         // "org.xmlBlaster:nodeClass=node,node=heron,clientClass=client,client=joe,queueClass=queue,queue=subject665,entryClass=entry,entry=1002"
         // like this jconsole creates a nice tree (see JmxWrapper.java for a discussion)
         if (this.parent == ROOT_NODE) {
            return sb.append("org.xmlBlaster:").append("nodeClass=node,node=").append(ObjectName.quote(this.instanceName)).toString();
         }
         sb.append(this.parent.getAbsoluteName(schema));
         sb.append(",").append(this.className).append("Class=").append(this.className);
         // JMX ',' make problems with or without quotes
         //     ':' is only OK if quoted
         if (this.instanceName != null) {
            sb.append(",").append(this.className).append("=").append(ObjectName.quote(this.instanceName));
         }
         return sb.toString();
      }
      else if (SCHEMA_XPATH.equalsIgnoreCase(schema)) {
         if (this.parent == ROOT_NODE) {
            sb.append(schema).append(":").append(ROOT_MARKER_TAG).append(SEP).append(this.className);
            if (this.instanceName != null) {
               sb.append("[@id='").append(this.instanceName).append("']");
            }
            return sb.toString();
         }
         sb.append(this.parent.getAbsoluteName(schema)).append(SEP).append(this.className);
         if (this.instanceName != null) {
            sb.append("[@id='").append(this.instanceName).append("']");
         }
         return sb.toString();
      }
      else /* if (SCHEMA_URL.equalsIgnoreCase(schema)) */ {
         return getAbsoluteName();
      }
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
      sb.append(this.className);
      if (this.instanceName != null) {
         sb.append(SEP).append(this.instanceName);
      }
      return sb.toString();
   }

   /**
    * @param schema Currently only "xpath"
    * @return e.g. "xpath:client[@id='joe']", never null
    */
   public String getRelativeName(String schema) {
      StringBuffer sb = new StringBuffer(256);
      if (SCHEMA_JMX.equalsIgnoreCase(schema)) {
         // "org.xmlBlaster:clientClass=client,client=joe"
         sb.append("org.xmlBlaster:");
         sb.append(this.className).append("Class=").append(this.className);
         if (this.instanceName != null) {
            sb.append(",").append(this.className).append("=").append(ObjectName.quote(this.instanceName));
         }
         return sb.toString();
      }
      else if (SCHEMA_XPATH.equalsIgnoreCase(schema)) {
         sb.append(schema).append(":").append(this.className);
         if (this.instanceName != null) {
            sb.append("[@id='").append(this.instanceName).append("']");
         }
         return sb.toString();
      }
      else /* if (SCHEMA_URL.equalsIgnoreCase(schema)) */ {
         return getRelativeName();
      }
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
    * Parse the given string. 
    * @param url For example 
    *             "/xmlBlaster/node/heron/client/joe/session/2"
    * @return The lowest ContextNode instance, you can navigate upwards with getParent()
    *         or null.
    */
   public static ContextNode valueOf(Global glob, String url) {
      if (url == null)
         return null;
      String lower = url.toLowerCase();
      if (lower.startsWith("org.xmlblaster") || lower.startsWith("xpath")) {
         throw new IllegalArgumentException("ContextNode.valueOf(): Unkown schema in '" + url + "'");
      }
      if (url.startsWith("/xmlBlaster/node/") || url.startsWith("/node/")) {
         String[] toks = org.jutils.text.StringHelper.toArray(url, "/");
         ContextNode node = ROOT_NODE;
         for (int i=0; i<toks.length; i++) {
            String tok = toks[i];
            if (i == 0 && "xmlBlaster".equals(tok)) {
               node = ROOT_NODE;
               continue;
            }
            if (i == toks.length-1) {
               glob.getLog("core").warn(ME, "Unexpected syntax in '" + url + "', missing value for class");
               break;
            }
            node = new ContextNode(glob, tok, toks[i+1], node);
            i++;
         }
         return node;
      }
      throw new IllegalArgumentException("ContextNode.valueOf(): not implemented: '" + url + "'");
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
         System.out.println("AbsoluteName=" + ses2.getAbsoluteName("xpath") + " RelativeName=" + ses2.getRelativeName("xpath"));
         System.out.println("AbsoluteName=" + ses2.getAbsoluteName("jmx") + " RelativeName=" + ses2.getRelativeName("jmx"));

         {
            System.out.println("\nTopic:");
            ContextNode hello = new ContextNode(glob, ContextNode.TOPIC_MARKER_TAG, "hello", heron);
            System.out.println("AbsoluteName=" + hello.getAbsoluteName() + " RelativeName=" + hello.getRelativeName());
            System.out.println("AbsoluteName=" + hello.getAbsoluteName("xpath") + " RelativeName=" + hello.getRelativeName("xpath"));
            System.out.println("AbsoluteName=" + hello.getAbsoluteName("jmx") + " RelativeName=" + hello.getRelativeName("jmx"));
         }
         {
            System.out.println("\nWith NULL:");
            ContextNode hello = new ContextNode(glob, ContextNode.TOPIC_MARKER_TAG, null, heron);
            System.out.println("AbsoluteName=" + hello.getAbsoluteName() + " RelativeName=" + hello.getRelativeName());
            System.out.println("AbsoluteName=" + hello.getAbsoluteName("xpath") + " RelativeName=" + hello.getRelativeName("xpath"));
            System.out.println("AbsoluteName=" + hello.getAbsoluteName("jmx") + " RelativeName=" + hello.getRelativeName("jmx"));
         }
      }
      catch (IllegalArgumentException e) {
         System.out.println("ERROR: " + e.toString());
      }
   }
}
