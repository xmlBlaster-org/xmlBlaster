/*------------------------------------------------------------------------------
Name:      I_XmlBlasterCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The client callback server interface.
Version:   $Id: I_XmlBlasterCallback.java,v 1.2 2000/06/25 18:32:43 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.xmlBlaster.engine.helper.MessageUnit;
import java.rmi.RemoteException;

/**
 * The client RMI callback server interface which your client needs to implement.
 * <p />
 * You need to register this RMI callback server with rmi-registry and
 * pass this name as the callback address of your subscribe()-QoS.
 * @author ruff@swand.lake.de
 */
public interface I_XmlBlasterCallback extends java.rmi.Remote
{
   public void update(org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr)
                      throws RemoteException;
}

