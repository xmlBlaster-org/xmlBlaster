/*------------------------------------------------------------------------------
Name:      I_Callback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;

/**
 * Interface for Callback, the supported methods to call back to client side.
 * <p />
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 */
public interface I_Callback
{
   public PublishReturnQos update(org.xmlBlaster.engine.helper.MessageUnit msgUnit) throws XmlBlasterException;

   public void updateOneway(org.xmlBlaster.engine.helper.MessageUnit [] msgUnitArr) throws XmlBlasterException;

   public String ping(String qos) throws XmlBlasterException;
}
