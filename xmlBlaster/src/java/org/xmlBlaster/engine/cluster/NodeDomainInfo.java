/*------------------------------------------------------------------------------
Name:      NodeDomainInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Mapping from domain informations to master id
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.helper.AccessFilterQos;

import org.xml.sax.Attributes;

import java.util.Vector;

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
public final class NodeDomainInfo implements Comparable
{
   /** Unique name for logging */
   private String ME = "NodeDomainInfo";
   private final Global glob;
   private final LogChannel log;
   private final ClusterNode clusterNode;

   private static int counter=0;
   private final int count;

   private int stratum = 0;
   public final boolean DEFAULT_acceptDefault = true;
   private boolean acceptDefault = DEFAULT_acceptDefault;
   public final boolean DEFAULT_acceptOtherDefault = false;
   private boolean acceptOtherDefault = DEFAULT_acceptOtherDefault;
   private String refId = null;
   private String type = null;
   /** The version of the plugin */
   public static final String DEFAULT_version = "1.0";
   private String version;
   private String query = "";

   public static final boolean DEFAULT_dirtyRead = false;
   private boolean dirtyRead = DEFAULT_dirtyRead;

   /** for SAX parsing */
   private int inMaster = 0;

   private transient AccessFilterQos tmpFilter = null;
   protected Vector filterVec = null;                      // To collect the <filter> when sax parsing
   protected transient AccessFilterQos[] filterArr = null; // To cache the filters in an array
   private transient boolean inFilter = false;

   private transient XmlKey tmpKey = null;
   protected Vector keyVec = null;                      // To collect the <key> when sax parsing
   private XmlKey[] keyArr;
   private transient boolean inKey = false;

   /**
    * Create a NodeDomainInfo belonging to the given cluster node. 
    * <p />
    * One instance of this is created for each &lt;master> tag
    */
   public NodeDomainInfo(Global glob, ClusterNode clusterNode) {
      this.glob = glob;
      this.log = this.glob.getLog("cluster");
      this.clusterNode = clusterNode;
      this.ME = this.ME + "-" + this.glob.getId();

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
      if (keyArr != null || keyVec == null || keyVec.size() < 1)
         return keyArr;

      keyArr = new XmlKey[keyVec.size()];
      keyVec.toArray(keyArr);
      return keyArr;
   }

   /**
    * Set a key based rule
    * @parameter XmlKey, e.g.<pre>
    *            &lt;key domain='rugby'/>
    */
   public void setKeyMappings(XmlKey[] keyArr){
         this.keyArr = keyArr;
   }

   /**
    * Return the cluster master filters or null if none is specified. 
    */
   public final AccessFilterQos[] getAccessFilterArr()
   {
      if (filterArr != null || filterVec == null || filterVec.size() < 1)
         return filterArr;

      filterArr = new AccessFilterQos[filterVec.size()];
      filterVec.toArray(filterArr);
      return filterArr;
   }

   /**
    * Return the XmlKey master filters or null if none is specified. 
    */
   public final XmlKey[] getXmlKeyFilters()
   {
      if (keyArr != null || keyVec == null || keyVec.size() < 1)
         return keyArr;

      keyArr = new XmlKey[keyVec.size()];
      keyVec.toArray(keyArr);
      return keyArr;
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
    * Are we master for default domains?
    */
   public void setAcceptDefault(boolean acceptDefault) {
      this.acceptDefault = acceptDefault;
   }

   /**
    * Are we master for messages with the default domain?
    */
   public boolean getAcceptDefault() {
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
   public boolean getAcceptOtherDefault() {
      return this.acceptOtherDefault;
   }

   /**
    * @return true if cluster slaves cache forwarded publish messages
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/cluster.dirtyRead.html">The cluster.dirtyRead requirement</a>
    */
   public boolean getDirtyRead() {
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
            if (tmp != null) { try { setStratum(Integer.parseInt(tmp.trim())); } catch(NumberFormatException e) { log.error(ME, "Invalid <master stratum='" + tmp + "'"); }; }
            tmp = attrs.getValue("refid");
            if (tmp != null) setRefId(tmp.trim());
            tmp = attrs.getValue("type");
            if (tmp != null) setType(tmp.trim());
            tmp = attrs.getValue("version");
            if (tmp != null) setVersion(tmp.trim());
            tmp = attrs.getValue("acceptDefault");
            if (tmp != null) { try { setAcceptDefault(Boolean.valueOf(tmp.trim()).booleanValue()); } catch(NumberFormatException e) { log.error(ME, "Invalid <master acceptDefault='" + tmp + "'"); }; }
            tmp = attrs.getValue("acceptOtherDefault");
            if (tmp != null) { try { setAcceptOtherDefault(Boolean.valueOf(tmp.trim()).booleanValue()); } catch(NumberFormatException e) { log.error(ME, "Invalid <master acceptOtherDefault='" + tmp + "'"); }; }
            tmp = attrs.getValue("dirtyRead");
            if (tmp != null) { try { setDirtyRead(Boolean.valueOf(tmp.trim()).booleanValue()); } catch(NumberFormatException e) { log.error(ME, "Invalid <master dirtyRead='" + tmp + "'"); }; }
         }
         character.setLength(0);
         if (getType() == null) {
            log.warn(ME, "Missing 'master' attribute 'type', ignoring the master request");
            setType(null);
            return false;
         }
         return true;
      }

      if (inMaster == 1 && name.equalsIgnoreCase("key")) {
         inKey = true;
      }

      if (inMaster == 1 && name.equalsIgnoreCase("filter")) {
         inFilter = true;
         tmpFilter = new AccessFilterQos(glob);
         boolean ok = tmpFilter.startElement(uri, localName, name, character, attrs);
         if (ok) {
            if (filterVec == null) filterVec = new Vector();
            filterVec.addElement(tmpFilter);
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
         if (log.TRACE) log.trace(ME, "Parsing filter xmlKey=" + character.toString());
         try {
            tmpKey = new XmlKey(glob, character.toString()); // Do a DOM parse on the collected tags
            if (keyVec == null) keyVec = new Vector();
            keyVec.addElement(tmpKey);
         }
         catch (XmlBlasterException e) {
            log.warn(ME, "Parsing <master>" + character.toString() + " failed, ignoring this rule: " + e.toString());
         }
         character.setLength(0);
         return;
      }

      if (inMaster == 1 && name.equalsIgnoreCase("filter")) {
         inFilter = false;
         if (tmpFilter != null)
            tmpFilter.endElement(uri, localName, name, character);
         return;
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
      if (DEFAULT_acceptDefault != getAcceptDefault())
          sb.append(" acceptDefault='").append(getAcceptDefault()).append("'");
      if (DEFAULT_acceptOtherDefault != getAcceptOtherDefault())
          sb.append(" acceptOtherDefault='").append(getAcceptOtherDefault()).append("'");
      if (DEFAULT_dirtyRead != getDirtyRead())
          sb.append(" dirtyRead='").append(getDirtyRead()).append("'");
      sb.append(">");

      XmlKey[] keyArr = getXmlKeyFilters();
      for (int ii=0; keyArr != null && ii<keyArr.length; ii++)
         sb.append(offset).append("   ").append(keyArr[ii].literal());

      AccessFilterQos[] filterArr = getAccessFilterArr();
      for (int ii=0; filterArr != null && ii<filterArr.length; ii++)
         sb.append(filterArr[ii].toXml(extraOffset+"   "));

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
         log.error(ME, "Unexpected exception in compareTo(), no sorting for connection state possible: " + e.toString());
      }

      if (getStratum() != a.getStratum())
         return getStratum() - a.getStratum();
   
      return getCount() - a.getCount(); 
   }
}
