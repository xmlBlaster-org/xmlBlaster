/*------------------------------------------------------------------------------
Name:      DomainToMaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Simple demo implementation for clustering
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster.simpledomain;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.mime.I_AccessFilter;
import org.xmlBlaster.engine.cluster.ClusterManager;
import org.xmlBlaster.engine.cluster.NodeMasterInfo;
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
   private ServerScope glob;
   private static Logger log = Logger.getLogger(DomainToMaster.class.getName());

   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(ServerScope glob, ClusterManager clusterManager) {
      this.glob = glob;

      this.ME = this.ME + "-" + this.glob.getId();
      log.info("The simple domain based master mapper plugin is initialized");
   }

   /**
    * Is called when new configuration arrived, notify the plugin to empty its
    * cache or do whatever it needs to do. 
    */
   public void reset() {
      log.warning("New configuration, nothing to do");
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
    * @return The nodeMasterInfo (same as you passed as parameter) it this is a possible master
    *         or null if not suitable.<br />
    * You can access the master ClusterNode with <code>nodeMasterInfo.getClusterNode()</code> and the xmlBlasterConnection
    * to the master node with <code>nodeMasterInfo.getClusterNode().getXmlBlasterAccess()</code>
    */
   public NodeMasterInfo getMasterId(NodeMasterInfo nodeMasterInfo, MsgUnit msgUnit) throws XmlBlasterException {

      QueryKeyData[] keyMappings = nodeMasterInfo.getKeyMappings();  // These are the key based queries

      if (msgUnit.getQosData() instanceof MsgQosData) {
         MsgQosData qos = (MsgQosData)msgUnit.getQosData();
         if (qos.isPtp()) {
            // We check the domain of each MsgUnit entry (PtP messages may use a static topic just for communication channel)
            for (int ii=0; ii<keyMappings.length; ii++) {
               String domain = keyMappings[ii].getDomain();
               if (domain == null)
            	   continue;
               if (domain.equals("*") || domain.equals(msgUnit.getKeyData().getDomain())) {
                  if (log.isLoggable(Level.FINE)) log.fine("Found master='" + nodeMasterInfo.getNodeId().getId() +
                           "' stratum=" + nodeMasterInfo.getStratum() + " for PtP message '" + msgUnit.getLogId() +
                           "' domain='" + msgUnit.getKeyData().getDomain() + "'.");
                  return nodeMasterInfo;
               }
            }
         }
      }
      
      // The following checks run for each MsgUnit key (not the immutable TopicHandler key)

      // Now check if we are master
      XmlKey xmlKey = new XmlKey(glob, msgUnit.getKeyData());
      for (int ii=0; ii<keyMappings.length; ii++) {
         QueryKeyData keyMapping = keyMappings[ii];
         /*
         if (ii==0) {
            // Try to find the DOM parsed XmlKey object:
            TopicHandler topicHandler = this.glob.getTopicAccessor().access(msgUnit.getKeyOid());
            try {
               if (topicHandler != null && topicHandler.hasXmlKey()) {
                  xmlKey = topicHandler.getXmlKey();
               }
               else {
                  xmlKey = new XmlKey(glob, msgUnit.getKeyData());
               }
            }
            finally {
               if (topicHandler != null) this.glob.getTopicAccessor().release(topicHandler);
            }
         }
         */
         
         if (keyMapping.isDomain() && !msgUnit.hasDomain()) {
            if (nodeMasterInfo.getClusterNode().isLocalNode()) {
               if (nodeMasterInfo.isAcceptDefault()==true) {
                  // if no domain is specified and the local node accepts default messages -> local node is master
                  if (log.isLoggable(Level.FINE)) log.fine("Message oid='" + msgUnit.getKeyOid() + "' domain='" + xmlKey.getDomain() + "' is handled by local node");
                  AccessFilterQos[] filterQos = keyMapping.getAccessFilterArr();
                  if (filterQos != null && filterQos.length > 0) {
                     log.warning("<filter> additional check is not implemented: " + keyMapping.toXml());
                  }
                  return nodeMasterInfo; // Found the master nodeMasterInfo.getClusterNode(); 
               }
            }
            else {
               if (nodeMasterInfo.isAcceptOtherDefault()==true) {
                  log.info("Found master='" + nodeMasterInfo.getNodeId().getId() + "' for message oid='" + msgUnit.getKeyOid() + "' which accepts other default domains");
                  AccessFilterQos[] filterQos = keyMapping.getAccessFilterArr();
                  if (filterQos != null && filterQos.length > 0) {
                     log.warning("<filter> additional check is not implemented: " + keyMapping.toXml());
                  }
                  return nodeMasterInfo; // Found the master nodeMasterInfo.getClusterNode(); 
               }
            }
         }
         
         // TODO: If filter has a prepared query cache switched on,
         // we should go over the TopicHandlerAccessor to force single threaded match() access
         if (xmlKey.match(keyMapping)) { // Checks EXACT DOMAIN XPATH
            if (log.isLoggable(Level.FINE)) log.fine("Found master='" + nodeMasterInfo.getNodeId().getId() +
                           "' stratum=" + nodeMasterInfo.getStratum() + " for message '" + msgUnit.getLogId() +
                           "' domain='" + xmlKey.getDomain() + "'.");
            AccessFilterQos[] filterQos = keyMapping.getAccessFilterArr();
            if (filterQos != null && filterQos.length > 0) {
               if (log.isLoggable(Level.FINE)) log.fine("Found " + filterQos.length + " key specific filter rules in XmlKey ...");
               for (int jj=0; jj<filterQos.length; jj++) {
                  I_AccessFilter filter = glob.getRequestBroker().getAccessPluginManager().getAccessFilter(
                                                filterQos[jj].getType(),
                                                filterQos[jj].getVersion(), 
                                                msgUnit.getContentMime(),
                                                msgUnit.getContentMimeExtended());
                  if (log.isLoggable(Level.FINE)) log.fine("Checking filter='" + filterQos[jj].getQuery() + "' on message content='" +
                                 msgUnit.getContentStr() + "'");
                  SessionInfo sessionInfo = null; // TODO: Pass sessionInfo here
                  if (filter != null && filter.match(sessionInfo, msgUnit, filterQos[jj].getQuery())) {
                     if (log.isLoggable(Level.FINE)) log.fine("Found master='" + nodeMasterInfo.getNodeId().getId() + "' stratum=" +
                                    nodeMasterInfo.getStratum() + " for message '" + msgUnit.getLogId() + "' with filter='" + filterQos[jj].getQuery() + "'.");
                     return nodeMasterInfo; // Found the master nodeMasterInfo.getClusterNode(); 
                  }
               }
            }
            else
               return nodeMasterInfo; // Found the master nodeMasterInfo.getClusterNode(); 
         }
      }

      // Check for user supplied filters <master><filter>... These are the filter based queries
      AccessFilterQos[] filterQos = nodeMasterInfo.getAccessFilterArr();
      if (log.isLoggable(Level.FINE)) log.fine("Found " + filterQos.length + " global filter rules ...");
      for (int jj=0; jj<filterQos.length; jj++) {
         I_AccessFilter filter = glob.getRequestBroker().getAccessPluginManager().getAccessFilter(
                                       filterQos[jj].getType(),
                                       filterQos[jj].getVersion(), 
                                       msgUnit.getContentMime(),
                                       msgUnit.getContentMimeExtended());
         if (log.isLoggable(Level.FINE)) log.fine("Checking filter='" + filterQos[jj].getQuery() + "' on message content='" +
                        msgUnit.getContentStr() + "'");
         SessionInfo sessionInfo = null; // TODO: Pass sessionInfo here
         if (filter != null && filter.match(sessionInfo, msgUnit, filterQos[jj].getQuery())) {
            if (log.isLoggable(Level.FINE)) log.fine("Found master='" + nodeMasterInfo.getNodeId().getId() + "' stratum=" + nodeMasterInfo.getStratum() +
                           " for message '" + msgUnit.getLogId() + "' with filter='" + filterQos[jj].getQuery() + "'.");
            return nodeMasterInfo; // Found the master nodeMasterInfo.getClusterNode(); 
         }
      }

      if (log.isLoggable(Level.FINE)) log.fine("Node '" + nodeMasterInfo.getId() + "' is not master for message '" +
                     msgUnit.getKeyData().toXml() + "' with given rules=" + nodeMasterInfo.toXml());
      // Another rule can still choose this node as a master

      return null; // This clusternode is not the master
   }

   public void shutdown() throws XmlBlasterException {
   }

}
