/*------------------------------------------------------------------------------
Name:      I_Authenticate.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: I_Authenticate.java,v 1.2 2002/11/26 12:39:40 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;


/**
 * This is the interface of authentication methods to the delivery layer. 
 * <p />
 * @see org.xmlBlaster.authentication.Authenticate
 */
public interface I_Authenticate extends org.xmlBlaster.protocol.I_Authenticate
{
}


