/*------------------------------------------------------------------------------
Name:      DomainToMaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Simple demo implementation for clustering
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster.simpledomain;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.cluster.ClusterManager;
import org.xmlBlaster.engine.cluster.ClusterNode;
import org.xmlBlaster.engine.cluster.NodeDomainInfo;
import org.xmlBlaster.engine.cluster.I_MapMsgToMasterId;

import java.util.Iterator;

/**
 * Finds the master of a message depending
 * on the <i>domain</i> attribute of the message key tag.
 * <p />
 * This is a simple demo implementation for clustering, the plugin
 * can be loaded depending on the mime type of a message, we
 * register it here for all messages, see getMessages().
 * <p />
 * @author ruff@swand.lake.de 
 * @since 0.79e
 */
final public class DomainToMaster implements I_Plugin, I_MapMsgToMasterId {
   private final String ME = "DomainToMaster";
   private Global glob;
   private Log log;
   private ClusterManager clusterManager;

   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(Global glob, ClusterManager clusterManager) {
      this.glob = glob;
      this.log = glob.getLog();
      this.clusterManager = clusterManager;
      log.info(ME, "The simple domain based master mapper plugin is initialized");
   }

   /**
    * Is called when new configuration arrived, notify the plugin to empty its
    * cache or do whatever it needs to do. 
    */
   public void reset() {
      Log.warn(ME, "New configuration, nothing to do");
   }

   /**
    * This method is called by the PluginManager.
    * <p />
    * This xmlBlaster.properties entry example
    * <pre>
    *   MapMsgToMasterPlugin[DomainToMaster][1.0]=org.xmlBlaster.engine.cluster.simpledomain.DomainToMaster,DEFAULT_DOMAIN=dummy
    * </pre>
    * passes 
    * <pre>
    *   options[0]="DEFAULT_DOMAIN"
    *   options[1]="dummy"
    * </pre>
    * <p/>
    * @param Global   An xmlBlaster instance global object holding logging and property informations
    * @param String[] Some arguments from xmlBlaster.properties.
    */
   public void init(org.xmlBlaster.util.Global glob, String[] options) throws XmlBlasterException {
      if (options != null) {
         for (int ii=0; ii<options.length-1; ii++) {
            if (options[ii].equalsIgnoreCase("DEFAULT_DOMAIN")) {
               // ... do something
            }
         }
      }
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
      String[] mimeExtended = { org.xmlBlaster.util.XmlKeyBase.DEFAULT_contentMimeExtended }; // "1.0"
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
    * @param msgWrapper The message
    * @return The node which is master of the message, you should always return a valid ClusterNode
    */
   public ClusterNode getMasterId(NodeDomainInfo nodeDomainInfo, MessageUnitWrapper msgWrapper) throws XmlBlasterException {

      if (msgWrapper.getXmlKey().isDefaultDomain()) {
         if (nodeDomainInfo.getClusterNode().isLocalNode()) {
            if (nodeDomainInfo.getAcceptDefault()==true) {
               // if no domain is specified and the local node accepts default messages -> local node is master
               if (log.TRACE) log.trace(ME, "Message oid='" + msgWrapper.getUniqueKey() + "' domain='" + msgWrapper.getXmlKey().getDomain() + "' is handled by local node");
               return nodeDomainInfo.getClusterNode(); // Found the master
            }
         }
         else {
            if (nodeDomainInfo.getAcceptOtherDefault()==true) {
               log.info(ME, "Found master='" + nodeDomainInfo.getNodeId().getId() + "' for message oid='" + msgWrapper.getUniqueKey() + "' which accepts other default domains");
               return nodeDomainInfo.getClusterNode(); // Found the master
            }
         }
      }

      XmlKey preparedQuery = (XmlKey)nodeDomainInfo.getPreparedQuery();

      if (preparedQuery == null) { // The first time we need to parse the query string, and cache it
         String query = nodeDomainInfo.getQuery().trim();
         if (log.TRACE) log.trace(ME, "Parsing user supplied domain to master mapping query='" + query + "'");
         try {
            preparedQuery = new XmlKey(glob, query);
         } catch(Throwable e) {
            log.warn(ME, "Parsing user supplied domain to master mapping query='" + query + "' failed, we ignore it: " + e.toString());
            return null;
         }
         nodeDomainInfo.setPreparedQuery(preparedQuery);
      }

      // Now check if we are master
      if (preparedQuery.getDomain().equals(msgWrapper.getXmlKey().getDomain())) {
         log.info(ME, "Found master='" + nodeDomainInfo.getNodeId().getId() + "' for message oid='" + msgWrapper.getUniqueKey() + "' domain='" + msgWrapper.getXmlKey().getDomain() + "'.");
         return nodeDomainInfo.getClusterNode(); // Found the master
      }

      log.info(ME, "Node '" + nodeDomainInfo.getId() + "' is not master for message oid='" + msgWrapper.getUniqueKey() + "' domain='" + msgWrapper.getXmlKey().getDomain() + "'");
      return null; // This clusternode is not the master
   }
}
