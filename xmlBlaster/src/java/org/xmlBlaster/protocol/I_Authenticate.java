/*------------------------------------------------------------------------------
Name:      I_Authenticate.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: I_Authenticate.java,v 1.4 2002/02/14 19:05:24 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;


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
   /*
      I don't know which is the better way
      1) passing raw strings as QoS
         + The driver doesn't need to parse
      2) passing QoS objects
         + The Socket driver needs the object to extract the sessionId
         - The drivers have a reference to the internal object (should we copy it)?
         - Every driver needs to know how to parse
   */

   public String connect(String qos_literal) throws XmlBlasterException;
   public ConnectReturnQos connect(ConnectQos qos) throws XmlBlasterException;
   /*
    * I_CallbackDriver is an optional argument to use for callbacks instead
    * of instantiating one via xmlBlaster.properties
    */
   //public ConnectReturnQos connect(ConnectQos qos, I_CallbackDriver cbDriver) throws XmlBlasterException;

   public String connect(String qos_literal, String sessionId) throws XmlBlasterException;
   public ConnectReturnQos connect(ConnectQos qos, String sessionId) throws XmlBlasterException;

   public String disconnect(String sessionId, String qos_literal) throws XmlBlasterException;
}


