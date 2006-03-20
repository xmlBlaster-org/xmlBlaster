/*------------------------------------------------------------------------------
Name:      I_XmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;

/**
 * Interface for XmlBlaster, the supported methods on java client side.
 * <p />
 * This allows string access, another interface allows object based access.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 * @author xmlBlaster@marcelruff.info
 */
public interface I_XmlBlaster
{
   public SubscribeReturnQos subscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;

   public org.xmlBlaster.util.MsgUnit[] get(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;

   public UnSubscribeReturnQos[] unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;

   public PublishReturnQos publish(org.xmlBlaster.util.MsgUnit msgUnit) throws XmlBlasterException;

   public void publishOneway(org.xmlBlaster.util.MsgUnit [] msgUnitArr) throws XmlBlasterException;

   public PublishReturnQos[] publishArr(org.xmlBlaster.util.MsgUnit[] msgUnitArr) throws XmlBlasterException;

   public EraseReturnQos[] erase(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
}
