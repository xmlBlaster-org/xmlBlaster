/*------------------------------------------------------------------------------
Name:      I_LoadBalancer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface top the load balancing implementation
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;

import java.util.Set;

/**
 * Interface to the load balancing implementation. 
 * <p />
 * See http://www.ddj.com/documents/s=921/ddj9804i/9804i.htm
 * @author ruff@swand.lake.de
 */
public interface I_LoadBalancer {
   /**
    * Your plugin should determine which xmlBlaster node to choose. 
    * <p />
    * You can access the necessary informations like this:
    * <pre>
    *  Iterator it = clusterNodeSet.iterator();
    *  while (it.hasNext()) {
    *     NodeDomainInfo nodeDomainInfo = (NodeDomainInfo)it.Next();
    *
    *     ... // Your load balancing code
    *
    *     // Return the clusterNode if nodeDomainInfo is OK to handle the message:
    *     ClusterNode clusterNode = nodeDomainInfo.getClusterNode();
    *     return clusterNode;
    *  }
    * </pre>
    * This corresponds to the following XML configuration:
    * <pre>
    *  &lt;!-- NodeDomainInfo.java contains the parsed: -->
    *  &lt;master type='DomainToMaster' stratum='0'>
    *     &lt;key domain='RUGBY'/>
    *     &lt;key type='XPATH'>//STOCK&lt;/key>
    *  &lt;/master>
    * </pre>
    * @param nodeDomainInfoSet A set containing NodeDomainInfo objects, the possible xmlBlaster nodes.
    *                       Is never null, but may have a size of 0.
    *  The set i guaranteed to be sorted after<br />
    *  <pre>
    *   "available:stratum:nodeId"
    *
    *   available := The connection state is: 0 connected, 1 polling, 2 unavailable
    *   stratum   := 0 master, 1 slave, 2 slaveOfSlave ...
    *   nodeId    := a unique counter (nodeDomainInfo.getCount())
    *  </pre>
    * @return The chosen clusterNode to handle the message
    */
   public ClusterNode getClusterNode(Set nodeDomainInfoSet) throws XmlBlasterException;
}
