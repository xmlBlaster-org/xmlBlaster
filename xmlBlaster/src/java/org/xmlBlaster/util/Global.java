/*------------------------------------------------------------------------------
Name:      Global.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Properties for xmlBlaster, using org.jutils
Version:   $Id: Global.java,v 1.7 2002/05/02 12:36:40 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.JUtilsException;
import org.jutils.init.Property;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.helper.Address;

import java.util.Properties;

import java.applet.Applet;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.net.MalformedURLException;
import java.io.IOException;


/**
 * Global variables to avoid singleton. 
 */
public class Global
{
   private final static String ME = "Global";
   private String ip_addr = null;

   /**
    * The IANA registered xmlBlaster port,
    * see <a href="http://www.iana.org/assignments/port-numbers">IANA Port Numbers</a>
    * and <a href="http://andrew.triumf.ca/cgi-bin/port">Network Service Query</a>.
    * <pre>
    *  xmlblaster      3412/tcp   xmlBlaster
    *  xmlblaster      3412/udp   xmlBlaster
    *  #                          Marcel Ruff <ruff@swand.lake.de> February 2002
    * </pre>
    */
   public static final int XMLBLASTER_PORT = 3412;

   private String[] args;
   protected XmlBlasterProperty property = new XmlBlasterProperty();
   protected Log log = new Log();
   private Address bootstrapAddress = null;
   private Map nativeCallbackDriverMap = Collections.synchronizedMap(new HashMap());


   public Global()
   {
      this.args = new String[0];
   }

   public Global(String[] args)
   {
      init(args);
   }

   public final XmlBlasterProperty getProperty()
   {
      return property;
   }

   public final Log getLog()
   {
      return log;
   }

   /**
    * The command line arguments. 
    * @return the arguments, is never null
    */
   public final String[] getArgs()
   {
      return this.args;
   }

   /**
    * @return 1 Show usage, 0 OK, -1 error
    */
   public int init(String[] args)
   {
      this.args = args;
      if (this.args == null)
         this.args = new String[0];
      try {
         // XmlBlasterProperty.addArgs2Props(this.args); // enforce that the args are added to the xmlBlaster.properties hash table
         boolean showUsage = XmlBlasterProperty.init(this.args);  // initialize
         if (showUsage) return 1;
         return 0;
      } catch (JUtilsException e) {
         System.err.println(ME + " ERROR: " + e.toString()); // Log probably not initialized yet.
         return -1;
      }
   }

   /**
    * The key is the protocol and the address to access the callback instance. 
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getAddress()'
    * @return The instance of the protocol callback driver or null if not known
    */
   public final I_CallbackDriver getNativeCallbackDriver(String key)
   {
      return (I_CallbackDriver)nativeCallbackDriverMap.get(key);
   }

   /**
    * The key is the protocol and the address to access the callback instance. 
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getAddress()'
    * @param The instance of the protocol callback driver
    */
   public final void addNativeCallbackDriver(String key, I_CallbackDriver driver)
   {
      nativeCallbackDriverMap.put(key, driver);
   }

   /**
    * The key is the protocol and the address to access the callback instance. 
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getAddress()'
    * @param The instance of the protocol callback driver
    */
   public final void removeNativeCallbackDriver(String key)
   {
      nativeCallbackDriverMap.remove(key);
   }

   /**
    * Returns the address of the xmlBlaster internal http server. 
    * <p />
    * Is configurable with
    * <pre>
    *   -hostname myhost.mycompany.com   (or the raw IP)
    *   -port 3412
    * </pre>
    * Defaults to the local machine and the IANA xmlBlaster port.<br />
    * You can set "-port 0" to avoid starting the internal HTTP server
    */
   public final Address getBootstrapAddress() {
      if (bootstrapAddress == null) {
         bootstrapAddress = new Address(this);
         boolean supportOldStyle = true; // for a while we support the old style -iorHost and -iorPort
         if (supportOldStyle) {
            String iorHost = getProperty().get("iorHost", getLocalIP());
            int iorPort = getProperty().get("iorPort", XMLBLASTER_PORT);
            bootstrapAddress.setHostname(getProperty().get("hostname", iorHost));
            bootstrapAddress.setPort(getProperty().get("port", iorPort));
         }
         else {
            bootstrapAddress.setHostname(getProperty().get("hostname", getLocalIP()));
            bootstrapAddress.setPort(getProperty().get("port", XMLBLASTER_PORT));
         }
         bootstrapAddress.setAddress("http://" + bootstrapAddress.getHostname() + ":" + bootstrapAddress.getPort());
      }
      return bootstrapAddress;
   }

