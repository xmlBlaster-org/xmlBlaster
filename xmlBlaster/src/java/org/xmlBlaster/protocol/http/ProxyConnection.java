/*------------------------------------------------------------------------------
Name:      ProxyConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: ProxyConnection.java,v 1.17 2000/05/18 17:20:01 ruff Exp $
Author:    Marcel Ruff ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import org.xmlBlaster.util.*;
import org.xmlBlaster.client.*;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;
import org.xmlBlaster.protocol.corba.authenticateIdl.*;

import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.Properties;


/**
 * This is a little helper class, helping a Java servlet client to connect
 * to xmlBlaster using IIOP (CORBA)
 * and remembering the session informations from the browser - servlet connection.
 * <p />
 * There is exactly one CORBA connection to the xmlBlaster (see CorbaConnection instance),
 * but there may be many browser (see HttpPushHandler instances) using this unique login.
 * <p />
 * The BlasterHttpProxy class is a global instance, which allows to retrieve
 * this ProxyConnection through the login name or the sessionId.
 * <p />
 * If you want to change the servlet default behavior,
 * you need to specify environment variables in the servlet configuration file,<br />
 * for JServ see /etc/httpd/conf/jserv/zone.properties,<br />
 * for jrun see jrun/jsm-default/services/jse/properties/servlets.properties.<br />
 * @version $Revision: 1.17 $
 * @author ruff@swand.lake.de
 */
public class ProxyConnection implements I_Callback
{
   private final String ME                 = "ProxyConnection";
   private final String loginName;
   private final String passwd;
   private CorbaConnection corbaConnection = null;
   private Server       xmlBlaster         = null;
   private Hashtable httpConnections       = null;
   private I_ProxyInterceptor interceptor  = null;


   /**
    **/
   public ProxyConnection(String loginName, String passwd) throws XmlBlasterException
   {
      this.loginName = loginName;
      this.passwd = passwd; // remember it to allow multiple browser logins for the same user
      if (Log.TRACE) Log.trace(ME, "Creating ProxyConnection ...");

      if (loginName == null || loginName.length() < 1 || passwd == null || passwd.length() < 1)
         throw new XmlBlasterException(ME+".AccessDenied", "Please give proper login name and password");

      //establish connection to server
      corbaConnection = new CorbaConnection();
      xmlBlaster = corbaConnection.login( loginName, passwd, null, this);
      httpConnections = new Hashtable();
   }

   /**
    * Check local if passwd is valid. 
    */
   boolean checkPasswd(String pw)
   {
      if (pw == null) return false;
      if (passwd.equals(pw)) return true;
      return false;
   }


   /**
    * Invoking the callback to the browser. If an interceptor is set, the interceptor
    * could modify the update parameters.
    * <p />
    * This sends the message to the hidden frame in the browser
    */
   public void update(String loginName, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
   {
      Log.trace(ME,"----------Update:"+updateKey.getUniqueKey());
      String[] s_arr = new String[3];
      s_arr[0] = updateKey.toString();
      s_arr[1] = new String(content);
      s_arr[2] = updateQoS.toString();
      if(interceptor != null) {
         s_arr = interceptor.update(s_arr[0], s_arr[1], s_arr[2]);
      }

      Log.trace(ME, "Update of "+httpConnections.size()+" http connections.");
      for( Enumeration e = httpConnections.elements(); e.hasMoreElements() ; )
        ((HttpPushHandler)e.nextElement()).update( s_arr[0], s_arr[1], s_arr[2] );
   }

   /**
    * Close connection to xmlBlaster and to all browsers using this login.
    */
   public void cleanup()
   {
      if (Log.CALLS) Log.calls(ME, "Entering cleanup() ...");

      BlasterHttpProxy.cleanupByLoginName(corbaConnection.getLoginName());

      // Logout from xmlBlaster
      if (corbaConnection != null) {
         corbaConnection.logout();
         Log.info(ME, "Corba connection for '" + corbaConnection.getLoginName() + "' removed");
         corbaConnection = null;
      }

      // Disconnect from browsers
      if (httpConnections != null) {
         for( Enumeration e = httpConnections.elements(); e.hasMoreElements() ; )
           ((HttpPushHandler)e.nextElement()).deinitialize();
         httpConnections.clear();
      }
   }


   /**
    * Clean up if a browser disappears or does a logout.
    * @param sessionId The unique browser identifier
    */
   public void cleanup(String sessionId)
   {
      BlasterHttpProxy.cleanupBySessionId(sessionId);
      HttpPushHandler ph = getHttpPushHandler(sessionId);
      if (ph == null) {
         // No error, may be invoked multiple times (e.g. form servlet and pingThread)
         Log.trace(ME, "Can't cleanup browser connection, your sessionId " + sessionId + " is unknown");
         return;
      }

      // Disconnect from browser
      ph.deinitialize();
      removeHttpPushHandler(sessionId, ph);

      if (httpConnections.isEmpty()) {
         // Logout from xmlBlaster if no browser uses this connection anymore
         if (corbaConnection != null) {
            BlasterHttpProxy.cleanupByLoginName(corbaConnection.getLoginName());
            Log.info(ME, "Corba connection for '" + corbaConnection.getLoginName() + "' for browser with sessionId=" + sessionId + " removed");
            corbaConnection.logout();
            corbaConnection = null;
         }
      }
      Log.info(ME, "Browser connection with sessionId=" + sessionId + " removed");
   }


   public Server getXmlBlaster()
   {
      return xmlBlaster;
   }
   public CorbaConnection getCorbaConnection()
   {
      return corbaConnection;
   }
   public String getLoginName()
   {
      return loginName;
   }

   /**
    * The HttpPushHandler handles and hides the persistent connection to the browser.
    */
   public void addHttpPushHandler( String sessionId, HttpPushHandler pushHandler )
   {
      if( sessionId == null || pushHandler == null )
         Log.warning(ME,"You shouldn't use null pointer: sessionId="+sessionId+"; pushHandler="+pushHandler);
      httpConnections.put( sessionId, pushHandler );
   }
   public void removeHttpPushHandler( String sessionId, HttpPushHandler pushHandler )
   {
      HttpPushHandler ph = getHttpPushHandler(sessionId);
      if( pushHandler == ph )
        httpConnections.remove( sessionId );
      // otherwise the PusHandler may be overwritten by an old one
   }

   /**
    * @return The HttpPushHandler object or null if not found
    */
   public final HttpPushHandler getHttpPushHandler( String sessionId )
   {
      if (sessionId == null) return (HttpPushHandler)null;
      return (HttpPushHandler)httpConnections.get( sessionId );
   }

   /**
    * If you implement I_ProxyInterceptor and register it here,
    * your update() implementation is called and may manipulate the
    * received message from xmlBlaster before it is sent to the browser.
    * @parameter interceptor Your optional implementation
    */
   public void setProxyInterceptor( I_ProxyInterceptor interceptor )
   {
      this.interceptor = interceptor;
   }
}

