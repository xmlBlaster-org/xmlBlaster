/*------------------------------------------------------------------------------
Name:      AuthenticationInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the authentication data
Version:   $Id: AuthenticationInfo.java,v 1.10 2000/06/05 11:39:20 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.CallbackAddress;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;


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
   private ClientQoS clientQoS;
   private String callbackAddr=null;


   /**
    * This Object is constructed by the client login call
    *
    * @param sessionId   The POA active object map id (AOM)
    * @param loginName   The unique login name of the client
    * @param passwd      Very secret
    * @param clientQoS   The login quality of service
    */
   public AuthenticationInfo(String sessionId, String loginName, String passwd,
                             ClientQoS clientQoS)
   {
      this.sessionId = sessionId;
      this.loginName = loginName;
      this.passwd = passwd;
      this.clientQoS = clientQoS;
      if (Log.CALLS) Log.trace(ME, "Creating new AuthenticationInfo " + loginName);
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
      return clientQoS.getCallbackAddresses();
   }


   /**
    * This is the unique identifier of the client.
    * <p />
    * It is currently the byte[] oid from the POA active object map.
    * @return oid
    */
   public final String getUniqueKey() throws XmlBlasterException
   {
      return sessionId;
   }


   /**
    * Access the unique login name of a client.
    * @return loginName
    */
   public final String getLoginName()
   {
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

