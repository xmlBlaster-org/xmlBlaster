/*------------------------------------------------------------------------------
Name:      I_InvocationRecorder.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface
Version:   $Id: I_InvocationRecorder.java,v 1.1 2000/06/13 13:04:52 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * Interface for InvocationRecorder, the supported methods.
 * <p />
 * @see InvocationRecorder
 */
public interface I_InvocationRecorder
{
   public java.lang.String subscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
   public org.xmlBlaster.protocol.corba.serverIdl.MessageUnitContainer[] get(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
   public void unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
   public java.lang.String publish(org.xmlBlaster.protocol.corba.serverIdl.MessageUnit msgUnit, java.lang.String qos) throws XmlBlasterException;
   public java.lang.String[] publishArr(org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] msgUnitArr, java.lang.String[] qosArr) throws XmlBlasterException;
   public java.lang.String[] erase(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
   public void setClientAttributes(java.lang.String clientName, java.lang.String xmlAttr, java.lang.String qos) throws XmlBlasterException;
}
