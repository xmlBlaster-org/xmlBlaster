/*------------------------------------------------------------------------------
Name:      BlasterHttpProxy.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This class contains some useful, static helper methods.
Version:   $Id: BlasterHttpProxy.java,v 1.10 2000/05/03 17:08:04 ruff Exp $
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
 * Contains some useful, static helper methods. It is
 * a singleton.
 * <p />
 * It holds a hashtable with all current browser-xmlBlaster connections.
 * <p />
 * You can also use this class to handle shared attributes for all servlets.
 * @author Konrad Krafft
 * @version $Revision: 1.10 $
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
    * gives a proxy connection by a given loginName and Password
    *
    * @param loginName
    * @return valid proxyConnection for valid HTTP sessionId.
    */
   public static ProxyConnection getNewProxyConnection( String loginName, String passwd ) throws XmlBlasterException
   {
      synchronized(proxyConnections) {
         ProxyConnection pc = new ProxyConnection( loginName, passwd );
         proxyConnections.put( loginName, pc );
         return pc;
      }
   }

   /**
    * gives a proxy connection by a given loginName and Password
    *
    * @param loginName
    * @return valid proxyConnection for valid HTTP sessionId.
    */
   public static ProxyConnection getProxyConnectionByLoginName( String loginName ) throws XmlBlasterException
   {
      synchronized(proxyConnections) {
         //return a proxy connection by login name
         return (ProxyConnection)proxyConnections.get( loginName );
      }
   }

   /**
    * combines getProxyConnectionByLoginName and getNewProxyConnection in a synchronized
    * way
    *
    * @param loginName
    * @return valid proxyConnection for valid HTTP sessionId.
    */
   synchronized public static ProxyConnection getProxyConnection( String loginName, String passwd ) throws XmlBlasterException
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


   /**
    * gives a corbaConnection by sessionId.
    * <p />
    * This CorbaConnection holds the CORBA connection to the XmlBlaster server
    * @param sessionId
    */
   public static CorbaConnection getCorbaConnection(String sessionId) throws XmlBlasterException
   {
      ProxyConnection pc = getProxyConnectionBySessionId(sessionId);
      if( pc == null ) {
         Log.trace(ME, "getCorbaConnection(" + sessionId + ") returned null");
         return null;
      }
      return pc.getCorbaConnection();
   }
}
