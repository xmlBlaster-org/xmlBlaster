/*------------------------------------------------------------------------------
Name:      BlasterHttpProxy.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling callback over http
Version:   $Id: BlasterHttpProxy.java,v 1.7 2000/03/15 22:18:25 kkrafft2 Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import java.rmi.RemoteException;
import java.io.*;
import java.util.*;
import org.xmlBlaster.client.*;
import org.xmlBlaster.util.*;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;


/**
 * This servlet doesn't leave the doGet() method after an invocation
 * keeping a permanent http connection.
 * <p />
 * With the doPost() method you may login/logout to xmlBlaster, and
 * do your work with publish/subscribe etc.<br />
 * With the doGet() method you receive your instant callbacks.
 * <p />
 * The logging output is redirected to the normal servlet log file.
 * If you use Apache/Jserv, look into /var/log/httpd/jserv.log
 * <p />
 * Invoke for testing:<br />
 *    http://localhost/servlet/CallbackServletDriver?ActionType=login&loginName=martin&passwd=secret
 * <p />
 * TODO:
 *   HTTP 1.1 specifies rfc2616 that the connection stays open as the
 *   default case. How must this code be changed?
 * @author Marcel Ruff ruff@swand.lake.de
 * @version $Revision: 1.7 $
 */
public class BlasterHttpProxy
{
   private static final String ME                      = "BlasterHttpProxy";

   /** Mapping the sessionId to a ServletConnection instance */
   private static Hashtable proxyConnections           = new Hashtable();

   /** Stores global Attributes for other Servlets */
   private static Hashtable attributes                 = new Hashtable();


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
    * gives a proxy connection by a given loginName and Password
    *
    * @param loginName
    * @return valid proxyConnection for valid HTTP sessionId.
    */
   public static ProxyConnection getNewProxyConnection( String loginName, String passwd ) throws XmlBlasterException
   {
      ProxyConnection pc = new ProxyConnection( loginName, passwd );
      proxyConnections.put( loginName, pc );
      return pc;
   }

   /**
    * gives a proxy connection by a given loginName and Password
    *
    * @param loginName
    * @return valid proxyConnection for valid HTTP sessionId.
    */
   public static ProxyConnection getProxyConnectionByLoginName( String loginName ) throws XmlBlasterException
   {
      //return a proxy connection by login name
      return (ProxyConnection)proxyConnections.get( loginName );
   }

   /**
    * combines getProxyConnectionByLoginName and getNewProxyConnection in a synchronized
    * way
    *
    * @param loginName
    * @return valid proxyConnection for valid HTTP sessionId.
    */
   public static ProxyConnection getProxyConnection( String loginName, String passwd ) throws XmlBlasterException
   {
      synchronized( proxyConnections ) {
         ProxyConnection pc = getProxyConnectionByLoginName(loginName);
         if(pc==null)
            pc=getNewProxyConnection(loginName,passwd);
         return pc;
      }
   }


   /**
    * gives a proxy connection by a given sessionId
    * if a proxy connection was found by the loginName, it will be returned.
    * if not the routine searches the first proxyConnection
    * which contains a HTTP Session with the given sessionId
    *
    * @param sessionId HTTP sessionId
    * @return valid proxyConnection for valid HTTP sessionId.
    */
   public static ProxyConnection getProxyConnectionBySessionId( String sessionId ) throws XmlBlasterException
   {
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
    * gives a corbaConnection by a given loginName
    *
    * @param loginName
    */
   public static CorbaConnection getCorbaConnection( String loginName, String passwd ) throws XmlBlasterException
   {
      synchronized( proxyConnections ) {
         Log.plain(ME,"proxyConnections="+proxyConnections);
         ProxyConnection pc =  getProxyConnectionByLoginName(loginName);
         if( pc == null ) {
            pc =  getNewProxyConnection(loginName, passwd);
         }
         return pc.getCorbaConnection();
      }
   }


}
