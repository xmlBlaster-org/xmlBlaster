/*------------------------------------------------------------------------------
Name:      Authenticate.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login for clients
Version:   $Id: Authenticate.java,v 1.29 2000/06/13 13:03:57 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.StopWatch;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.ClientInfo;
import java.util.*;



/**
 * Authenticate a client via login.
 * <p>
 * The login method serves as a factory for a xmlBlaster.Server Reference
 */
public class Authenticate
{
   final private static String ME = "Authenticate";

   /** Unique counter to generate IDs */
   private long counter = 1;

   /**
    * With this map you can find a client using a sessionId.
    *
    * key   = sessionId, the byte[] from the POA active object map (aom)
    * value = ClientInfo object, containing all data about a client
    */
   final private Map aomClientInfoMap = Collections.synchronizedMap(new HashMap());

   /**
    * With this map you can find a client using his login name.
    *
    * key   = loginName, the unique login name of a client
    * value = ClientInfo object, containing all data about a client
    */
   final private Map loginNameClientInfoMap = Collections.synchronizedMap(new HashMap());

   /**
    * For listeners who want to be informed about login/logout
    */
   final private Set clientListenerSet = Collections.synchronizedSet(new HashSet());



   /**
    */
   public Authenticate()
   {
      if (Log.CALLS) Log.calls(ME, "Entering constructor");
   }


   /**
    * Authentication of a client.
    * <p>
    * @param xmlQoS_literal
    *     <pre>
    *        &lt;client>
    *           &lt;compress type='gzip'>
    *              1000
    *           &lt;/compress>
    *           &lt;queue>
    *              &lt;size>
    *                 1000
    *              &lt;/size>
    *              &lt;timeout>
    *                 3600
    *              &lt;/timeout>
    *           &lt;/queue>
    *        &lt;/client>
    *     </pre>
    * @param sessionId The user session ID if generated outside, otherwise null
    * @return The sessionId on successful login
    * @exception XmlBlasterException Access denied
    */
   public String login(String loginName, String passwd,
                       String xmlQoS_literal, String sessionId)
                          throws XmlBlasterException
   {
      if (Log.DUMP) Log.dump(ME, "-------START-login()---------\n" + toXml().toString());

      ClientInfo clientInfo = getClientInfoByName(loginName);

      if (clientInfo != null && clientInfo.isLoggedIn()) {
         Log.warning(ME+".AlreadyLoggedIn", "Client " + loginName + " is already logged in. Your login session will be re-initialized.");
         resetClientInfo(clientInfo.getUniqueKey(), false);
         // allowing re-login: if the client crashed without proper logout, she should
         // be allowed to login again, so - first logout the last session (but keep messages in client queue)
         // We need to clean up clientInfo, usually the callback reference is another one, etc.
      }

      ClientQoS xmlQoS = new ClientQoS(xmlQoS_literal);

      // !=== CHECK PASSWORD HERE IN FUTURE VERSION ====!

      AuthenticationInfo authInfo = new AuthenticationInfo(sessionId, loginName, passwd, xmlQoS);

      if (clientInfo != null) {
         clientInfo.notifyAboutLogin(authInfo); // clientInfo object exists, maybe with a queue of messages
      }
      else {                               // login of yet unknown client
         clientInfo = new ClientInfo(authInfo);
         synchronized(loginNameClientInfoMap) {
            loginNameClientInfoMap.put(loginName, clientInfo);
         }
      }

      if (sessionId == null || sessionId.length() < 2) {
         sessionId = createSessionId(loginName);
      }

      synchronized(aomClientInfoMap) {
         aomClientInfoMap.put(sessionId, clientInfo);
      }

      fireClientEvent(clientInfo, true);

      if (Log.DUMP) Log.dump(ME, "-------END-login()---------\n" + toXml().toString());
      return sessionId;
   }


   /**
    * Access a clientInfo with the unique login name.
    * <p />
    * If the client is yet unknown, there will be instantiated a dummy ClientInfo object
    * @return the ClientInfo object<br />
    */
   public final ClientInfo getOrCreateClientInfoByName(String loginName) throws XmlBlasterException
   {
      if (loginName == null || loginName.length() < 2) {
         Log.warning(ME + ".InvalidClientName", "Given loginName='" + loginName + "' is invalid");
         throw new XmlBlasterException(ME + ".InvalidClientName", "Your given loginName is null or shorter 2 chars, loginName rejected");
      }

      ClientInfo clientInfo = getClientInfoByName(loginName);
      if (clientInfo == null) {
         clientInfo = new ClientInfo(loginName);
         synchronized(loginNameClientInfoMap) {
            loginNameClientInfoMap.put(loginName, clientInfo);
         }
      }

      return clientInfo;
   }


   /**
    * Access a clientInfo with the unique login name
    * @return the ClientInfo object<br />
    *         null if not found
    */
   public final ClientInfo getClientInfoByName(String loginName)
   {
      synchronized(loginNameClientInfoMap) {
         return (ClientInfo)loginNameClientInfoMap.get(loginName);
      }
   }


   /**
    * Logout of a client.
    * <p>
    * @exception XmlBlasterException If client is unknown
    */
   public void logout(String sessionId) throws XmlBlasterException
   {
      if (Log.DUMP) Log.dump(ME, "-------START-logout()---------\n" + toXml().toString());
      ClientInfo clientInfo = resetClientInfo(sessionId, true);

      String loginName = clientInfo.getLoginName();

      synchronized(loginNameClientInfoMap) {
         loginNameClientInfoMap.remove(loginName);
      }

      Log.info(ME, "Successful logout for client " + loginName);
      clientInfo = null;
      if (Log.DUMP) Log.dump(ME, "-------END-logout()---------\n" + toXml().toString());
   }


