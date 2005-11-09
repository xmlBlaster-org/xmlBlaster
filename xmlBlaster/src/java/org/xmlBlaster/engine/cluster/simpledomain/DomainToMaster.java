/*------------------------------------------------------------------------------
Name:      DomainToMaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Simple demo implementation for clustering
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster.simpledomain;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.TopicHandler;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.mime.I_AccessFilter;
import org.xmlBlaster.engine.cluster.ClusterManager;
import org.xmlBlaster.engine.cluster.NodeDomainInfo;
import org.xmlBlaster.engine.cluster.I_MapMsgToMasterId;
import org.xmlBlaster.authentication.SessionInfo;

/**
 * Finds the master of a message depending
 * on the <i>domain</i> attribute of the message key tag.
 * <p />
 * This is a simple demo implementation for clustering, the plugin
 * can be loaded depending on the mime type of a message, we
 * register it here for all messages, see getMessages().
 * <p />
 * Switch on logging with '-trace[cluster] true'
 * <p />
 * @author xmlBlaster@marcelruff.info 
 * @since 0.79e
 */
final public class DomainToMaster implements I_Plugin, I_MapMsgToMasterId {
   private String ME = "DomainToMaster";
   private Global glob;
   private LogChannel log;

   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(Global glob, ClusterManager clusterManager) {
      this.glob = glob;
      this.log = glob.getLog("cluster");
      this.ME = this.ME + "-" + this.glob.getId();
      log.info(ME, "The simple domain based master mapper plugin is initialized");
   }

   /**
    * Is called when new configuration arrived, notify the plugin to empty its
    * cache or do whatever it needs to do. 
    */
   public void reset() {
      log.warn(ME, "New configuration, nothing to do");
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
   }

   /**
    * Return plugin type for Plugin loader
    * @return "SimpleDomainToMasterMapper"
    */
   public String getType() {
      return "SimpleDomainToMasterMapper";
   }

   /**
    * Return plugin version for Plugin loader
    * @return "1.0"
    */
   public String getVersion() {
      return "1.0";
   }

   /**
    * Get the content MIME type for which this plugin applies
    * @return "*" This plugin handles all mime types
    */
   public String[] getMimeTypes() {
      String[] mimeTypes = { "*" };
      return mimeTypes;
   }

   /**
    * Get the content MIME version number for which this plugin applies
    * @return "1.0" (this is the default version number)
    */
   public String[] getMimeExtended() {
      String[] mimeExtended = { Constants.DEFAULT_CONTENT_MIME_EXTENDED }; // "1.0"
      return mimeExtended;
   }

   /**
    * Get a human readable name of this implementation
    * @return "SimpleDomainToMasterMapper"
    */
   public String getName() {
      return "SimpleDomainToMasterMapper";
   }

