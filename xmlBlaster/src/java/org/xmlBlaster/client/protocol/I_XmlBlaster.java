/*------------------------------------------------------------------------------
Name:      I_XmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface
Version:   $Id: I_XmlBlaster.java,v 1.3 2002/05/30 09:56:24 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 * Interface for XmlBlaster, the supported methods on java client side.
 * <p />
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 */
public interface I_XmlBlaster
{
   public java.lang.String subscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;

   public org.xmlBlaster.engine.helper.MessageUnit[] get(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;

   public void unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;

   public java.lang.String publish(org.xmlBlaster.engine.helper.MessageUnit msgUnit) throws XmlBlasterException;

   public void publishOneway(org.xmlBlaster.engine.helper.MessageUnit [] msgUnitArr) throws XmlBlasterException;

   public java.lang.String[] publishArr(org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr) throws XmlBlasterException;

   public java.lang.String[] erase(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
}
