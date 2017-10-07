/*------------------------------------------------------------------------------
Name:      XmlRpcUrl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   XmlRpcUrl knows how to parse the URL notation of our XMLRPC protocol
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.HelperIPv6And4;

import java.util.logging.Logger;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.qos.address.AddressBase;

import java.net.InetAddress;

/**
 * This knows how to parse the URL notation of our XMLRPC protocol. 
 * It holds the hostname and the port in the form "http://myServer.com:8080/".
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.xmlrpc.html">The protocol.xmlrpc requirement</a>
 * @see org.xmlBlaster.test.classtest.XmlRpcUrlTest
 */
public class XmlRpcUrl
{
   private String ME = "XmlRpcUrl";
   private Global glob;
   private static Logger log = Logger.getLogger(XmlRpcUrl.class.getName());
   /** The string representation like "192.168.1.1", useful if multihomed computer */
   private String hostname;
   /** xmlBlaster server host */
   private java.net.InetAddress inetAddress;
   /** The port */
   private int port = XmlRpcDriver.DEFAULT_HTTP_PORT;
   private boolean isLocal;
   private String path;
   private String protocol = "http://";
   
   /**
    * @param hostname if null or empty the local IP will be used
    * @param port any port, not checked
    */
   public XmlRpcUrl(Global glob, String hostname, int port, String path) throws XmlBlasterException {
      this.glob = glob;

      this.hostname = hostname;
      if (this.hostname == null || this.hostname.length() < 1) {
         this.hostname = glob.getLocalIP();
      }
      this.port = port;
      this.path = path;
      createInetAddress(); // first check
   }

   /**
    * Parse the given url. 
    * @param url e.g. "http://127.168.1.1:8080"
    * @exception XmlBlasterException if url is null or invalid
    */
   public XmlRpcUrl(Global glob, String url) throws XmlBlasterException {
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
   public XmlRpcUrl(Global glob, AddressBase address) throws XmlBlasterException {
      this(glob, address, false, XmlRpcDriver.DEFAULT_HTTP_PORT);
   }

   /**
    * Extract hostname and port from environment, if not found use the local host
    * for hostname and the given default port (usually XmlRpcDriver.DEFAULT_HTTP_PORT=8080). 
    * <br />
    * Updates the raw address from AddressBase if isLocal==false
    * <br />
    * NOTE: If address.getRawAddress() is filled this has precedence. 
    * @param isLocal If local is set to true "localHostname" and "localPort" will be extracted
    */
   public XmlRpcUrl(Global glob, AddressBase address, boolean isLocal, int defaultServerPort) throws XmlBlasterException {
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
         
         String tmp = address.getEnv("hostname", glob.getLocalIP()).getValue();
         if (tmp != null && (tmp.indexOf("http://") > -1 || tmp.indexOf("https://") > -1)) {
            address.setRawAddress(tmp);
            parse(address.getRawAddress());
            createInetAddress(); // first check
            return;
         }
         this.hostname = tmp;
         this.port = address.getEnv("port", defaultServerPort).getValue();
         this.path = address.getEnv("path", null).getValue();
         boolean isSSL = address.getEnv("SSL", false).getValue();
         if (isSSL)
            protocol = "https://";
         address.setRawAddress(getUrl());
      }
      createInetAddress(); // first check
   }

   public String getHostname() {
      return this.hostname;
   }

   public int getPort() {
      return this.port;
   }

   /**
    * Change the port
    */
   public void setPort(int port) {
      this.port = port;
   }

   /**
    * @return for example "http://myServer.com:8080/"
    */
   public String getUrl() {
      String tmp = protocol + hostname + ":" + port + "/";
      if (path != null)
         tmp += path;
      return tmp;
   }

   public String toString() {
      return getUrl();
   }

   public String getProtocol() {
      return protocol;
   }
   
   private void parse(String url) throws XmlBlasterException {
      if (url == null) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "Your given XMLRPC url '" + url + "' is invalid");
      }

      String urlLowerCase = url.toLowerCase();
      if (urlLowerCase.startsWith("http://")) {
         url = url.substring("http://".length());
      }
      else if (urlLowerCase.startsWith("http:")) {
         url = url.substring("http:".length());
      }
      if (urlLowerCase.startsWith("https://")) {
         url = url.substring("https://".length());
         protocol = "https://";
      }

      int pos = HelperIPv6And4.getIPv6OrIPv4PortPosition(url);
      String portStr = null;
      if (pos > -1) {
         this.hostname = url.substring(0, pos);
         portStr = url.substring(pos+1);
         if (portStr != null && portStr.length() > 0) {
            pos = portStr.indexOf("/");
            if (pos > -1) {
               if (pos < portStr.length() -1)
                  path = portStr.substring(pos+1);
               portStr = portStr.substring(0, pos); // strip path e.g. "http://myHost:8080/path/subpath"
            }
            try {
               this.port = (new Integer(portStr)).intValue();
            }
            catch (NumberFormatException e) {
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "Your given XMLRPC url '" + url + "' port '" + portStr + "' is invalid");
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
      } 
      catch(java.net.UnknownHostException e) {
         Thread.dumpStack();
         String txt = "The hostname [" + this.hostname + "] of url '" + getUrl() + "' is invalid, check your '-plugin/xmlrpc/" +
                       (isLocal ? "localHostname" : "hostname") + " <ip>' setting: " + e.toString();
         log.warning(txt);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, txt);
      }
      
   }

   public boolean equals(XmlRpcUrl other) {
      if (this.port == other.getPort() && getInetAddress().equals(other.getInetAddress())) {
         //log.error(ME, "DEBUG ONLY: EQUAL: " + getUrl() + " - " + other.getUrl());
         return true;
      }
      //log.error(ME, "DEBUG ONLY: NOT EQUAL: " + getUrl() + " - " + other.getUrl());
      return false;
   }

   /** java org.xmlBlaster.protocol.xmlrpc.XmlRpcUrl http://localhost:8080 */
   public static void main(String[] args) {
      try {
         if (args.length > 0) {
            XmlRpcUrl s = new XmlRpcUrl(Global.instance(), args[0]);
            System.out.println(args[0] + " -> " + s.getUrl() + " hostname=" + s.getHostname() + " port=" + s.getPort());
         }
      }
      catch (Throwable e) {
         System.out.println("ERROR: " + e.toString());
      }
   }
   
}

