/*------------------------------------------------------------------------------
Name:      ProxyConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Author:    Marcel Ruff xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.util.enum.Constants;

import java.io.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;


/**
 * This is a little helper class, helping a Java servlet client to connect
 * to xmlBlaster using IIOP (CORBA)
 * and remembering the session informations from the browser - servlet connection.
 * <p />
 * There is exactly one CORBA connection to the xmlBlaster (see I_XmlBlasterAccess instance),
 * but there may be many browser (see HttpPushHandler instances) using this unique login.
 * <p />
 * The BlasterHttpProxy class is a global instance, which allows to retrieve
 * this ProxyConnection through the login name or the sessionId.
 * <p />
 * @version $Revision: 1.39 $
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 */
public class ProxyConnection implements I_Callback
{
   private String ME = "ProxyConnection";
   private final Global glob;
   private final LogChannel log;
   private final String loginName;
   private final String passwd;
   private I_XmlBlasterAccess xmlBlasterConnection = null;
   private Hashtable httpConnections = new Hashtable(); // must be always != null
   private I_ProxyInterceptor interceptor = null;


   /**
    */
   public ProxyConnection(Global glob, String loginName, String passwd) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("http");
      this.loginName = loginName;
      this.passwd = passwd; // remember it to allow multiple browser logins for the same user
      this.ME = "ProxyConnection-" + loginName;

      if (log.TRACE) log.trace(ME, "Creating ProxyConnection ...");

      if (loginName == null || loginName.length() < 1 || passwd == null || passwd.length() < 1)
         throw new XmlBlasterException(ME+".AccessDenied", "Please give proper login name and password");

      //establish a CORBA based connection to server
      xmlBlasterConnection = glob.getXmlBlasterAccess();

      // initFailSave() ???

      org.xmlBlaster.client.qos.ConnectQos connectQos = 
            new org.xmlBlaster.client.qos.ConnectQos(glob, loginName, passwd);
      xmlBlasterConnection.connect(connectQos, this);
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
   public String update(String sessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.trace(ME,"----------Update:"+updateKey.getOid());
      String[] s_arr = new String[3];
      s_arr[0] = updateKey.toString();
      s_arr[1] = new String(content);
      s_arr[2] = updateQos.toString();
      if(interceptor != null) {
         s_arr = interceptor.update(s_arr[0], s_arr[1], s_arr[2]);
      }

      log.trace(ME, "Update of "+httpConnections.size()+" http connections.");
      synchronized(httpConnections) {
         for( Enumeration e = httpConnections.elements(); e.hasMoreElements() ; )
           ((HttpPushHandler)e.nextElement()).update( s_arr[0], s_arr[1], s_arr[2] );
      }
      return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
   }

   /**
    * Close connection to xmlBlaster and to all browsers using this login.
    */
   public void cleanup()
   {
      if (log.CALL) log.call(ME, "Entering cleanup() ...");


      synchronized(httpConnections) {
         // Logout from xmlBlaster
         if (xmlBlasterConnection != null) {
            BlasterHttpProxy.cleanupByLoginName(xmlBlasterConnection.getSessionName().getLoginName());
            xmlBlasterConnection.disconnect(null);
            log.info(ME, "Corba connection for '" + xmlBlasterConnection.getSessionName().getLoginName() + "' removed");
            xmlBlasterConnection = null;
         }

         // Disconnect from browsers
         for( Enumeration e = httpConnections.elements(); e.hasMoreElements() ; )
            ((HttpPushHandler)e.nextElement()).deinitialize();
         httpConnections.clear();
      }
   }


   /**
    * Clean up if a browser disappears or does a logout.
    * If this was the last browser using the xmlBlasterConnection,
    * the CORBA connection to xmlBlaster is removed as well.
    * @param sessionId The unique browser identifier
    */
   public void cleanup(String sessionId)
   {
      if (sessionId == null) return;

      synchronized(httpConnections) {
         HttpPushHandler ph = (HttpPushHandler)httpConnections.remove(sessionId);
         if (ph == null) {
            // No error, may be invoked multiple times (e.g. form servlet and pingThread)
            log.trace(ME, "Can't cleanup browser connection, your sessionId " + sessionId + " is unknown");
            return;
         }

         // Disconnect from browser
         ph.deinitialize();

         if (httpConnections.isEmpty()) {
            // Logout from xmlBlaster if no browser uses this connection anymore
            if (xmlBlasterConnection != null) {
               BlasterHttpProxy.cleanupByLoginName(xmlBlasterConnection.getSessionName().getLoginName());
               xmlBlasterConnection.disconnect(null);
               log.info(ME, "Corba connection for '" + xmlBlasterConnection.getSessionName().getLoginName() + "' for browser with sessionId=" + sessionId + " removed");
               xmlBlasterConnection = null;
            }
         }
      }
      log.info(ME, "Browser connection with sessionId=" + sessionId + " removed");
   }


   public I_XmlBlasterAccess getXmlBlasterAccess()
   {
      return xmlBlasterConnection;
   }
   public String getLoginName()
   {
      return loginName;
   }

   /**
    * The HttpPushHandler handles and hides the persistent connection to the browser.
    */
   public void addHttpPushHandler( String sessionId, HttpPushHandler pushHandler ) throws XmlBlasterException
   {
      if( sessionId == null || pushHandler == null ) {
         log.warn(ME,"You shouldn't use null pointer: sessionId="+sessionId+"; pushHandler="+pushHandler);
         throw new XmlBlasterException(ME, "You shouldn't use null pointer: sessionId="+sessionId+"; pushHandler="+pushHandler);
      }

      synchronized(httpConnections) {
         httpConnections.put( sessionId, pushHandler );
      }
   }

   /**
    * @return The HttpPushHandler object or exception if not found
    */
   public final HttpPushHandler getHttpPushHandler( String sessionId ) throws XmlBlasterException
   {
      if (sessionId == null)
         throw new XmlBlasterException(ME+".BrowserLost", "Your sessionId is null");

      HttpPushHandler ph = (HttpPushHandler)httpConnections.get( sessionId );

      if (ph == null || ph.closed())
         throw new XmlBlasterException(ME+".BrowserLost", "Your sessionId '" + sessionId + "' is invalid");

      return ph;
   }

   /**
    * @return The HttpPushHandler object or null if not found
    */
   public final HttpPushHandler getHttpPushHandlerOrNull( String sessionId )
   {
      if (sessionId == null) return null;

      return (HttpPushHandler)httpConnections.get( sessionId );
   }

   /**
    * If you implement I_ProxyInterceptor and register it here,
    * your update() implementation is called and may manipulate the
    * received message from xmlBlaster before it is sent to the browser.
    * @param interceptor Your optional implementation
    */
   public void setProxyInterceptor( I_ProxyInterceptor interceptor )
   {
      this.interceptor = interceptor;
   }
}

