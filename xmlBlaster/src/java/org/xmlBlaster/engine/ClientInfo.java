/*------------------------------------------------------------------------------
Name:      ClientInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: ClientInfo.java,v 1.9 1999/11/22 16:12:21 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.authentication.XmlQoSClient;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.clientIdl.BlasterCallback;


/**
 * ClientInfo stores all known data about a client
 *
 * @version $Revision: 1.9 $
 * @author $Name:  $
 */
public class ClientInfo
{
   private String ME = "ClientInfo";
   private String uniqueKey;
   private String loginName;
   private String passwd;
   private XmlQoSClient xmlQoS;
   private BlasterCallback callback=null;
   private String callbackIOR;

   public ClientInfo(String uniqueKey, String loginName, String passwd,
                       BlasterCallback callback,
                       String callbackIOR, XmlQoSClient xmlQoS)
   {
      this.uniqueKey = uniqueKey;
      this.loginName = loginName;
      this.passwd = passwd;
      this.callback = callback;
      this.callbackIOR = callbackIOR;
      this.xmlQoS = xmlQoS;
      if (Log.CALLS) Log.trace(ME, "Creating new ClientInfo " + loginName);
   }

   public final BlasterCallback getCB() throws XmlBlasterException
   {
      if (this.callback == null) {
         throw new XmlBlasterException(ME+"NoCallback", "Sorry, no Callback for " + loginName);
      }
      return callback;
   }


   /**
    * This is the unique identifier of the client
    * it is currently the byte[] oid from the POA active object map.
    * @return oid
    */
   public String getUniqueKey() throws XmlBlasterException
   {
      return uniqueKey;
   }


   /**
    * @return the uniqueKey in hex notation for dumping it (readable form)
    */
   public String getUniqueKeyHex() throws XmlBlasterException
   {
      return jacorb.poa.util.POAUtil.convert(getUniqueKey().getBytes(), true);
   }


   /**
    */
   public String toString()
   {
      return loginName;
   }


   public String getCallbackIOR() throws XmlBlasterException
   {
      if (this.callbackIOR == null) {
         throw new XmlBlasterException(ME+"NoCallback", "Sorry, no CallbackIOR for " + loginName);
      }
      return callbackIOR;
   }
}