   /**
    * @param xmlServer xmlBlaster CORBA handle
    * @param clearQueue Shall the message queue of the client be destroyed as well?
    */
   private ClientInfo resetClientInfo(String sessionId, boolean clearQueue) throws XmlBlasterException
   {
      /* !!!!!!!!!!!
      !!! Callback to protocolDriver into AuthServerImpl !!!!
      try {
         xmlServer._release();
      } catch (Exception e) {
         e.printStackTrace();
         Log.error(ME, e.toString());
      }
      */

      Object obj;
      synchronized(aomClientInfoMap) {
         obj = aomClientInfoMap.remove(sessionId);
      }

      if (obj == null) {
         Log.error(ME+".Unknown", "Sorry, you are not known, no logout");
         throw new XmlBlasterException(ME+".Unknown", "Sorry, you are not known, no logout");
      }

      ClientInfo clientInfo = (ClientInfo)obj;

      fireClientEvent(clientInfo, false); // informs all I_ClientListener

      clientInfo.notifyAboutLogout(true);

      return clientInfo;
   }


   /**
    *  Generate a unique resource ID <br>
    *
    *  @param loginName
    *  @return unique ID
    *  @exception XmlBlasterException random generator
    */
   private final String createSessionId(String loginName) throws XmlBlasterException
   {
      try {
         String ip;
         try  {
            java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
            ip = addr.getHostAddress();
         } catch (Exception e) {
            Log.warning(ME, "Can't determin your IP address");
            ip = "localhost";
         }

         // This is a real random, but probably not necessary here:
         // Random random = new java.security.SecureRandom();
         java.util.Random ran = new java.util.Random();  // is more or less currentTimeMillis

         // Note: We should include the process ID from this JVM on this host to be granted unique

         //   <IP-Address>-<LoginName>-<TimestampMilliSec>-<RandomNumber>-<LocalCounter>
         String sessionId = ip + "-" + loginName + "-" + System.currentTimeMillis() + "-" +  ran.nextInt() + "-" + (counter++);
                        if (Log.TRACE) Log.trace(ME, "Created sessionId='" + sessionId + "'");
                        return sessionId;
      }
      catch (Exception e) {
         String text = "Can't generate a unique sessionId: " + e.toString();
         Log.error(ME, text);
         throw new XmlBlasterException("NoSessionId", text);
      }
   }


   /**
    * Used to fire an event if a client does a login / logout
    */
   private final void fireClientEvent(ClientInfo clientInfo, boolean login) throws XmlBlasterException
   {
      synchronized (clientListenerSet) {
         if (clientListenerSet.size() == 0)
            return;

         ClientEvent event = new ClientEvent(clientInfo);
         Iterator iterator = clientListenerSet.iterator();

         while (iterator.hasNext()) {
            I_ClientListener cli = (I_ClientListener)iterator.next();
            if (login)
               cli.clientAdded(event);
            else
               cli.clientRemove(event);
         }

         event = null;
      }
   }


   /**
    * Use this method to check a clients authentication.
    * <p>
    * This method can only be called from an invoked xmlBlaster-server
    * method (like subscribe()), because only there the
    * unique POA 'active object identifier' is available to identify the caller.
    *
    * @return ClientInfo - if the client is OK
    * @exception XmlBlasterException Access denied
    */
   public ClientInfo check(String sessionId) throws XmlBlasterException
   {
      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

      Object obj = null;
      synchronized(aomClientInfoMap) {
         obj = aomClientInfoMap.get(sessionId);
      }

      if (obj == null) {
         Log.error(ME+".AccessDenied", "Sorry, sessionId is invalid");
         throw new XmlBlasterException("AccessDenied", "Sorry, sessionId is invalid");
      }
      ClientInfo clientInfo = (ClientInfo)obj;

      if (Log.TIME) Log.time(ME, "Elapsed time in check()" + stop.nice());
      if (Log.TRACE) Log.trace(ME, "Succesfully granted access for " + clientInfo.toString());

      return clientInfo;
   }


   /**
    * Adds the specified client listener to receive login/logout events.
    * <p />
    * This listener needs to implement the I_ClientListener interface.
    */
   public void addClientListener(I_ClientListener l) {
      if (l == null) {
         return;
      }
      synchronized (clientListenerSet) {
         clientListenerSet.add(l);
      }
   }


   /**
    * Removes the specified listener
    */
   public synchronized void removeClientListener(I_ClientListener l) {
      if (l == null) {
         return;
      }
      synchronized (clientListenerSet) {
         clientListenerSet.remove(l);
      }
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of Authenticate as a XML ASCII string
    */
   public final String toXml() throws XmlBlasterException
   {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of Authenticate as a XML ASCII string
    */
   public final String toXml(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer(1000);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      if (aomClientInfoMap.size() != loginNameClientInfoMap.size())
         Log.error(ME, "Inconsistent client maps, aomClientInfoMap.size()=" + aomClientInfoMap.size() + " and loginNameClientInfoMap.size()=" + loginNameClientInfoMap.size());
      Iterator iterator = loginNameClientInfoMap.values().iterator();

      sb.append(offset + "<Authenticate>");
      while (iterator.hasNext()) {
         ClientInfo clientInfo = (ClientInfo)iterator.next();
         sb.append(clientInfo.printOn(extraOffset + "   ").toString());
      }
      sb.append(offset + "</Authenticate>\n");

      return sb.toString();
   }

}
