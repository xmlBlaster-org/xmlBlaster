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
    * @param clusterNodeSet A set containing ClusterNode objects, the possible xmlBlaster nodes.
    *                       Is never null, but may have a size of 0.
    * @return The chosen clusterNode to handle the message
    */
   public ClusterNode getClusterNode(Set clusterNodeSet) throws XmlBlasterException;
}
