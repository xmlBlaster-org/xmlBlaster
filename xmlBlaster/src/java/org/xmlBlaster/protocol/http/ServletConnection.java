/*------------------------------------------------------------------------------
Name:      ServletConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: ServletConnection.java,v 1.1 2000/02/21 09:46:05 ruff Exp $
Author:    Marcel Ruff ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.http;

import org.xmlBlaster.util.*;
import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;
import org.xmlBlaster.protocol.corba.authenticateIdl.*;

import java.io.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.Properties;


/**
 * This is a little helper class, helping a Java servlet client to connect
 * to xmlBlaster using IIOP (CORBA)
 * and remembering the session informations from the browser - servlet connection.
 * <p />
 * If you want to change the default behavior,
 * you need to specify environment variables in the servlet configuration file,<br />
 * for JServ see /etc/httpd/conf/jserv/zone.properties,<br />
 * for jrun see jrun/jsm-default/services/jse/properties/servlets.properties.<br />
 * Example: servlet.CallbackServletDriver.args=ORBagentAddr=192.168.1.1
 * @version $Revision: 1.1 $
 * @author ruff@swand.lake.de
 */
public class ServletConnection extends CorbaConnection
{
   private final String ME = "ServletConnection";
   private final ServletConfig conf;
   private final CallbackHandler callbackHandler;
   private final String sessionId;
   private final String loginName;


   /**
    * CORBA client access to xmlBlaster for <strong>servlets</strong>.
    * <p />
    * Use these environment settings for VisiBroker
    * <br />
    * <ul>
    *    <li>ORBservices</li>
    *    <li>SVCnameroot</li>
    *    <li>ORBagentAddr</li>
    *    <li>ORBagentPort</li>
    * </ul>
    * <br />
    * Usually you set these variables in the configuration file of your servlet engine (jrun, jserv etc.)
    *
    * @param conf   Servlet Handle
    * @param sessionId   Client sessionId (unique in browser-servlet context)
    * @param loginName   Client login name (unique in servlet-xmlBlaster context)
    */
   public ServletConnection(ServletConfig conf, CallbackHandler callbackHandler,
                            String sessionId, String loginName) throws IOException
   {
      this.sessionId = sessionId;
      this.loginName = loginName;
      this.conf = conf;
      this.callbackHandler = callbackHandler;

      if (Log.TRACE) Log.trace(ME, "Creating ServletConnection ...");


      // Setting the system properties
      Properties props = System.getProperties();
      java.util.Enumeration e = props.propertyNames();

      // These may be overwritten in /usr/local/apache/etc/servlet.properties
      // servlets.default.initArgs=DefaultTemplDir=/usr/local/apache/share/templates/,ORBagentAddr=192.168.1.1,ORBagentPort=14000,ORBservices=CosNaming,SVCnameroot=xmlBlaster-Authenticate

      if (conf.getInitParameter("ORBservices") != null) {
         props.put( "ORBservices", conf.getInitParameter("ORBservices"));
         if (Log.TRACE) Log.trace(ME, "  Found system parameter ORBservices=" + conf.getInitParameter("ORBservices"));
      }
      else
         props.put( "ORBservices", "CosNaming" );

      if (conf.getInitParameter("SVCnameroot") != null) {
         props.put( "SVCnameroot", conf.getInitParameter("SVCnameroot"));
         if (Log.TRACE) Log.trace(ME, "  Found system parameter SVCnameroot=" + conf.getInitParameter("SVCnameroot"));
      }
      else
         props.put( "SVCnameroot", "xmlBlaster-Authenticate" );

      if (conf.getInitParameter("ORBagentAddr") != null) {
         props.put( "ORBagentAddr", conf.getInitParameter("ORBagentAddr"));
         if (Log.TRACE) Log.trace(ME, "  Found system parameter ORBagentAddr=" + conf.getInitParameter("ORBagentAddr"));
      }
      else
         props.put( "ORBagentAddr", "192.168.1.1" );

      if (conf.getInitParameter("ORBagentPort") != null) {
         props.put( "ORBagentPort", conf.getInitParameter("ORBagentPort"));
         if (Log.TRACE) Log.trace(ME, "  Found system parameter ORBagentPort=" + conf.getInitParameter("ORBagentPort"));
      }
      else
         props.put( "ORBagentPort", "14000" );

      System.setProperties(props);

      if (Log.TRACE) {
         Log.trace(ME, "Known servlet system properties:");
         props = System.getProperties();
         e = props.propertyNames();
         while (e.hasMoreElements()) {
            String key = (String)e.nextElement();
            Log.trace(ME, key + "=" + System.getProperty(key));
         }
      }

      String agrs[] = null;
      orb = org.omg.CORBA.ORB.init(args, null);
   }

   /**
    * Logout from xmlBlaster.
    * Better use cleanup(), which does everything
    */
   public void logout()
   {
      super.logout(xmlBlaster);
   }

   /**
    * Invoking the callback to the browser.
    * <p />
    * This sends the message to the hidden frame in the browser
    */
   public void update(String msg) throws ServletException, IOException
   {
      callbackHandler.update(msg);
   }

   /**
    * Close connection to xmlBlaster and to browser
    */
   public void cleanup() throws IOException
   {
      callbackHandler.cleanup();
      logout();
   }

   public Server getXmlBlaster()
   {
      return xmlBlaster;
   }
   public String getSessionId()
   {
      return sessionId;
   }
   public String getLoginName()
   {
      return loginName;
   }
   public ServletConfig getServletConfig()
   {
      return conf;
   }
}

