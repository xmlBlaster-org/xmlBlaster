/*------------------------------------------------------------------------------
Name:      ClientInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling the Client data
           $Revision: 1.2 $  $Date: 1999/11/08 22:40:59 $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.xmlBlaster.util.*;


/**
 * ClientInfo
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

   public String getUniqueKey()
   {
      return xmlKey.getUniqueKey();
   }


   public String getCallbackIOR() throws XmlBlasterException
   {
      return xmlQoS.getCallbackIOR();
   }
}
