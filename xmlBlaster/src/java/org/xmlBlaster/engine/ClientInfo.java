/*------------------------------------------------------------------------------
Name:      ClientInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: ClientInfo.java,v 1.7 1999/11/16 18:44:49 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.authentication.XmlQoSClient;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.clientIdl.BlasterCallback;


/**
 * ClientInfo stores all known data about a client
 *
 * @version $Revision: 1.7 $
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
    */
   public String getUniqueKey() throws XmlBlasterException
   {
      return uniqueKey;
   }


   /**
    */
   public String toString()
   {
      return loginName;
   }


   public String getCallbackIOR() throws XmlBlasterException
   {
      return callbackIOR;
   }
}
