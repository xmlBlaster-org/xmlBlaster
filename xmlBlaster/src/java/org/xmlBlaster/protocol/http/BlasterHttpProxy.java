/*------------------------------------------------------------------------------
Name:      BlasterHttpProxy.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This class contains some useful, static helper methods.
Version:   $Id: BlasterHttpProxy.java,v 1.15 2000/05/18 17:20:01 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import java.rmi.RemoteException;
import java.io.*;
import java.util.*;
import org.xmlBlaster.client.*;
import org.xmlBlaster.util.*;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;
import javax.servlet.http.HttpServletRequest;


/**
 * Contains some useful, static helper methods. It is
 * a singleton.
 * <p />
 * It holds a hashtable with all current browser-xmlBlaster connections.
 * <p />
 * You can also use this class to handle shared attributes for all servlets.
 * @author Konrad Krafft
 * @version $Revision: 1.15 $
 */
public class BlasterHttpProxy
{
   private static final String ME            = "BlasterHttpProxy";

   /** Mapping the sessionId to a ProxyConnection instance.
    * <p />
    * This ProxyConnections knows how to access xmlBlaster (with CORBA)
    * and the Browser (with http).
    */
   private static Hashtable proxyConnections = new Hashtable();

   /** Stores global Attributes for other Servlets */
   private static Hashtable attributes       = new Hashtable();


   /**
    * returns a Object by a given name
    *
    * @param name key name
    * @return Object
    */
   public static Object getAttribute(String name)
   {
      return attributes.get(name);
   }

   /**
    * writes a Object by a given name
    *
    * @param name key name
    * @param obj Object
    */
   public static void setAttribute(String name, Object obj)
   {
      attributes.put(name,obj);
   }

   /**
    * Gives a proxy connection by a given loginName and Password.
    * <p />
    * Multiple logins of the same user are possible.
    * @param loginName
    * @return valid proxyConnection for valid HTTP sessionId (is never null).
    * @exception XmlBlasterException if login fails
    */
   public static ProxyConnection getNewProxyConnection(String loginName, String passwd) throws XmlBlasterException
   {
      synchronized(proxyConnections) {
         ProxyConnection pc = (ProxyConnection)proxyConnections.get(loginName);
         if(pc==null) {
            pc = new ProxyConnection( loginName, passwd );
            proxyConnections.put(loginName, pc);
            return pc;
         }
         else {
            // Allow re-login from another browser.
            if (pc.checkPasswd(passwd) == false) {
               throw new XmlBlasterException(ME+".AccessDenied", "Wong password for user " + loginName + ". You are logged in already.");
            }
            return pc;
         }
      }
   }


   /**
    * Gives a proxy connection by a given sessionId.
    * <p />
    * @param sessionId HTTP sessionId
    * @return valid proxyConnection for valid HTTP sessionId.
    */
   public static ProxyConnection getProxyConnectionBySessionId( String sessionId ) throws XmlBlasterException
   {
      if (sessionId == null) {
         throw new XmlBlasterException(ME+".NoSessionId", "Sorry, no valid session ID, are cookies disabled?");
      }
      synchronized( proxyConnections ) {
         for( Enumeration e = proxyConnections.elements(); e.hasMoreElements() ; ) {
            ProxyConnection pc = (ProxyConnection) e.nextElement();
            HttpPushHandler hph = pc.getHttpPushHandler( sessionId );
            if( hph != null )
               return pc;
         }
      }
      return null;
   }


   /**
    * Cleanup Hashtable etc.
    */
   public static void cleanupByLoginName(String loginName)
   {
      if (Log.CALLS) Log.calls(ME, "Entering cleanupByLoginName(" + loginName + ")");
      synchronized(proxyConnections) {
         proxyConnections.remove(loginName);
      }
   }


   /**
    * Cleanup Hashtable etc.
    */
   public static void cleanupBySessionId(String sessionId)
   {
      if (Log.CALLS) Log.calls(ME, "Entering cleanupBySessionId(" + sessionId + ")");
      synchronized(proxyConnections) {
         // nothing to do ...
      }
   }


   /**
    * gives a corbaConnection by a given loginName
    *
    * @param loginName
    */
   public static CorbaConnection getCorbaConnection( String loginName, String passwd ) throws XmlBlasterException
   {
      synchronized( proxyConnections ) {
         Log.plain(ME,"proxyConnections="+proxyConnections);
         ProxyConnection pc = getNewProxyConnection(loginName, passwd);
         if( pc == null ) {
         }
         return pc.getCorbaConnection();
      }
   }


   /**
    * gives a corbaConnection by sessionId.
    * <p />
    * This CorbaConnection holds the CORBA connection to the XmlBlaster server
    * @param req Servlet request, only for error handling
    * @param sessionId
    * @return The CorbaConnection object or null if sessionId is unknown
    */
   public static CorbaConnection getCorbaConnection(HttpServletRequest req, String sessionId) throws XmlBlasterException
   {
      if (sessionId == null && !req.isRequestedSessionIdFromCookie()) { // && isCookieEnabled() ?????
         throw new XmlBlasterException(ME+".NoCookies", "Sorry, your browser\n" +
            "    " + req.getHeader("User-Agent") + "\n" +
            "does not support 'cookies or has switched them off.\n" +
            "We need cookies to handle your login session ID.\n" +
            "You will not get updates from xmlBlaster.");
      }
      else if (sessionId == null) {
         throw new XmlBlasterException(ME+".NoSessionId", "Sorry, no valid session ID");
      }

      ProxyConnection pc = getProxyConnectionBySessionId(sessionId);
      if( pc == null ) {
         Log.warning(ME, "getCorbaConnection(sessionId=" + sessionId + ") returned null");
         return null;
      }
      return pc.getCorbaConnection();
   }


   /**
    * gives a HttpPushHandler by sessionId.
    * <p />
    * This push handler holds the persistent http connection to the browser
    * @param sessionId
    */
   public static HttpPushHandler getHttpPushHandler(String sessionId) throws XmlBlasterException
   {
      if (sessionId == null) {
         throw new XmlBlasterException(ME+".NoSessionId", "Sorry, no valid session ID, are cookies disabled?");
      }
      ProxyConnection proxyConnection = getProxyConnectionBySessionId(sessionId);
      if( proxyConnection == null ) {
         throw new XmlBlasterException(ME+".SessionNotKnown", "Session not registered yet (sessionId="+sessionId+")");
      }
      return proxyConnection.getHttpPushHandler(sessionId);
   }
}
