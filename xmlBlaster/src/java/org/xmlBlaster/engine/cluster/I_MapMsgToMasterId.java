/*------------------------------------------------------------------------------
Name:      I_MapMsgToMasterId.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface top the load balancing implementation
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.MessageUnitWrapper;

/**
 * Interface to implementation which finds the master cluster node of a given message. 
 * @author ruff@swand.lake.de
 */
public interface I_MapMsgToMasterId
{

   public NodeId getMasterId(MessageUnitWrapper msgWrapper) throws XmlBlasterException;
}
