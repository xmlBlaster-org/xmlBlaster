/*------------------------------------------------------------------------------
Name:      AuthenticationInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the authentication data
Version:   $Id: AuthenticationInfo.java,v 1.17 2002/02/14 22:57:36 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * AuthenticationInfo stores all known login data about a client
 *
 * @author ruff@swand.lake.de
 */
public class AuthenticationInfo
{
   private String ME = "AuthenticationInfo";
   private String sessionId;
   private String loginName;
   private String passwd;
   private ConnectQos connectQos;
   private String callbackAddr=null;


   /**
    * This Object is constructed by the client login call
    *
    * @param sessionId   The POA active object map id (AOM)
    * @param loginName   The unique login name of the client
    * @param passwd      Very secret
    * @param connectQos   The login quality of service
    */
   public AuthenticationInfo(String sessionId, String loginName, String passwd,
                             ConnectQos connectQos)
   {
      this.sessionId = sessionId;
      this.loginName = loginName;
      this.passwd = passwd;
      this.connectQos = connectQos;
      if (Log.CALL) Log.trace(ME, "Creating new AuthenticationInfo " + loginName);
   }


   /**
    * Accessing the Callback addresses of the client
    * this may be a CORBA-IOR or email or URL ...
    * <p />
    * @return An array of CallbackAddress objects, containing the address and the protocol type
    *         If no callback available, return an array of 0 length
    */
   public final CallbackAddress[] getCallbackAddresses() throws XmlBlasterException
   {
      return connectQos.getCallbackAddresses();
   }

   /**
    * The native callback driver as passed from the socket driver
    */
   public final I_CallbackDriver getCallbackDriver()
   {
      return connectQos.getCallbackDriver();
   }


   /**
    * This is the unique identifier of the client.
    * <p />
    * It is currently the byte[] oid from the POA active object map.
    * @return oid
    */
   public final String getUniqueKey()
   {
      return sessionId;
   }


   /**
    * Access the unique login name of a client.
    * <br /> 
    * If not known, its unique key (sessionId) is delivered
    * @return loginName
    * @todo The sessionId is a security risk, what else
    *        can we use? !!!
    */
   public final String getLoginName()
   {
      if (loginName == null)
         return getUniqueKey();
      return loginName;
   }


   /**
    * Access the unique login name of a client.
    * @return loginName
    */
   public final String toString()
   {
      return getLoginName();
   }

}

