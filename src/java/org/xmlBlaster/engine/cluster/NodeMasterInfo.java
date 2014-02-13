/*------------------------------------------------------------------------------
Name:      NodeMasterInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Mapping from domain informations to master id
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBuffer;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.cluster.RouteInfo;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;

import org.xml.sax.Attributes;

import java.util.ArrayList;

/**
 * Here we have the rules to find out who is the master of a message. 
 * <p />
 * The rules are configurable with such a message:
 * <pre>
 * &lt;!-- Messages of type "__sys__cluster.node.master[heron]": -->
 *
 * &lt;master stratum='0' refid='bilbo' type='DomainToMaster' version='1.0'>
 *    &lt;![CDATA[
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
public final class NodeMasterInfo implements Comparable, NodeMasterInfoMBean
{
   /** Unique name for logging */
   private String ME = "NodeMasterInfo";
   private final ServerScope serverScope;
   private static Logger log = Logger.getLogger(NodeMasterInfo.class.getName());
   private final ClusterNode clusterNode;

   private static int counter=0;
   private final int count;

   private int stratum = 0;
   public static final boolean DEFAULT_acceptDefault = true;
   private boolean acceptDefault = DEFAULT_acceptDefault;
   public static final boolean DEFAULT_acceptOtherDefault = false;
   private boolean acceptOtherDefault = DEFAULT_acceptOtherDefault;
   private String refId = null;
   private String type = null;
   /** The version of the plugin */
   public static final String DEFAULT_version = "1.0";
   private String version;

   private boolean dirtyRead = RouteInfo.DEFAULT_dirtyRead;

   /** for SAX parsing */
   private int inMaster = 0;

   private transient AccessFilterQos tmpFilter = null;
   protected ArrayList filterList = null;                   // To collect the <filter> when sax parsing
   protected transient AccessFilterQos[] filterArr = null; // To cache the filters in an array
   protected final transient AccessFilterQos[] filterArr0 = new AccessFilterQos[0];

   private transient QueryKeyData tmpKey = null;
   protected ArrayList keyList = null;                      // To collect the <key> when sax parsing
   private QueryKeyData[] keyArr;
   private final transient QueryKeyData[] keyArr0 = new QueryKeyData[0];
   private transient boolean inKey = false;
   
   private ContextNode contextNode;
   /** My JMX registration */
   private Object mbeanHandle;

   /**
    * Create a NodeMasterInfo belonging to the given cluster node. 
    * <p />
    * One instance of this is created for each &lt;master> tag
    */
   public NodeMasterInfo(ServerScope glob, ClusterNode clusterNode) throws XmlBlasterException {
      this.serverScope = glob;

      this.clusterNode = clusterNode;
      this.ME = this.ME + "-" + this.serverScope.getId();

      synchronized (NodeMasterInfo.class) {
         count = counter++;
      }
      version = glob.getProperty().get("cluster.domainMapper.version", DEFAULT_version);
      
      this.contextNode = new ContextNode(ContextNode.CLUSTERMASTER_MARKER_TAG,
            ""+getCount(), clusterNode.getContextNode());
      this.mbeanHandle = this.serverScope.registerMBean(this.contextNode, this);
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
    * @return never null
    */
   public QueryKeyData[] getKeyMappings() {
      if (this.keyArr != null || this.keyList == null || this.keyList.size() < 1) {
         return (this.keyArr == null) ? keyArr0 : this.keyArr;
      }

      synchronized (this.keyList) {
         this.keyArr = new QueryKeyData[this.keyList.size()];
         this.keyList.toArray(this.keyArr);
         return this.keyArr;
      }
   }

   public String getKeyMappingFirstLog() {
	   QueryKeyData[] arr = getKeyMappings();
	   if (arr == null || arr.length == 0)
		   return "";
	   return arr[0].toString();
   }

   /**
    * Access the cluster master filters
    * @return never null but length == 0 if none is specified. 
    */
   public final AccessFilterQos[] getAccessFilterArr() {
      if (this.filterArr != null || this.filterList == null || this.filterList.size() < 1) {
         return (this.filterArr == null) ? filterArr0 : this.filterArr;
      }

      synchronized (this.filterList) {
         this.filterArr = new AccessFilterQos[filterList.size()];
         this.filterList.toArray(filterArr);
         return this.filterArr;
      }
   }

   /**
    * The distance of the node to the master. 
    * @param 0 is the master, 1 is the direct slave, 2 is the slave of the slave ...
    */
   public void setStratum(int stratum) {
      if (stratum < 0) throw new IllegalArgumentException("NodeMasterInfo: stratum can't be small zero");
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
    * Are we master for default domains?
    */
   public void setAcceptDefault(boolean acceptDefault) {
      this.acceptDefault = acceptDefault;
   }

   /**
    * Are we master for messages with the default domain?
    */
   public boolean isAcceptDefault() {
      return this.acceptDefault;
   }

   /**
    * Are we master for default domains of other nodes?
    */
   public void setAcceptOtherDefault(boolean acceptOtherDefault) {
      this.acceptOtherDefault = acceptOtherDefault;
   }

   /**
    * Are we master for messages with default domain from other nodes?
    */
   public boolean isAcceptOtherDefault() {
      return this.acceptOtherDefault;
   }

   /**
    * @return true if cluster slaves cache forwarded publish messages
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/cluster.dirtyRead.html">The cluster.dirtyRead requirement</a>
    */
   public boolean isDirtyRead() {
      return this.dirtyRead;
   }

   /**
    * @param dirtyRead true if cluster slaves cache forwarded publish messages
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/cluster.dirtyRead.html">The cluster.dirtyRead requirement</a>
    */
   public void setDirtyRead(boolean dirtyRead) {
      this.dirtyRead = dirtyRead;
   }

   /**
    * Called for SAX master start tag
    * @return true if ok, false on error
    */
   public final boolean startElement(String uri, String localName, String name, StringBuffer character, Attributes attrs) {
      //log.info(ME, "startElement: name=" + name + " character='" + character.toString() + "'");
      if (name.equalsIgnoreCase("master")) {
         inMaster++;
         if (inMaster > 1) return false; // ignore nested master tags
         if (attrs != null) {
            String tmp = attrs.getValue("stratum");
            if (tmp != null) { try { setStratum(Integer.parseInt(tmp.trim())); } catch(NumberFormatException e) { log.severe("Invalid <master stratum='" + tmp + "'"); }; }
            tmp = attrs.getValue("refid");
            if (tmp != null) setRefId(tmp.trim());
            tmp = attrs.getValue("type");
            if (tmp != null) setType(tmp.trim());
            tmp = attrs.getValue("version");
            if (tmp != null) setVersion(tmp.trim());
            tmp = attrs.getValue("acceptDefault");
            if (tmp != null) { try { setAcceptDefault(Boolean.valueOf(tmp.trim()).booleanValue()); } catch(NumberFormatException e) { log.severe("Invalid <master acceptDefault='" + tmp + "'"); }; }
            tmp = attrs.getValue("acceptOtherDefault");
            if (tmp != null) { try { setAcceptOtherDefault(Boolean.valueOf(tmp.trim()).booleanValue()); } catch(NumberFormatException e) { log.severe("Invalid <master acceptOtherDefault='" + tmp + "'"); }; }
            tmp = attrs.getValue("dirtyRead");
            if (tmp != null) { try { setDirtyRead(Boolean.valueOf(tmp.trim()).booleanValue()); } catch(NumberFormatException e) { log.severe("Invalid <master dirtyRead='" + tmp + "'"); }; }
         }
         character.setLength(0);
         if (getType() == null) {
            log.warning("Missing 'master' attribute 'type', ignoring the master request");
            setType(null);
            return false;
         }
         return true;
      }

      if (inMaster == 1 && name.equalsIgnoreCase("key")) {
         inKey = true;
      }

      if (inMaster == 1 && name.equalsIgnoreCase("filter")) {
         tmpFilter = new AccessFilterQos(serverScope);
         boolean ok = tmpFilter.startElement(uri, localName, name, character, attrs);
         if (ok) {
            if (this.filterList == null) {
               this.filterList = new ArrayList();
               this.filterArr = null;
            }
            filterList.add(tmpFilter);
         }
         else
            tmpFilter = null;
         return ok;
      }

      if (inKey) {
         // Collect everything to pass it later to XmlKey for DOM parsing:
         character.append("<").append(name);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int ii=0; ii<len; ii++) {
                character.append(" ").append(attrs.getQName(ii)).append("='").append(attrs.getValue(ii)).append("'");
            }
         }
         //log.info("Parsing " + character.toString());
         character.append(">");
         return true;
      }

      return false;
   }

   /**
    * Handle SAX parsed end element
    */
   public final void endElement(String uri, String localName, String name, StringBuffer character) {
      //log.info(ME, "endElement: name=" + name + " character='" + character.toString() + "'");
      if (name.equalsIgnoreCase("master")) {
         inMaster--;
         if (inMaster > 0) return; // ignore nested master tags
         character.setLength(0);
      }

      if (inKey)
         character.append("</"+name+">");

      if (inMaster == 1 && name.equalsIgnoreCase("key")) {
         inKey = false;
         if (log.isLoggable(Level.FINE)) log.fine("Parsing filter xmlKey=" + character.toString());
         try {
            tmpKey = serverScope.getQueryKeyFactory().readObject(character.toString()); // Parse it
            if (keyList == null) keyList = new ArrayList();
            keyList.add(tmpKey);
         }
         catch (XmlBlasterException e) {
            log.warning("Parsing <master>" + character.toString() + " failed, ignoring this rule: " + e.toString());
         }
         character.setLength(0);
         return;
      }

      if (inMaster == 1 && name.equalsIgnoreCase("filter")) {
         if (tmpFilter != null)
            tmpFilter.endElement(uri, localName, name, character);
         return;
      }
   }
   
   /**
    * The XML markup for JMX. 
    */
   public String getConfiguration() {
      return toXml("", true);
   }
   
   /**
    * Reconfigure node routing via JMX (transient only). 
    * Is not activated and not intensively tested
    */
   public String setConfiguration(String xml) {
      return clusterNode.replace(this, xml);
   }
   
   /**
    * For JMX only. 
    * @return
    */
   public String destroy() {
      String xml = toXml();
      this.shutdown();
      log.warning("Configuration of '" + getId() + "' is destroyed. Please also change your configuration file to survive xmlBlaster restart: " + xml);
      return "Configuration of '" + getId() + "' is destroyed.\nPlease also change your configuration file to survive xmlBlaster restart";
   }
   
   public void shutdown() {
      Object mbean = this.mbeanHandle;
      ServerScope sc = this.serverScope;
      if (sc != null && mbean != null) {
         this.mbeanHandle = null;
         sc.unregisterMBean(mbean);
      }
      clusterNode.removeNodeMasterInfo(this);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml() {
      return toXml((String)null, false);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * Only none default values are dumped for performance reasons
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   public final String toXml(String extraOffset, boolean forceAllAttributes) {
      XmlBuffer sb = new XmlBuffer(300);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<master");
      if (forceAllAttributes || getStratum() > 0)
         sb.append(" stratum='").append(getStratum()).append("'");
      if (getRefId() != null)
         sb.append(" refId='").append(getRefId()).append("'");
      sb.append(" type='").appendAttributeEscaped(getType()).append("'");
      if (forceAllAttributes || !DEFAULT_version.equals(getVersion()))
          sb.append(" version='").append(getVersion()).append("'");
      if (forceAllAttributes || DEFAULT_acceptDefault != isAcceptDefault())
          sb.append(" acceptDefault='").append(isAcceptDefault()).append("'");
      if (forceAllAttributes || DEFAULT_acceptOtherDefault != isAcceptOtherDefault())
          sb.append(" acceptOtherDefault='").append(isAcceptOtherDefault()).append("'");
      if (forceAllAttributes || RouteInfo.DEFAULT_dirtyRead != isDirtyRead())
          sb.append(" dirtyRead='").append(isDirtyRead()).append("'");
      sb.append(">");

      QueryKeyData[] keyArr = getKeyMappings();
      for (int ii=0; ii<keyArr.length; ii++)
         sb.append(keyArr[ii].toXml(extraOffset+Constants.INDENT));

      AccessFilterQos[] filterArr = getAccessFilterArr();
      for (int ii=0; ii<filterArr.length; ii++)
         sb.append(filterArr[ii].toXml(extraOffset+Constants.INDENT));

      sb.append(offset).append("</master>");

      return sb.toString();
   }

   /**
    * Enforced by interface Comparable, does sorting
    * of NodeMasterInfo instances in a treeSet with stratum
    */
   public int compareTo(Object obj)  {
      NodeMasterInfo a = (NodeMasterInfo)obj;
      
      try {
         if (getClusterNode().getConnectionState() != a.getClusterNode().getConnectionState())
            return getClusterNode().getConnectionState() - a.getClusterNode().getConnectionState();
      }
      catch (XmlBlasterException e) {
         log.severe("Unexpected exception in compareTo(), no sorting for connection state possible: " + e.toString());
      }

      if (getStratum() != a.getStratum())
         return getStratum() - a.getStratum();
   
      return getCount() - a.getCount(); 
   }

   public String toString() {
	   QueryKeyData[] arr = getKeyMappings();
	   StringBuilder sb = new StringBuilder();
	   sb.append("NodeMasterInfo");
	   if (clusterNode.getRemoteSessionName() != null)
		   sb.append(",").append(clusterNode.getRemoteSessionName().getAbsoluteName());
	   for (QueryKeyData q: arr) {
		   sb.append(",queryKeyData=").append(q.toXml());
	   }
	   return sb.toString();
   }
}