   /**
    * Access the xmlBlaster internal HTTP server and download the requested path. 
    * <p />
    * Currently we only use it for CORBA IOR download. To avoid the name service,
    * one can access the AuthServer IOR directly
    * using a http connection.
    * @param false Suppress error logging when server not found
    * @param urlPath The part after the host:port, from an URL "http://myhost.com:3412/AuthenticationService.ior"
    *                urlPath is "AuthenticationService.ior"
    */
   public String accessFromInternalHttpServer(String urlPath, boolean verbose) throws XmlBlasterException
   {
      Address addr = getBootstrapAddress();
      if (Log.CALL) Log.call(ME, "Trying internal http server on " + addr.getHostname() + ":" + addr.getPort());
      try {
         if (urlPath != null && urlPath.startsWith("/") == false)
            urlPath = "/" + urlPath;

         java.net.URL nsURL = new java.net.URL("http", addr.getHostname(), addr.getPort(), urlPath);
         java.io.InputStream nsis = nsURL.openStream();
         byte[] bytes = new byte[4096];
         java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
         int numbytes;
         for (int ii=0; ii<20 && (nsis.available() <= 0); ii++) {
            if (Log.TRACE) Log.trace(ME, "XmlBlaster on host " + addr.getHostname() + " and port " + addr.getPort() + " returns empty data, trying again after sleeping 10 milli ...");
            org.jutils.runtime.Sleeper.sleep(10); // On heavy logins, sometimes available() returns 0, but after sleeping it is OK
         }
         while (nsis.available() > 0 && (numbytes = nsis.read(bytes)) > 0) {
            bos.write(bytes, 0, numbytes);
         }
         nsis.close();
         String data = bos.toString();
         if (Log.TRACE) Log.trace(ME, "Retrieved http data='" + data + "'");
         return data;
      }
      catch(MalformedURLException e) {
         String text = "XmlBlaster not found on host " + addr.getHostname() + " and port " + addr.getPort() + ".";
         Log.error(ME, text + e.toString());
         throw new XmlBlasterException(ME+"NoHttpServer", text);
      }
      catch(IOException e) {
         if (verbose) Log.warn(ME, "XmlBlaster not found on host " + addr.getHostname() + " and port " + addr.getPort() + ": " + e.toString());
         throw new XmlBlasterException(ME+"NoHttpServer", "XmlBlaster not found on host " + addr.getHostname() + " and port " + addr.getPort() + ".");
      }
   }

   /**
    * The IP address where we are running. 
    * <p />
    * You can specify the local IP address with e.g. -hostname 192.168.10.1
    * on command line, useful for multi-homed hosts.
    *
    * @return The local IP address, defaults to '127.0.0.1' if not known.
    */
   public final String getLocalIP()
   {
      if (ip_addr == null) {
         ip_addr = getBootstrapAddress().getHostname();
         if (ip_addr == null || ip_addr.length() < 1) {
            try {
               ip_addr = java.net.InetAddress.getLocalHost().getHostAddress(); // e.g. "204.120.1.12"
            } catch (java.net.UnknownHostException e) {
               Log.warn(ME, "Can't determine local IP address, try e.g. '-hostname 192.168.10.1' on command line: " + e.toString());
            }
            if (ip_addr == null) ip_addr = "127.0.0.1";
         }
      }
      return ip_addr;
   }

   /**
    * For testing only
    * <p />
    * java org.xmlBlaster.util.Global -Persistence.Dummy true -info true
    */
   public static void main(String args[])
   {
      String ME = "Global";
      Global glob = new Global(args);
      Log.info(ME, "Persistence.Dummy=" + glob.getProperty().get("Persistence.Dummy", false));
   }
}