   /**
    * Find out who is the master of the provided message. 
    * <pre>
    *   &lt;clusternode id='heron'>
    *   &lt;master type='DomainToMaster' version='0.9'>
    *     &lt;![CDATA[
    *       &lt;key type='DOMAIN' domain='RUGBY'/>
    *       &lt;key type='XPATH'>//GAME&lt;/key>
    *     ]]>
    *     &lt;filter type='ContentLength'>
    *       8000
    *     &lt;/filter>
    *     &lt;filter type='ContainsChecker' version='7.1' xy='true'>
    *       rugby
    *     &lt;/filter>
    *   &lt;/master>
    *   &lt;/clusternode>
    * </pre>
    * If the attribute domain='RUGBY' and the meta informations contains the tag &lt;GAME>
    * and the message is shorter 8000 bytes and the message content contains a token 'rugby'
    * the cluster node 'heron' is chosen as the master of the message.
    * @param msgUnit The message
    * @return The nodeDomainInfo (same as you passed as parameter) it this is a possible master
    *         or null if not suitable.<br />
    * You can access the master ClusterNode with <code>nodeDomainInfo.getClusterNode()</code> and the xmlBlasterConnection
    * to the master node with <code>nodeDomainInfo.getClusterNode().getXmlBlasterAccess()</code>
    */
   public NodeDomainInfo getMasterId(NodeDomainInfo nodeDomainInfo, MsgUnit msgUnit) throws XmlBlasterException {

      QueryKeyData[] keyMappings = nodeDomainInfo.getKeyMappings();  // These are the key based queries

      if (msgUnit.getQosData() instanceof MsgQosData) {
         MsgQosData qos = (MsgQosData)msgUnit.getQosData();
         if (qos.isPtp()) {
            // We check the domain of each MsgUnit entry (PtP messages may use a static topic just for communication channel)
            for (int ii=0; keyMappings!=null && ii<keyMappings.length; ii++) {
               if (keyMappings[ii].getDomain().equals("*") || keyMappings[ii].getDomain().equals(msgUnit.getKeyData().getDomain())) {
                  if (log.TRACE) log.trace(ME, "Found master='" + nodeDomainInfo.getNodeId().getId() +
                           "' stratum=" + nodeDomainInfo.getStratum() + " for PtP message '" + msgUnit.getLogId() +
                           "' domain='" + msgUnit.getKeyData().getDomain() + "'.");
                  return nodeDomainInfo;
               }
            }
            // The following query checks are only useful for PtP messages if the XmlKey for each MsgUnit in a topic remains same!
         }
      }

      /*
      // Look if we can handle it simple ...
      if (msgUnit.isDefaultDomain()) {
         if (nodeDomainInfo.getClusterNode().isLocalNode()) {
            if (nodeDomainInfo.getAcceptDefault()==true) {
               // if no domain is specified and the local node accepts default messages -> local node is master
               if (log.TRACE) log.trace(ME, "Message oid='" + msgUnit.getKeyOid() + "' domain='" + xmlKey.getDomain() + "' is handled by local node");
               log.warn(ME, "<filter> additional check is not implemented");
               return nodeDomainInfo; // Found the master nodeDomainInfo.getClusterNode(); 
            }
         }
         else {
            if (nodeDomainInfo.getAcceptOtherDefault()==true) {
               log.info(ME, "Found master='" + nodeDomainInfo.getNodeId().getId() + "' for message oid='" + msgUnit.getKeyOid() + "' which accepts other default domains");
               log.warn(ME, "<filter> additional check is not implemented");
               return nodeDomainInfo; // Found the master nodeDomainInfo.getClusterNode(); 
            }
         }
      }
      */

      // Now check if we are master
      XmlKey xmlKey = null;
      for (int ii=0; keyMappings!=null && ii<keyMappings.length; ii++) {
         if (ii==0) {
            // Try to find the DOM parsed XmlKey object:
            TopicHandler topicHandler = glob.getRequestBroker().getMessageHandlerFromOid(msgUnit.getKeyOid());
            if (topicHandler != null && topicHandler.hasXmlKey()) {
               xmlKey = topicHandler.getXmlKey();
            }
            else {
               xmlKey = new XmlKey(glob, msgUnit.getKeyData());
            }
         }
         if (xmlKey.match(keyMappings[ii])) {
            if (log.TRACE) log.trace(ME, "Found master='" + nodeDomainInfo.getNodeId().getId() +
                           "' stratum=" + nodeDomainInfo.getStratum() + " for message '" + msgUnit.getLogId() +
                           "' domain='" + xmlKey.getDomain() + "'.");
            AccessFilterQos[] filterQos = keyMappings[ii].getAccessFilterArr();
            if (filterQos != null && filterQos.length > 0) {
               if (log.TRACE) log.trace(ME, "Found " + filterQos.length + " key specific filter rules in XmlKey ...");
               for (int jj=0; jj<filterQos.length; jj++) {
                  I_AccessFilter filter = glob.getRequestBroker().getAccessPluginManager().getAccessFilter(
                                                filterQos[jj].getType(),
                                                filterQos[jj].getVersion(), 
                                                msgUnit.getContentMime(),
                                                msgUnit.getContentMimeExtended());
                  if (log.TRACE) log.trace(ME, "Checking filter='" + filterQos[jj].getQuery() + "' on message content='" +
                                 msgUnit.getContentStr() + "'");
                  SessionInfo sessionInfo = null; // TODO: Pass sessionInfo here
                  if (filter != null && filter.match(sessionInfo, msgUnit, filterQos[jj].getQuery())) {
                     if (log.TRACE) log.trace(ME, "Found master='" + nodeDomainInfo.getNodeId().getId() + "' stratum=" +
                                    nodeDomainInfo.getStratum() + " for message '" + msgUnit.getLogId() + "' with filter='" + filterQos[jj].getQuery() + "'.");
                     return nodeDomainInfo; // Found the master nodeDomainInfo.getClusterNode(); 
                  }
               }
            }
            else
               return nodeDomainInfo; // Found the master nodeDomainInfo.getClusterNode(); 
         }
      }

      // Check for user supplied filters <master><filter>... These are the filter based queries
      AccessFilterQos[] filterQos = nodeDomainInfo.getAccessFilterArr();
      if (filterQos != null && filterQos.length > 0) {
         if (log.TRACE) log.trace(ME, "Found " + filterQos.length + " global filter rules ...");
         for (int jj=0; jj<filterQos.length; jj++) {
            I_AccessFilter filter = glob.getRequestBroker().getAccessPluginManager().getAccessFilter(
                                          filterQos[jj].getType(),
                                          filterQos[jj].getVersion(), 
                                          msgUnit.getContentMime(),
                                          msgUnit.getContentMimeExtended());
            if (log.TRACE) log.trace(ME, "Checking filter='" + filterQos[jj].getQuery() + "' on message content='" +
                           msgUnit.getContentStr() + "'");
            SessionInfo sessionInfo = null; // TODO: Pass sessionInfo here
            if (filter != null && filter.match(sessionInfo, msgUnit, filterQos[jj].getQuery())) {
               if (log.TRACE) log.trace(ME, "Found master='" + nodeDomainInfo.getNodeId().getId() + "' stratum=" + nodeDomainInfo.getStratum() +
                              " for message '" + msgUnit.getLogId() + "' with filter='" + filterQos[jj].getQuery() + "'.");
               return nodeDomainInfo; // Found the master nodeDomainInfo.getClusterNode(); 
            }
         }
      }

      if (log.TRACE) log.trace(ME, "Node '" + nodeDomainInfo.getId() + "' is not master for message '" +
                     msgUnit.getKeyData().toXml() + "' with given rules=" + nodeDomainInfo.toXml());
      // Another rule can still choose this node as a master

      return null; // This clusternode is not the master
   }

   public void shutdown() throws XmlBlasterException {
   }

}
