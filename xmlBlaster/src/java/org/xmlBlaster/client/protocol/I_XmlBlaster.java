/*------------------------------------------------------------------------------
Name:      I_XmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface
Version:   $Id: I_XmlBlaster.java,v 1.5 2002/06/03 09:36:18 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.client.SubscribeRetQos;
import org.xmlBlaster.client.PublishRetQos;
import org.xmlBlaster.client.EraseRetQos;

/**
 * Interface for XmlBlaster, the supported methods on java client side.
 * <p />
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 */
public interface I_XmlBlaster
{
   public SubscribeRetQos subscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;

   public org.xmlBlaster.engine.helper.MessageUnit[] get(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;

   public void unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;

   public PublishRetQos publish(org.xmlBlaster.engine.helper.MessageUnit msgUnit) throws XmlBlasterException;

   public void publishOneway(org.xmlBlaster.engine.helper.MessageUnit [] msgUnitArr) throws XmlBlasterException;

   public PublishRetQos[] publishArr(org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr) throws XmlBlasterException;

   public EraseRetQos[] erase(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
}
