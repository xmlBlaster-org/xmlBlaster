/*------------------------------------------------------------------------------
Name:      I_CallbackServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: I_CallbackServer.java,v 1.2 2002/03/13 16:41:08 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.xmlBlaster.util.XmlBlasterException;


/**
 * This is the client callback interface to xmlBlaster.
 * <p />
 * All callback protocol drivers are accessed through these methods.
 * We need it to decouple the protocol specific stuff
 * (like RemoteException from RMI or CORBA exceptions) from
 * our java client code.
 * <p />
 * Note that you don't need this code, you can access xmlBlaster
 * with your own lowlevel RMI or CORBA coding as well.
 *
 * @see org.xmlBlaster.client.protocol.I_XmlBlasterConnection
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public interface I_CallbackServer
{
   public boolean shutdownCb() throws XmlBlasterException;
   public void initCb() throws XmlBlasterException;
   public void setCbSessionId(String sessionId) throws XmlBlasterException;
}

