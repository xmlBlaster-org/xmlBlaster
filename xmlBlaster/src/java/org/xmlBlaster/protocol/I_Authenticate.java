/*------------------------------------------------------------------------------
Name:      I_Authenticate.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: I_Authenticate.java,v 1.1 2001/09/04 11:51:50 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.ClientQoS;
import org.xmlBlaster.engine.xml2java.LoginReturnQoS;


/**
 * This is the native interface to xmlBlaster-authentication.
 * <p />
 * All login/logout or connect/disconnect calls access xmlBlaster's
 * authentication plugins through these methods.
 * This interface is implemented by authentication/Authenticate.java
 * @see xmlBlaster.idl
 * @see org.xmlBlaster.authentication.Authenticate
 * @author ruff@swand.lake.de
 */
public interface I_Authenticate
{
   public LoginReturnQoS connect(ClientQoS qos) throws XmlBlasterException;
   public LoginReturnQoS connect(ClientQoS qos, String sessionId) throws XmlBlasterException;
   public void disconnect(String sessionId, String qos_literal) throws XmlBlasterException;
}


