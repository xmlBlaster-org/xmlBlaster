/*------------------------------------------------------------------------------
Name:      NodeDomainInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Mapping from domain informations to master id
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.xml2java.XmlKey;

import org.xml.sax.Attributes;

/**
 * Here we have the rules to find out who is the master of a message. 
 * <p />
 * The rules are configurable with such a message:
 * <pre>
 * &lt;!-- Messages of type "__sys__cluster.node.domainmapping:heron": -->
 *
 * &lt;master stratum='0' refid='bilbo' type='DomainToMaster' version='1.0'>
 *    &lt;![CDATA[
 *       &lt;key domain=''/>
 *       &lt;key domain='rugby'/>
 *    ]]>
 * &lt;/master>
 * </pre>
 * Here the plugin 'DomainToMaster' with version '1.0' is chosen, this is mapped
 * with the xmlBlaster.properties entry
 * <pre>
 * MapMsgToMasterPlugin[DomainToMaster][1.0]=org.xmlBlaster.engine.cluster.simpledomain.DomainToMaster
 * </pre>
 * to the real java implementation.
 */
public final class NodeDomainInfo implements Comparable
{
   /** Unique name for logging */
   private final static String ME = "NodeDomainInfo";
   private final Global glob;
   private final ClusterNode clusterNode;

   private static int counter=0;
   private final int count;

   private int stratum = 0;
   private String refId = null;
   private String type = null;
   /** The version of the plugin */
   public static final String DEFAULT_version = "1.0";
   private String version;
   private String query = "";

   private Object preparedQuery = null;

   private XmlKey[] keyMappings;

   /**
    * Create a NodeDomainInfo belonging to the given cluster node. 
    */
   public NodeDomainInfo(Global glob, ClusterNode clusterNode) {
      this.glob = glob;
      this.clusterNode = clusterNode;
      synchronized (NodeDomainInfo.class) {
         count = counter++;
      }
      version = glob.getProperty().get("cluster.domainMapper.version", DEFAULT_version);
   }

   /** Unique number (in this JVM) */
   public int getCount() {
      return count;
   }

   /**
    * Access my manager
    * @return The clusterNode which takes care of me
    */
   public ClusterNode getClusterNode() {
      return clusterNode;
   }

   /**
    * Convenience method, delegates to clusterNode.getNodeId(). 
    * @return My node id object
    */
   public NodeId getNodeId() {
      return clusterNode.getNodeId();
   }

   /**
    * Convenience method, delegates to clusterNode.getNodeId().getId(). 
    * @return My node id String
    */
   public String getId() {
      return getNodeId().getId();
   }

   /**
    * Get the key based rules
    */
   public XmlKey[] getKeyMappings() {
         return keyMappings;
   }

   /**
    * Set a key based rule
    * @parameter XmlKey, e.g.<pre>
    *            &lt;key domain='rugby'/>
    */
   public void setKeyMappings(XmlKey[] keyMappings){
         this.keyMappings = keyMappings;
   }

   /**
    * Set the master query, it should fit to the protocol-type. 
    * <p />
    * Clears the pre parsed query object to null
    * @param query The master query, e.g. "&lt;key domain='RUGBY'>" to select RUGBY messages
    */
   public final void setQuery(String query) {
      if (query == null)
         this.query = "";
      else
         this.query = query;
      this.preparedQuery = null;
   }

   /**
    * Returns the query, the syntax is depending on what your plugin supports.
    * @return e.g. "&lt;key domain='RUGBY'>", is never null
    */
   public final String getQuery() {
      return query;
   }

   /**
    * This object is for the plugin writer, she can
    * parse the query and store here an arbitrary pre parsed object
    * for better performance.
    */
   public Object getPreparedQuery() {
      return this.preparedQuery;
   }

   /**
    * This object is for the plugin writer, she can
    * parse the query and store here an arbitrary pre parsed object
    * for better performance.
    */
   public void setPreparedQuery(Object preparedQuery) {
      this.preparedQuery = preparedQuery;
   }

