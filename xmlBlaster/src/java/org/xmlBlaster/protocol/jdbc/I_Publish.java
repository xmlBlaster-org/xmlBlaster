/*------------------------------------------------------------------------------
Name:      I_Publish.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface
Version:   $Id: I_Publish.java,v 1.3 2002/12/18 12:39:09 ruff Exp $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.jdbc;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;

/**
 * Callback interface for Publish. 
 */
public interface I_Publish
{
   public java.lang.String publish(org.xmlBlaster.util.MsgUnitRaw msgUnit) throws XmlBlasterException;
}
