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

/**
 * Interface to the load balancing implementation. 
 * @author ruff@swand.lake.de
 */
public interface I_LoadBalancer {

   public XmlBlasterConnection getConnection(NodeInfo[] nodeInfoArr) throws XmlBlasterException;
}
