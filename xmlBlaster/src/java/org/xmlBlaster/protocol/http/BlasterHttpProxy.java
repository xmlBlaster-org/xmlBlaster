/*------------------------------------------------------------------------------
Name:      BlasterHttpProxy.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This class contains some useful, static helper methods.
Version:   $Id: BlasterHttpProxy.java,v 1.26 2003/03/24 16:13:19 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import java.rmi.RemoteException;
import java.io.*;
import java.util.*;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_XmlBlasterAccess;

import javax.servlet.http.HttpServletRequest;


/**
 * Contains some useful, static helper methods. It is
 * a singleton.
 * <p />
 * It holds a hashtable with all current browser-xmlBlaster connections.
 * <p />
 * You can also use this class to handle shared attributes for all servlets.
 * @author Konrad Krafft
 * @version $Revision: 1.26 $
 */
public class BlasterHttpProxy
{
   private static final String ME = "BlasterHttpProxy";

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
    * Remove the attribute.
    *
    * @param name key name
    */
   public static void removeAttribute(String name)
   {
      attributes.remove(name);
   }

   /**
    * Gives a proxy connection by a given loginName and Password.
    * <p />
    * Multiple logins of the same user are possible.
    * @param loginName
    * @return valid proxyConnection for valid HTTP sessionId (is never null).
    * @exception XmlBlasterException if login fails
    */
   public static ProxyConnection getNewProxyConnection(Global glob, String loginName, String passwd) throws XmlBlasterException
   {
      synchronized(proxyConnections) {
         ProxyConnection pc = (ProxyConnection)proxyConnections.get(loginName);
         if(pc==null) {
            pc = new ProxyConnection(glob, loginName, passwd);
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
    * @return valid proxyConnection for valid HTTP sessionId (never null)
    * @exception If sessionId not found.
    */
   public static ProxyConnection getProxyConnectionBySessionId( String sessionId ) throws XmlBlasterException
   {
      if (sessionId == null) {
         throw new XmlBlasterException(ME+".NoSessionId", "Sorry, no valid session ID, are cookies disabled?");
      }
      synchronized( proxyConnections ) {
         for( Enumeration e = proxyConnections.elements(); e.hasMoreElements() ; ) {
            ProxyConnection pc = (ProxyConnection) e.nextElement();
            HttpPushHandler hph = pc.getHttpPushHandlerOrNull(sessionId);
            if( hph != null )
               return pc;
         }
      }
      throw new XmlBlasterException(ME, "Session not registered yet (sessionId="+sessionId+")");
   }


   /**
    * Cleanup Hashtable etc.
    */
   public static void cleanupByLoginName(String loginName)
   {
      synchronized(proxyConnections) {
         proxyConnections.remove(loginName);
      }
   }


   /**
    * gives a xmlBlasterConnection by a given loginName
    *
    * @param loginName
    */
   public static I_XmlBlasterAccess getXmlBlasterAccess(Global glob, String loginName, String passwd ) throws XmlBlasterException
   {
      synchronized( proxyConnections ) {
         ProxyConnection pc = getNewProxyConnection(glob, loginName, passwd);
         return pc.getXmlBlasterAccess();
      }
   }


   /**
    * gives a xmlBlasterConnection by sessionId.
    * <p />
    * This I_XmlBlasterAccess holds the CORBA connection to the XmlBlaster server
    * @param req Servlet request, only for error handling
    * @param sessionId
    * @return The I_XmlBlasterAccess object or null if sessionId is unknown
    */
   public static I_XmlBlasterAccess getXmlBlasterAccess(HttpServletRequest req, String sessionId) throws XmlBlasterException
   {
      if (sessionId == null && req != null && !req.isRequestedSessionIdFromCookie()) { // && isCookieEnabled() ?????
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
      return pc.getXmlBlasterAccess();
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
      return proxyConnection.getHttpPushHandler(sessionId);
   }
}
