/*------------------------------------------------------------------------------
Name:      I_Publish.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface
Version:   $Id: I_Publish.java,v 1.2 2002/05/19 12:55:50 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.jdbc;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 * Callback interface for Publish. 
 */
public interface I_Publish
{
   public java.lang.String publish(org.xmlBlaster.engine.helper.MessageUnit msgUnit) throws XmlBlasterException;
}
