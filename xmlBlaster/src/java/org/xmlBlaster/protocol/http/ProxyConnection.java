/*------------------------------------------------------------------------------
Name:      ProxyConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: ProxyConnection.java,v 1.10 2000/03/29 16:33:35 kkrafft2 Exp $
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
 * there are multiple clients ...
 * <p />
 * If you want to change the default behavior,
 * you need to specify environment variables in the servlet configuration file,<br />
 * for JServ see /etc/httpd/conf/jserv/zone.properties,<br />
 * for jrun see jrun/jsm-default/services/jse/properties/servlets.properties.<br />
 * @version $Revision: 1.10 $
 * @author ruff@swand.lake.de
 */
public class ProxyConnection implements I_Callback
{
   private final String ME                              = "ProxyConnection";
   private String loginName                             = null;
   private CorbaConnection corbaConnection              = null;
   private Server       xmlBlaster                      = null;
   private Hashtable httpConnections                    = null;
   private ProxyInterceptor interceptor                 = null;


   /**
    **/
   public ProxyConnection(String loginName, String passwd) throws XmlBlasterException
   {
      this.loginName = loginName;
      if (Log.TRACE) Log.trace(ME, "Creating ProxyConnection ...");
      //establish connection to server
      corbaConnection = new CorbaConnection();
      String qos = "<qos></qos>";
      xmlBlaster = corbaConnection.login( loginName, passwd, qos, this);
      httpConnections = new Hashtable();
   }

   /**
    * Logout from xmlBlaster.
    * Better use cleanup(), which does everything
    */
   public boolean logout()
   {
      return corbaConnection.logout();
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
      s_arr[0] = new String(updateKey.toString());
      s_arr[1] = new String(content);
      s_arr[2] = new String(updateQoS.toString());
      if(interceptor != null) {
         s_arr = interceptor.update(s_arr[0], s_arr[1], s_arr[2]);
      }

      Log.trace(ME, "Update of "+httpConnections.size()+" http connections.");
      for( Enumeration e = httpConnections.elements(); e.hasMoreElements() ; )
        ((HttpPushHandler)e.nextElement()).update( s_arr[0], s_arr[1], s_arr[2] );
   }

   /**
    * Close connection to xmlBlaster and to browser
    */
   public void cleanup() throws IOException
   {
      corbaConnection.logout();

      for( Enumeration e = httpConnections.elements(); e.hasMoreElements() ; )
        ((HttpPushHandler)e.nextElement()).cleanup();
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
   public void addHttpPushHandler( String sessionId, HttpPushHandler pushHandler )
   {
      if( sessionId == null || pushHandler == null )
         Log.warning(ME,"You shouldn't use null pointer: sessionId="+sessionId+"; pushHandler="+pushHandler);
      httpConnections.put( sessionId, pushHandler );
   }
   public void removeHttpPushHandler( String sessionId, HttpPushHandler pushHandler )
   {
      HttpPushHandler ph = (HttpPushHandler)httpConnections.get( sessionId );
      if( pushHandler == ph )
        httpConnections.remove( sessionId );
      //ansonsten wurde der PusHandler durch einen alten überschieben.
   }
   public HttpPushHandler getHttpPushHandler( String sessionId )
   {
      return (HttpPushHandler)httpConnections.get( sessionId );
   }

   public void setProxyInterceptor( ProxyInterceptor interceptor )
   {
      this.interceptor = interceptor;
   }
}

