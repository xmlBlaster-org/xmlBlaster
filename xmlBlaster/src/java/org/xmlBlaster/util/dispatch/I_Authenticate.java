/*------------------------------------------------------------------------------
Name:      I_Authenticate.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: I_Authenticate.java,v 1.4 2003/01/05 23:01:44 ruff Exp $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;


/**
 * This is the interface of authentication methods to the delivery layer. 
 * <p />
 * @see org.xmlBlaster.authentication.Authenticate
 */
public interface I_Authenticate extends org.xmlBlaster.protocol.I_Authenticate
{
}


