/*------------------------------------------------------------------------------
Name:      I_InvocationRecorder.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface
Version:   $Id: I_InvocationRecorder.java,v 1.2 2000/06/25 18:32:43 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 * Interface for InvocationRecorder, the supported methods.
 * <p />
 * @see InvocationRecorder
 */
public interface I_InvocationRecorder
{
   public java.lang.String subscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
   public org.xmlBlaster.engine.helper.MessageUnit[] get(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
   public void unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
   public java.lang.String publish(org.xmlBlaster.engine.helper.MessageUnit msgUnit) throws XmlBlasterException;
   public java.lang.String[] publishArr(org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr) throws XmlBlasterException;
   public java.lang.String[] erase(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException;
}
