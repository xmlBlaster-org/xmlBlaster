/*------------------------------------------------------------------------------
Name:      AuthenticationInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the authentication data
Version:   $Id: AuthenticationInfo.java,v 1.8 2000/05/16 20:57:35 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.CallbackAddress;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;


/**
 * AuthenticationInfo stores all known login data about a client
 *
 * @author ruff@swand.lake.de
 */
public class AuthenticationInfo
{
   private String ME = "AuthenticationInfo";
   private String uniqueKey;
   private String loginName;
   private String passwd;
   private org.xmlBlaster.protocol.corba.serverIdl.Server xmlBlaster;
   private ClientQoS clientQoS;
   private String callbackAddr=null;


   /**
    * This Object is constructed by the client login call
    *
    * @param uniqueKey   The POA active object map id (AOM)
    * @param loginName   The unique login name of the client
    * @param passwd      Very secret
    * @param xmlBlaster  The server serving this client
    * @param clientQoS   The login quality of service
    */
   public AuthenticationInfo(String uniqueKey, String loginName, String passwd,
                       org.xmlBlaster.protocol.corba.serverIdl.Server xmlBlaster,
                       ClientQoS clientQoS)
   {
      this.uniqueKey = uniqueKey;
      this.loginName = loginName;
      this.passwd = passwd;
      this.xmlBlaster = xmlBlaster;
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
    * The CORBA xmlBlaster server reference serving this client.
    * @return Server reference
    */
   org.xmlBlaster.protocol.corba.serverIdl.Server getXmlBlaster() throws XmlBlasterException
   {
      if (this.xmlBlaster == null) {
         Log.error(ME+"NoCallback", "Sorry, no xmlBlaster Server for " + loginName);
         throw new XmlBlasterException(ME+"NoCallback", "Sorry, no xmlBlaster Server for " + loginName);
      }
      return xmlBlaster;
   }


   /**
    * This is the unique identifier of the client.
    * <p />
    * It is currently the byte[] oid from the POA active object map.
    * @return oid
    */
   public final String getUniqueKey() throws XmlBlasterException
   {
      return uniqueKey;
   }


   /**
    * The unique Client ID in HEX format, to be able to dump it
    * @return the uniqueKey in hex notation for dumping it (readable form)
    */
   public final String getUniqueKeyHex() throws XmlBlasterException
   {
      return jacorb.poa.util.POAUtil.convert(getUniqueKey().getBytes(), true);
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