   /**
    * The distance of the node to the master. 
    * @param 0 is the master, 1 is the direct slave, 2 is the slave of the slave ...
    */
   public void setStratum(int stratum) {
      if (stratum < 0) throw new IllegalArgumentException("NodeDomainInfo: stratum can't be small zero");
      this.stratum = stratum;
   }

   /**
    * The distance of the node to the master. 
    * @return 0 is the master, 1 is the direct slave, 2 is the slave of the slave ...
    */
   public int getStratum() {
      return this.stratum;
   }

   public void setRefId(String refId) {
      this.refId = refId;
   }

   public String getRefId() {
      return this.refId;
   }

   /**
    * The plugin type. 
    */
   public void setType(String type) {
      this.type = type;
   }

   /**
    * The plugin type. 
    * @return Defaults to "DomainToMaster"
    */
   public String getType() {
      return this.type;
   }

   /**
    * The plugin version. 
    */
   public void setVersion(String version) {
      this.version = version;
   }

   /**
    * The plugin version. 
    * @return Defaults to "1.0"
    */
   public String getVersion() {
      return this.version;
   }

   /**
    * Called for SAX master start tag
    * @return true if ok, false on error
    */
   public final boolean startElement(String uri, String localName, String name, StringBuffer character, Attributes attrs) {
      //glob.getLog().info(ME, "startElement: name=" + name + " character='" + character.toString() + "'");
      String tmp1 = character.toString().trim(); // The query
      if (tmp1.length() > 0) {
         setQuery(tmp1);
         character.setLength(0);
      }

      if (name.equalsIgnoreCase("master")) {
         if (attrs != null) {
            String tmp = attrs.getValue("stratum");
            if (tmp != null) { try { setStratum(Integer.parseInt(tmp.trim())); } catch(NumberFormatException e) { glob.getLog().error(ME, "Invalid <master stratum='" + tmp + "'"); }; }
            tmp = attrs.getValue("refid");
            if (tmp != null) setRefId(tmp.trim());
            tmp = attrs.getValue("type");
            if (tmp != null) setType(tmp.trim());
            tmp = attrs.getValue("version");
            if (tmp != null) setVersion(tmp.trim());
         }
         character.setLength(0);
         if (getType() == null) {
            glob.getLog().warn(ME, "Missing 'master' attribute 'type', ignoring the master request");
            setType(null);
            return false;
         }
         return true;
      }

      return false;
   }

   /**
    * Handle SAX parsed end element
    */
   public final void endElement(String uri, String localName, String name, StringBuffer character) {
      //glob.getLog().info(ME, "endElement: name=" + name + " character='" + character.toString() + "'");
      if (name.equalsIgnoreCase("master")) {
         String tmp = character.toString().trim(); // The query
         if (tmp.length() > 0)
            setQuery(tmp);
         if (getQuery() == null || getQuery().length() < 1)
            glob.getLog().error(ME, "<master> contains no query data to map messages to their master node");
         character.setLength(0);
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
    * <br>
    * Only none default values are dumped for performance reasons
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(300);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<master");
      if (getStratum() > 0)
         sb.append(" stratum='").append(getStratum()).append("'");
      if (getRefId() != null)
         sb.append(" refId='").append(getRefId()).append("'");
      sb.append(" type='").append(getType()).append("'");
      if (!DEFAULT_version.equals(getVersion()))
          sb.append(" version='").append(getVersion()).append("'");
      sb.append(">");

      sb.append(offset).append("   <![CDATA[").append(getQuery()).append("]]>");
      sb.append(offset).append("</master>");

      return sb.toString();
   }

   /**
    * Enforced by interface Comparable, does sorting
    * of NodeDomainInfo instances in a treeSet with stratum
    */
   public int compareTo(Object obj)  {
      NodeDomainInfo a = (NodeDomainInfo)obj;
      
      try {
         if (getClusterNode().getConnectionState() != a.getClusterNode().getConnectionState())
            return getClusterNode().getConnectionState() - a.getClusterNode().getConnectionState();
      }
      catch (XmlBlasterException e) {
         glob.getLog().error(ME, "Unexpected exception in compareTo(), no sorting for connection state possible: " + e.toString());
      }

      if (getStratum() != a.getStratum())
         return getStratum() - a.getStratum();
   
      return getCount() - a.getCount(); 
   }
}
