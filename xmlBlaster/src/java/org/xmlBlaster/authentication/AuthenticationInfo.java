/*------------------------------------------------------------------------------
Name:      AuthenticationInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the authentication data
Version:   $Id: AuthenticationInfo.java,v 1.3 1999/12/01 22:17:28 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.clientIdl.BlasterCallback;


/**
 * AuthenticationInfo stores all known login data about a client
 *
 * @version$
 * @author$
 */
public class AuthenticationInfo
{
   private String ME = "AuthenticationInfo";
   private String uniqueKey;
   private String loginName;
   private String passwd;
   private XmlQoSClient xmlQoS;
   private BlasterCallback callback=null;
   private String callbackIOR;


   /**
    * This Object is constructed by the client login call
    */
   public AuthenticationInfo(String uniqueKey, String loginName, String passwd,
                       BlasterCallback callback,
                       String callbackIOR, XmlQoSClient xmlQoS)
   {
      this.uniqueKey = uniqueKey;
      this.loginName = loginName;
      this.passwd = passwd;
      this.callback = callback;
      this.callbackIOR = callbackIOR;
      this.xmlQoS = xmlQoS;
      if (Log.CALLS) Log.trace(ME, "Creating new AuthenticationInfo " + loginName);
   }

   /**
    * The CORBA callback reference of the client.
    * @return BlasterCallback The client callback implementation
    */
   public final BlasterCallback getCB() throws XmlBlasterException
   {
      if (this.callback == null) {
         Log.error(ME+"NoCallback", "Sorry, no Callback for " + loginName);
         throw new XmlBlasterException(ME+"NoCallback", "Sorry, no Callback for " + loginName);
      }
      return callback;
   }


   /**
    * This is the unique identifier of the client
    * it is currently the byte[] oid from the POA active object map.
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
    * email callbacks are not yet supported
    * @return false
    */
   public final boolean useEmailCB()
   {
      // !!! TODO: inspect QoS which callback type is wanted from the client
      return false;
   }


   /**
    * Http callbacks are not yet supported
    * @return false
    */
   public final boolean useHttpCB()
   {
      // !!! TODO: inspect QoS which callback type is wanted from the client
      return false;
   }


   /**
    * Only CORBA callbacks are supported this version
    * @return false
    */
   public final boolean useCorbaCB()
   {
      // !!! TODO: inspect QoS which callback type is wanted from the client
      return true;
   }


   /**
    *
    */
   public final String toString()
   {
      return loginName;
   }


   public final String getCallbackIOR() throws XmlBlasterException
   {
      if (this.callbackIOR == null) {
         Log.error(ME+"NoCallback", "Sorry, no CallbackIOR for " + loginName);
         throw new XmlBlasterException(ME+"NoCallback", "Sorry, no CallbackIOR for " + loginName);
      }
      return callbackIOR;
   }
}
