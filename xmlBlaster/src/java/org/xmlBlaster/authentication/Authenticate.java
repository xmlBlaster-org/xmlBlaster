/*------------------------------------------------------------------------------
Name:      Authenticate.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling the Client data
           $Revision: 1.1 $
           $Date: 1999/11/13 17:16:05 $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.serverIdl.ServerImpl;
import org.xmlBlaster.serverIdl.MessageUnit;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.clientIdl.BlasterCallback;
import java.util.*;


/**
 * Authenticate
 */
public class Authenticate
{
   final private String ME = "Authenticate";

   private static Authenticate authenticate = null; // Singleton pattern

   private ServerImpl serverImpl;

   final private Map clientInfoMap = Collections.synchronizedMap(new HashMap());


   public static Authenticate getInstance(ServerImpl serverImpl)
   {
      synchronized (Authenticate.class)
      {
         if (authenticate == null)
            authenticate = new Authenticate(serverImpl);
      }
      return authenticate;
   }


   /**
    * private Constructor for Singleton Pattern
    */
   private Authenticate(ServerImpl serverImpl)
   {
      this.serverImpl = serverImpl;
   }


   /**
    * Authentication of a client
    * @param cb The Callback interface of the client
    * @return a unique sessionId for the client, to be used in following calls
    */
   public String login(String loginName, String passwd,
                       BlasterCallback callback,
                       String xmlQoS_literal, String callbackIOR) throws XmlBlasterException
   {
      String sessionId = callbackIOR;  // !!! needs to be generated
      XmlQoSClient xmlQoS = new XmlQoSClient(xmlQoS_literal);
      ClientInfo clientInfo = new ClientInfo(sessionId, loginName, passwd, callback, callbackIOR, xmlQoS);
      synchronized(clientInfoMap) {
         clientInfoMap.put(clientInfo.getUniqueKey(), clientInfo);
      }
      return sessionId;
   }


   /**
    * Logout of a client
    * @param the unique sessionId for the client
    */
   public void logout(String sessionId) throws XmlBlasterException
   {
      synchronized(clientInfoMap) {
         Object obj = clientInfoMap.remove(sessionId);
         if (obj == null) {
            throw new XmlBlasterException(ME+"Unknown", "Sorry, you are not known, no logout");
         }
         obj = null;
      }
   }


   public ClientInfo check(String sessionId) throws XmlBlasterException
   {
      synchronized(clientInfoMap) {
         Object obj = clientInfoMap.get(sessionId);
         if (obj == null) {
            throw new XmlBlasterException(ME+"AccessDenied", "Sorry, sessionId is invalid");
         }
         return (ClientInfo)obj;
      }
   }
}
