/*------------------------------------------------------------------------------
Name:      RequestBroker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling the Client data
           $Revision: 1.1 $
           $Date: 1999/11/08 14:32:54 $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import java.util.*;


/**
 * RequestBroker
 */
public class RequestBroker
{
   final private String ME = "RequestBroker";

   private static RequestBroker requestBroker = null; // Singleton pattern

   final private Map clientInfoMap = Collections.synchronizedMap(new HashMap());

   final private ServerImpl serverImpl;



   public static RequestBroker getInstance(ServerImpl serverImpl)
   {
      synchronized (RequestBroker.class)
      {
         if (requestBroker == null)
            requestBroker = new RequestBroker(serverImpl);
      }
      return requestBroker;
   }


   /**
    * private Constructor for Singleton Pattern
    */
   private RequestBroker(ServerImpl serverImpl)
   {
      this.serverImpl = serverImpl;
   }


   /**
    */
   public BlasterCallback getBlasterCallback(String callbackIOR)
   {
      return serverImpl.getBlasterCallback(callbackIOR);
   }


   /**
    */
   public ClientInfo getClientInfo(String xmlKey, String qos)
   {
      synchronized(clientInfoMap) {
         Object obj = clientInfoMap.get(xmlKey);
         if (obj == null) {
            String uniqueKey = xmlKey; // !!! SAX parser?
            String ior = xmlKey;
            ClientInfo cl = new ClientInfo(this, uniqueKey, ior);
            clientInfoMap.put(xmlKey, cl);
            return cl;
         }
         return (ClientInfo)obj;
      }
   }

   
   /**
    */
   public void subscribe(String xmlKey, String qos)
   {
      ClientInfo cl = getClientInfo(xmlKey, qos);

   }
}
