/*------------------------------------------------------------------------------
Name:      SocketUrl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SocketUrl knows how to parse the URL notation of our SOCKET protocol
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.xmlBlaster.util.Global;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.qos.address.AddressBase;
import java.net.InetAddress;

/**
 * This knows how to parse the URL notation of our SOCKET protocol. 
 * It holds the hostname and the port.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The protocol.socket requirement</a>
 * @see org.xmlBlaster.test.classtest.SocketUrlTest
 */
public class SocketUrl
{
   private String ME = "SocketUrl";
   private Global glob;
   private LogChannel log;
   /** The string representation like "192.168.1.1", useful if multihomed computer */
   private String hostname = null;
   /** xmlBlaster server host */
   private java.net.InetAddress inetAddress = null;
   /** The port */
   private int port = ExecutorBase.DEFAULT_SERVER_PORT;
   private boolean isLocal = false;

   /**
    * @param hostname if null or empty the local IP will be used
    * @param port any port, not checked
    */
   public SocketUrl(Global glob, String hostname, int port) throws XmlBlasterException {
      this.glob = glob;
      this.log = this.glob.getLog("socket");
      this.hostname = hostname;
      if (this.hostname == null || this.hostname.length() < 1) {
         this.hostname = glob.getLocalIP();
      }
      this.port = port;
      createInetAddress(); // first check
   }

   /**
    * Parse the given url. 
    * @param url e.g. "socket://127.168.1.1:7607" or only "127.168.1.1:7607" or "" (choose default settings)
    * @exception XmlBlasterException if url is null or invalid
    */
   public SocketUrl(Global glob, String url) throws XmlBlasterException {
      this.glob = glob;
      this.log = this.glob.getLog("socket");
      parse(url);
      createInetAddress(); // first check
   }

   /**
    * Extract "hostname" and "port" from environment, if not found use the local host
    * for hostname and the default port 7607. 
    * <br />
    * Updates the raw address from AddressBase
    */
   public SocketUrl(Global glob, AddressBase address) throws XmlBlasterException {
      this(glob, address, false, ExecutorBase.DEFAULT_SERVER_PORT);
   }

   /**
    * Extract hostname and port from environment, if not found use the local host
    * for hostname and the given default port (usually ExecutorBase.DEFAULT_SERVER_PORT=7607). 
    * <br />
    * Updates the raw address from AddressBase if isLocal==false
    * @param isLocal If local is set to true "localHostname" and "localPort" will be extracted
    */
   public SocketUrl(Global glob, AddressBase address, boolean isLocal, int defaultServerPort) throws XmlBlasterException {
      this.glob = glob;
      this.log = this.glob.getLog("socket");

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
         this.port = address.getEnv("port", defaultServerPort).getValue();
         this.hostname = address.getEnv("hostname", glob.getLocalIP()).getValue();
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
    * @return for example "socket://myServer.com:7607"
    */
   public String getUrl() {
      return "socket://" + this.hostname + ":" + this.port;
   }

   public String toString() {
      return getUrl();
   }

   private void parse(String url) throws XmlBlasterException {
      if (url == null) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "Your given SOCKET url '" + url + "' is invalid");
      }

      String urlLowerCase = url.toLowerCase();
      if (urlLowerCase.startsWith("socket://")) {
         url = url.substring("socket://".length());
      }
      else if (urlLowerCase.startsWith("socket:")) {
         url = url.substring("socket:".length());
      }

      int pos = url.indexOf(":");
      String portStr = null;
      if (pos > -1) {
         this.hostname = url.substring(0, pos);
         portStr = url.substring(pos+1);
         if (portStr != null && portStr.length() > 0) {
            pos = portStr.indexOf("/");
            if (pos > -1) {
               portStr = portStr.substring(0, pos); // strip path e.g. "socket://myHost:8000/path/subpath"
            }
            try {
               this.port = (new Integer(portStr)).intValue();
            }
            catch (NumberFormatException e) {
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "Your given SOCKET url '" + url + "' port '" + portStr + "' is invalid");
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
         String txt = "The hostname [" + this.hostname + "] of url '" + getUrl() + "' is invalid, check your '-plugin/socket/" +
                       (isLocal ? "localHostname" : "hostname") + " <ip>' setting: " + e.toString();
         log.warn(ME, txt);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, txt);
      }
   }

   public boolean equals(SocketUrl other) {
      if (this.port == other.getPort() && getInetAddress().equals(other.getInetAddress())) {
         //log.error(ME, "DEBUG ONLY: EQUAL: " + getUrl() + " - " + other.getUrl());
         return true;
      }
      //log.error(ME, "DEBUG ONLY: NOT EQUAL: " + getUrl() + " - " + other.getUrl());
      return false;
   }

   /** java org.xmlBlaster.protocol.socket.SocketUrl socket://localhost:7609 */
   public static void main(String[] args) {
      try {
         if (args.length > 0) {
            SocketUrl s = new SocketUrl(Global.instance(), args[0]);
            System.out.println(args[0] + " -> " + s.getUrl() + " hostname=" + s.getHostname() + " port=" + s.getPort());
         }
      }
      catch (Throwable e) {
         System.out.println("ERROR: " + e.toString());
      }
   }
}

