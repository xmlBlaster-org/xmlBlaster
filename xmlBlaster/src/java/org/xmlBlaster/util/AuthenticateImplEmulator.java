/*------------------------------------------------------------------------------
Name:      AuthenticateImplEmulator.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: AuthenticateImplEmulator.java,v 1.1 2002/05/22 17:20:11 laghi Exp $
Author:    laghi@swissinfo.org
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.util.Log;

/**
 * Used for testing the plugin communication drivers
 * @author laghi@swissinfo.org
 */
public class AuthenticateImplEmulator implements I_Authenticate
{

   private final static String ME = "AuthenticateImplEmulator";

   public AuthenticateImplEmulator ()
   {
   }

   public ConnectReturnQos connect(ConnectQos qos) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "connect: " + qos);
      return new ConnectReturnQos(qos);
   }

   public ConnectReturnQos connect(ConnectQos qos, String sessionId) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "connect: " + qos + " SessionId: " + sessionId);
      return new ConnectReturnQos(qos);
   }

   public void disconnect(String sessionId, String qos_literal) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "disconnect: sessionId " + sessionId  + " " + qos_literal);
   }
}


