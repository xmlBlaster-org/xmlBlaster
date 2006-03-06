/*------------------------------------------------------------------------------
Name:      I_MapMsgToMasterId.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface top the load balancing implementation
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.MsgUnit;

/**
 * Interface to implementation which finds the master cluster node of a given message. 
 * @author xmlBlaster@marcelruff.info
 */
public interface I_MapMsgToMasterId
{
   /**
    * This is called after instantiation of the plugin. 
    * We pass the engine.Global handle which has more knowledge then
    * the util.Global passed by I_Plugin (we could have down casted though).
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(ServerScope glob, ClusterManager clusterManager);

   /**
    * Is called when new configuration arrived, notify the plugin to empty its
    * cache or do whatever it needs to do. 
    */
   public void reset();

   /** Get a human readable name of the implementation */
   public String getName();

   /**
    * Get the content MIME types for which this plugin applies, "*" is for all mime types
    * @return The supported mime types, for example  return { "text/plain", "text/xml", "application/mytext" };
    */
   public String[] getMimeTypes();

   /**
    * Get the content MIME version number for which this plugin applies. The returned String array length must
    * be the same as this of getMimeTypes(), the index corresponds to the index of getMimeTypes().<br />
    * For example "stable" is the extended mime type of "application/mytext" (see getMimeTypes()).
    * @return E.g. a string array like  { "1.0", "1.3", "stable" }
    */
   public String[] getMimeExtended();

   /**
    * Find out who is the master of the provided message. 
    * <p />
    * Here you code your clustering logic.
    * @param nodeDomainInfo The mapping rule to find the master
    * @param msgUnit The message
    * @return The nodeDomainInfo (same as you passed as parameter) it this is a possible master
    *         or null if not suitable.<br />
    * You can access the master ClusterNode with <code>nodeDomainInfo.getClusterNode()</code> and the xmlBlasterConnection
    * to the master node with <code>nodeDomainInfo.getClusterNode().getXmlBlasterAccess()</code>
    */
   public NodeDomainInfo getMasterId(NodeDomainInfo nodeDomainInfo, MsgUnit msgUnit) throws XmlBlasterException;
}
