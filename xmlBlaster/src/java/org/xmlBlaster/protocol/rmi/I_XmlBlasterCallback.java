/*------------------------------------------------------------------------------
Name:      I_XmlBlasterCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The client callback server interface.
Version:   $Id: I_XmlBlasterCallback.java,v 1.6 2002/03/18 00:29:36 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.util.XmlBlasterException;

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
   public String[] update(String cbSessionId, org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr)
                      throws RemoteException, XmlBlasterException;

   /**
    * The oneway variant for better performance. 
    * Does RMI implement a real oneway?
    */
   public void updateOneway(String cbSessionId, org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr)
                      throws RemoteException;

   /**
    * Ping to check if the xmlBlaster server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    */
   public String ping(String str) throws RemoteException;
}

