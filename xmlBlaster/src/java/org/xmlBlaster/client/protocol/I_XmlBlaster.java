/*------------------------------------------------------------------------------
Name:      I_XmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface
Version:   $Id: I_XmlBlaster.java,v 1.6 2002/11/26 12:38:06 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;

/**
 * Interface for XmlBlaster, the supported methods on java client side.
 * <p />
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 */
public interface I_XmlBlaster
{
   public SubscribeReturnQos subscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;

   public org.xmlBlaster.engine.helper.MessageUnit[] get(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;

   public UnSubscribeReturnQos[] unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;

   public PublishReturnQos publish(org.xmlBlaster.engine.helper.MessageUnit msgUnit) throws XmlBlasterException;

   public void publishOneway(org.xmlBlaster.engine.helper.MessageUnit [] msgUnitArr) throws XmlBlasterException;

   public PublishReturnQos[] publishArr(org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr) throws XmlBlasterException;

   public EraseReturnQos[] erase(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
}
