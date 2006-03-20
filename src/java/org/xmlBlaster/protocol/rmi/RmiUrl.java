/*------------------------------------------------------------------------------
Name:      RmiUrl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   RmiUrl knows how to parse the URL notation of our RMI protocol
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.xmlBlaster.util.Global;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.qos.address.AddressBase;
import java.net.InetAddress;

/**
 * This knows how to parse the URL notation of our RMI protocol. 
 * It holds the hostname and the port.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.rmi.html">The protocol.rmi requirement</a>
 * @see org.xmlBlaster.test.classtest.RmiUrlTest
 */
public class RmiUrl
{
   private String ME = "RmiUrl";
   private Global glob;
   private static Logger log = Logger.getLogger(RmiUrl.class.getName());
   /** The string representation like "192.168.1.1", useful if multihomed computer */
   private String hostname;
   /** xmlBlaster server host */
   private java.net.InetAddress inetAddress;
   /** The port */
   private int port = RmiDriver.DEFAULT_REGISTRY_PORT;
   private boolean isLocal = false;

   /**
    * @param hostname if null or empty the local IP will be used
    * @param port any port, not checked
    */
   public RmiUrl(Global glob, String hostname, int port) throws XmlBlasterException {
      this.glob = glob;

      this.hostname = hostname;
      if (this.hostname == null || this.hostname.length() < 1) {
         this.hostname = glob.getLocalIP();
      }
      this.port = port;
      createInetAddress(); // first check
   }

   /**
    * Parse the given url. 
    * @param url e.g. "rmi://127.168.1.1:8080"
    * @exception XmlBlasterException if url is null or invalid
    */
   public RmiUrl(Global glob, String url) throws XmlBlasterException {
      this.glob = glob;

      parse(url);
      createInetAddress(); // first check
   }

   /**
    * Extract "hostname" and "port" from environment, if not found use the local host
    * for hostname and the default port 8080. 
    * <br />
    * NOTE: If address.getRawAddress() is filled this has precedence. 
    * <br />
    * Updates the raw address from AddressBase
    */
   public RmiUrl(Global glob, AddressBase address) throws XmlBlasterException {
      this(glob, address, false, RmiDriver.DEFAULT_REGISTRY_PORT);
   }

   /**
    * Extract hostname and port from environment, if not found use the local host
    * for hostname and the given default port (usually RmiDriver.DEFAULT_REGISTRY_PORT=1099). 
    * <br />
    * Updates the raw address from AddressBase if isLocal==false
    * <br />
    * NOTE: If address.getRawAddress() is filled this has precedence. 
    * @param isLocal If local is set to true "localHostname" and "localPort" will be extracted
    */
   public RmiUrl(Global glob, AddressBase address, boolean isLocal, int defaultServerPort) throws XmlBlasterException {
      this.glob = glob;


      if (isLocal) {
         this.isLocal = true;
         this.port = address.getEnv("localPort", defaultServerPort).getValue();
         this.hostname = address.getEnv("localHostname", glob.getLocalIP()).getValue();
      }
      else {
         if (address.getRawAddress() != null && address.getRawAddress().length() > 2) {
            parse(address.getRawAddress());
            createInetAddress(); // first check
            return;
         }
         this.port = address.getEnv("registryPort", defaultServerPort).getValue();
         this.hostname = address.getEnv("hostname", glob.getLocalIP()).getValue();
         address.setRawAddress(getUrl());
      }
      createInetAddress(); // first check
   }

   public String getHostname() {
      return this.hostname;
   }

   public int getRegistryPort() {
      return this.port;
   }

   /**
    * Change the port
    */
   public void setRegistryPort(int port) {
      this.port = port;
   }

   /**
    * @return for example "rmi://myServer.com:8080/"
    */
   public String getUrl() {
      return "rmi://" + this.hostname + ":" + this.port + "/";
   }

   public String toString() {
      return getUrl();
   }

   private void parse(String url) throws XmlBlasterException {
      if (url == null) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "Your given RMI url '" + url + "' is invalid");
      }

      String urlLowerCase = url.toLowerCase();
      if (urlLowerCase.startsWith("rmi://")) {
         url = url.substring("rmi://".length());
      }
      else if (urlLowerCase.startsWith("rmi:")) {
         url = url.substring("rmi:".length());
      }

      int pos = url.indexOf(":");
      String portStr = null;
      if (pos > -1) {
         this.hostname = url.substring(0, pos);
         portStr = url.substring(pos+1);
         if (portStr != null && portStr.length() > 0) {
            pos = portStr.indexOf("/");
            if (pos > -1) {
               portStr = portStr.substring(0, pos); // strip path e.g. "rmi://myHost:8080/path/subpath"
            }
            try {
               this.port = (new Integer(portStr)).intValue();
            }
            catch (NumberFormatException e) {
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "Your given RMI url '" + url + "' port '" + portStr + "' is invalid");
            }
         }
      }
      else {
         this.hostname = url;
      }

      if (this.hostname == null || this.hostname.length() < 1) {
         this.hostname = glob.getLocalIP();
      }
   }

   public InetAddress getInetAddress() {
      return this.inetAddress;
   }

   private void createInetAddress() throws XmlBlasterException {
      try {
         this.inetAddress = java.net.InetAddress.getByName(this.hostname);
      } catch(java.net.UnknownHostException e) {
         Thread.currentThread().dumpStack();
         String txt = "The hostname [" + this.hostname + "] of url '" + getUrl() + "' is invalid, check your '-plugin/rmi/" +
                       (isLocal ? "localHostname" : "hostname") + " <ip>' setting: " + e.toString();
         log.warning(txt);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, txt);
      }
   }

   public boolean equals(RmiUrl other) {
      if (this.port == other.getRegistryPort() && getInetAddress().equals(other.getInetAddress())) {
         //log.error(ME, "DEBUG ONLY: EQUAL: " + getUrl() + " - " + other.getUrl());
         return true;
      }
      //log.error(ME, "DEBUG ONLY: NOT EQUAL: " + getUrl() + " - " + other.getUrl());
      return false;
   }

   /** java org.xmlBlaster.protocol.rmi.RmiUrl rmi://localhost:8080 */
   public static void main(String[] args) {
      try {
         if (args.length > 0) {
            RmiUrl s = new RmiUrl(Global.instance(), args[0]);
            System.out.println(args[0] + " -> " + s.getUrl() + " hostname=" + s.getHostname() + " port=" + s.getRegistryPort());
         }
      }
      catch (Throwable e) {
         System.out.println("ERROR: " + e.toString());
      }
   }
}

