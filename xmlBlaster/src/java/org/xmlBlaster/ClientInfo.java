/*------------------------------------------------------------------------------
Name:      ClientInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling the Client data
           $Revision: 1.1 $  $Date: 1999/11/08 14:32:54 $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.xmlBlaster.util.*;


/**
 * ClientInfo
 */
public class ClientInfo
{
   private String ME = "ClientInfo";
   private String uniqueKey;
   private String callbackIOR;
   private BlasterCallback callback=null;
   private RequestBroker requestBroker;

   public ClientInfo(RequestBroker requestBroker, String uniqueKey, String callbackIOR)
   {
      if (Log.CALLS) Log.trace(ME, "Creating new ClientInfo " + uniqueKey);
      this.requestBroker = requestBroker;
      this.uniqueKey = uniqueKey;
      this.callbackIOR = callbackIOR;
   }

   public final BlasterCallback getCB()
   {
      if (this.callback == null)
         callback = requestBroker.getBlasterCallback(callbackIOR);
      return callback;
   }

   String getUniqueKey()
   {
      return uniqueKey;
   }


   String getCallbackIOR()
   {
      return callbackIOR;
   }
}
