/*------------------------------------------------------------------------------
Name:      ClientInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling the Client data
           $Revision: 1.2 $  $Date: 1999/11/11 16:15:00 $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.clientIdl.BlasterCallback;


/**
 * ClientInfo stores all known data about a client
 */
public class ClientInfo
{
   private String ME = "ClientInfo";
   private XmlKey xmlKey;
   private XmlQoS xmlQoS;
   private BlasterCallback callback=null;
   private RequestBroker requestBroker;

   public ClientInfo(RequestBroker requestBroker, XmlKey xmlKey, XmlQoS xmlQoS)
   {
      this.requestBroker = requestBroker;
      this.xmlKey = xmlKey;
      this.xmlQoS = xmlQoS;
      if (Log.CALLS) Log.trace(ME, "Creating new ClientInfo " + xmlKey.getUniqueKey());
   }

   public final BlasterCallback getCB() throws XmlBlasterException
   {
      if (this.callback == null)
         callback = requestBroker.getBlasterCallback(getCallbackIOR());
      return callback;
   }


   /**
    * The Callback IOR is a unique identifier for the client
    */
   public String getUniqueKey() throws XmlBlasterException
   {
      // return xmlKey.getUniqueKey();
      return getCallbackIOR();
   }


   public String getCallbackIOR() throws XmlBlasterException
   {
      return xmlQoS.getCallbackIOR();
   }
}
