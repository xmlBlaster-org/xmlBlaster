/*------------------------------------------------------------------------------
Name:      Authenticate.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login for clients
Version:   $Id: Authenticate.java,v 1.48 2002/02/16 13:27:46 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.plugins.I_Subject;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.authentication.plugins.PluginManager;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.ClientInfo;
import org.jutils.time.StopWatch;
import java.util.*;



/**
 * Authenticate a client via login.
 * <p>
 * The login method serves as a factory for a xmlBlaster.Server Reference
 */
final public class Authenticate implements I_Authenticate
{
   final private static String ME = "Authenticate";

   public static final String DEFAULT_SECURITYPLUGIN_TYPE = "simple";
   public static final String DEFAULT_SECURITYPLUGIN_VERSION = "1.0";

   private PluginManager plgnLdr = null;

//   private Hashtable sessions = new Hashtable();

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
      if (Log.CALL) Log.call(ME, "Entering constructor");
      plgnLdr = PluginManager.getInstance();
      plgnLdr.init(this);
   }


   /**
    * Authentication of a client.
    * <p>
    * If the qos contains security tags with credentials, e.g.
    * <pre>
    *  &lt;qos>
    *     &lt;securityService type="simple" version="1.0">
    *        &lt;![CDATA[
    *           &lt;user>michele&lt;/user>
    *           &lt;passwd>secret&lt;/passwd>
    *        ]]>
    *     &lt;/securityService>
    *  &lt;/qos>
    * </pre>
    * these are stronger and the given loginName / password arguments of this
    * method are ignored. 
    * @param xmlQoS_literal, for example
    * <pre>
    *    &lt;qos>
    *       &lt;callback type='XML-RPC'>
    *          http:/www.mars.universe:8080/RPC2
    *       &lt;/callback>
    *    &lt;/qos>
    * </pre>
    * @param sessionId The user session ID if generated outside, otherwise null
    * @return The sessionId on successful login
    * @exception XmlBlasterException Access denied
    * @deprecated Use connect()
    */
   public String login(String loginName, String passwd,
                       String xmlQoS_literal, String sessionId)
                          throws XmlBlasterException
   {
      try {
         I_Subject subject = null;
         I_Session sessionSecurityCtx = null;
         I_Manager securityMgr = null;
         String returnQoS = null;
         ConnectQos xmlQoS = new ConnectQos(xmlQoS_literal);
         ClientInfo clientInfo = null;
         AuthenticationInfo  authInfo = null;

         if (Log.DUMP) Log.dump(ME, "-------START-login(" + loginName + ", " + sessionId + ")---------\n" + toXml().toString());
         if (Log.DUMP) Log.dump(ME, xmlQoS_literal);

         // --- try to get a suitable SecurityManager ----------------------------
         securityMgr = plgnLdr.getManager(xmlQoS.getSecurityPluginType(),
                          xmlQoS.getSecurityPluginVersion()); // throws XmlBlasterExceptions

         clientInfo = getClientInfoByName(loginName);

         if (clientInfo != null && clientInfo.isLoggedIn()) {
            Log.warn(ME+".AlreadyLoggedIn", "Client " + loginName + " is already logged in. Your login session will be re-initialized.");
            try {
               resetClientInfo(clientInfo.getUniqueKey(), false);
            } catch(XmlBlasterException e) {
               // fireClientEvent(clientInfo, false); // informs all I_ClientListener
               // clientInfo.notifyAboutLogout(true);
               // clientInfo = null;
            }
            // allowing re-login: if the client crashed without proper logout, she should
            // be allowed to login again, so - first logout the last session (but keep messages in client queue)
            // We need to clean up clientInfo, usually the callback reference is another one, etc.
         }

         if (sessionId == null || sessionId.length() < 2) {
            sessionId = createSessionId(loginName);
         }

         sessionSecurityCtx = securityMgr.reserveSession(sessionId);

         String securityQoS = xmlQoS.getSecurityData();
         if (securityQoS == null)
            securityQoS = "<securityPlugin type=\"" + DEFAULT_SECURITYPLUGIN_TYPE +
                              "\" version=\"" + DEFAULT_SECURITYPLUGIN_VERSION + "\">\n" +
                              "   <user>" + loginName + "</user>\n" +
                              "   <passwd>" + passwd + "</passwd>\n" +
                              "</securityPlugin>";
         
         String clientSecurityInfo = sessionSecurityCtx.init(securityQoS); // throws XmlBlasterExceptions
         if (clientSecurityInfo != null && clientSecurityInfo.length() > 1)
            Log.warn(ME, "Ignoring security info: " + clientSecurityInfo);
         subject = sessionSecurityCtx.getSubject();

         authInfo = new AuthenticationInfo(sessionId, loginName, passwd, xmlQoS);

         if (clientInfo != null) {
            clientInfo.notifyAboutLogin(authInfo); // clientInfo object exists, maybe with a queue of messages
         }
         else {                               // login of yet unknown client
            clientInfo = new ClientInfo(authInfo, sessionSecurityCtx);
            synchronized(loginNameClientInfoMap) {
               loginNameClientInfoMap.put(loginName, clientInfo);
            }
         }

         synchronized(aomClientInfoMap) {
            aomClientInfoMap.put(sessionId, clientInfo);
         }

         fireClientEvent(clientInfo, true);

         Log.info(ME, "Successful login for client " + loginName);
         if (Log.DUMP) Log.dump(ME, "-------END-login()---------\n" + toXml().toString());
         return sessionId;
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException("Authenticate.login.InternalError", e.toString());
      }
   }


   /**
    * Login to xmlBlaster.
    *
    * If the sessionId from xmlQoS_literal is null, we generate one.
    *
    * @param xmlQos The login/connect QoS, see ConnectQos.java
    */
   public final String connect(String qos_literal) throws XmlBlasterException
   {
      return connect(new ConnectQos(qos_literal), null).toXml();
   }

   /**
    * Login to xmlBlaster.
    *
    * If the sessionId from xmlQoS_literal is null, we generate one.
    *
    * @param xmlQos The login/connect QoS, see ConnectQos.java
    */
   public final String connect(String qos_literal, String sessionId) throws XmlBlasterException
   {
      return connect(new ConnectQos(qos_literal), sessionId).toXml();
   }

   /**
    * Login to xmlBlaster.
    *
    * If the sessionId from xmlQoS_literal is null, we generate one.
    *
    * @param xmlQos The login/connect QoS, see ConnectQos.java
    */
   public final ConnectReturnQos connect(ConnectQos xmlQos) throws XmlBlasterException
   {
      return connect(xmlQos, null);
   }

   /**
    * Login to xmlBlaster.
    *
    * If no sessionId==null, the sessionId from xmlQoS_literal is used,
    * if this is null as well, we generate one.
    * <p />
    * The given sessionId (in the qos) from the client could be from e.g. a2Blaster,
    * and will be used here as is, the a2Blaster plugin verifies it.
    * The extra parameter sessionId is the CORBA internal POA session id.
    * <p />
    * TODO: Totally rewrite this connect() method to allow multiple sessions
    *
    * @param connectQos  The login/connect QoS, see ConnectQos.java
    * @param sessionId   The caller (here CORBA-POA protocol driver) may insist to you its own sessionId
    */
   public final ConnectReturnQos connect(ConnectQos connectQos, String sessionId) throws XmlBlasterException
   {
      try {
         if (Log.CALL) Log.call(ME, "-------START-connect()---------");
         if (Log.DUMP) Log.dump(ME, toXml().toString());

         I_Subject subject = null;
         I_Session sessionSecurityCtx = null;
         I_Manager securityMgr = null;
         ClientInfo clientInfo = null;
         AuthenticationInfo  authInfo = null;
         if (sessionId == null)
            sessionId = connectQos.getSessionId();
         if (sessionId == null || sessionId.length() < 2) {
            sessionId = createSessionId("null" /*subject.getName()*/);
            if (Log.TRACE) Log.trace(ME+".connect()", "Empty sessionId - generated sessionId=" + sessionId);
            connectQos.setSessionId(sessionId);
         }
         // we don't overwrite the given qos-sessionId with the given sessionId-parameter

         // --- try to get a suitable SecurityManager ----------------------------
         securityMgr = plgnLdr.getManager(connectQos.getSecurityPluginType(),
                                          connectQos.getSecurityPluginVersion()); // throws XmlBlasterExceptions

         sessionSecurityCtx = securityMgr.reserveSession(sessionId);
         String clientSecurityInfo = sessionSecurityCtx.init(connectQos.getSecurityQos()); // throws XmlBlasterExceptions
         if (clientSecurityInfo != null && clientSecurityInfo.length() > 1)
            Log.warn(ME, "Ignoring security info: " + clientSecurityInfo);
         subject = sessionSecurityCtx.getSubject();

         clientInfo = getClientInfoByName(subject.getName());
         if(clientInfo!=null) {
            I_Session oldSessionSecCtx = clientInfo.getSecuritySession();
            oldSessionSecCtx.getManager().releaseSession(oldSessionSecCtx.getSessionId(), null);
         }

         if (clientInfo != null && clientInfo.isLoggedIn()) {
            Log.warn(ME+".AlreadyLoggedIn", "Client " + subject.getName() + " is already logged in. Your login session will be re-initialized.");
            try {
               resetClientInfo(clientInfo.getUniqueKey(), false);
            } catch(XmlBlasterException e) {
               // fireClientEvent(clientInfo, false); // informs all I_ClientListener
               // clientInfo.notifyAboutLogout(true);
               // clientInfo = null;
            }
            // allowing re-login: if the client crashed without proper logout, she should
            // be allowed to login again, so - first logout the last session (but keep messages in client queue)
            // We need to clean up clientInfo, usually the callback reference is another one, etc.
         }

         if (sessionId == null || sessionId.length() < 2) {
            sessionId = createSessionId(subject.getName());
         }

         authInfo = new AuthenticationInfo(sessionId, subject.getName(), "", connectQos);

         try {
            // Tell plugin the new sessionId on relogins...
            sessionSecurityCtx.changeSessionId(sessionId);
         }
         catch (XmlBlasterException xe) {
            Log.error(ME+".rejected", "Can't change session id="+sessionSecurityCtx.getSessionId()+" to session id="+sessionId+"! Reason: "+xe.toString());
         }

         if (clientInfo != null) {
            clientInfo.setSecuritySession(sessionSecurityCtx);
            clientInfo.notifyAboutLogin(authInfo); // clientInfo object exists, maybe with a queue of messages
         }
         else {                               // login of yet unknown client
            clientInfo = new ClientInfo(authInfo, sessionSecurityCtx);
            synchronized(loginNameClientInfoMap) {
               loginNameClientInfoMap.put(subject.getName(), clientInfo);
            }
         }

         synchronized(aomClientInfoMap) {
            aomClientInfoMap.put(sessionId, clientInfo);
         }

         fireClientEvent(clientInfo, true);

         // --- compose an answer -----------------------------------------------
         ConnectReturnQos returnQos = new ConnectReturnQos(connectQos);
         returnQos.setSessionId(sessionId); // clientSecurityInfo is not coded yet !

         if (Log.DUMP) Log.dump(ME, "Returned QoS:\n" + returnQos.toXml());
         Log.info(ME, "Successful login for client " + subject.getName());
         if (Log.CALL) Log.call(ME, "-------END-connect()---------");
         if (Log.DUMP) Log.dump(ME, toXml().toString());

         return returnQos;
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException("Authenticate.connect.InternalError", e.toString());
      }
   }


   public final String disconnect(String sessionId, String qos_literal) throws XmlBlasterException
   {
      try {
         if (Log.CALL) Log.call(ME, "-------START-disconnect()---------" + toXml().toString());
         if (Log.DUMP) Log.dump(ME, toXml().toString());

         I_Manager securityMgr = plgnLdr.getManager(sessionId);
         I_Session sessionSecCtx = securityMgr.getSessionById(sessionId);
         sessionSecCtx.importMessage(qos_literal);

         ClientInfo clientInfo = resetClientInfo(sessionId, true);

         String loginName = clientInfo.getLoginName();

         securityMgr.releaseSession(sessionId, null);

         synchronized(loginNameClientInfoMap) {
            loginNameClientInfoMap.remove(loginName);
         }

         if (aomClientInfoMap.size() != loginNameClientInfoMap.size())
            Log.error(ME, "Inconsistent client maps, aomClientInfoMap.size()=" + aomClientInfoMap.size() + " and loginNameClientInfoMap.size()=" + loginNameClientInfoMap.size());

         Log.info(ME, "Client " + loginName + " (sessionId=" + sessionId + ") successfully disconnected!");
         clientInfo = null;
         if (Log.CALL) Log.call(ME, "-------END-disconnect()---------");
         if (Log.DUMP) Log.dump(ME, toXml().toString());
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException("Authenticate.disconnect.InternalError", e.toString());
      }
      return "<qos/>";
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
         Log.warn(ME + ".InvalidClientName", "Given loginName='" + loginName + "' is invalid");
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
   public final void logout(String sessionId) throws XmlBlasterException
   {
      try {
         if (Log.CALL) Log.call(ME, "-------START-logout()---------\n" + toXml().toString());
         I_Manager securityMgr = plgnLdr.getManager(sessionId);
         ClientInfo clientInfo = resetClientInfo(sessionId, true);
         String loginName = clientInfo.getLoginName();

         securityMgr.releaseSession(sessionId, null);

         synchronized(loginNameClientInfoMap) {
            loginNameClientInfoMap.remove(loginName);
         }

         Log.info(ME, "Successful logout for client " + loginName);
         clientInfo = null;
         if (Log.CALL) Log.call(ME, "-------END-logout()---------\n" + toXml().toString());
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException("Authenticate.logout.InternalError", e.toString());
      }
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
   private String createSessionId(String loginName) throws XmlBlasterException
   {
      try {
         String ip;
         try  {
            java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
            ip = addr.getHostAddress();
         } catch (Exception e) {
            Log.warn(ME, "Can't determin your IP address");
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
   private void fireClientEvent(ClientInfo clientInfo, boolean login) throws XmlBlasterException
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
         Log.error(ME+".AccessDenied", "Sorry, sessionId '" + sessionId + "' is invalid");
         //(new Exception("")).printStackTrace();
         throw new XmlBlasterException("AccessDenied", "Sorry, sessionId is invalid!");
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

      Iterator iterator = loginNameClientInfoMap.values().iterator();

      sb.append(offset + "<Authenticate>");
      while (iterator.hasNext()) {
         ClientInfo clientInfo = (ClientInfo)iterator.next();
         sb.append(clientInfo.toXml(extraOffset + "   "));
      }
      sb.append(offset + "</Authenticate>\n");

      return sb.toString();
   }

}
