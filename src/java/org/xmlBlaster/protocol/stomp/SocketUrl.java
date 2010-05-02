/*------------------------------------------------------------------------------
Name:      SocketUrl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SocketUrl knows how to parse the URL notation of our STOMP protocol
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.stomp;

import java.net.InetAddress;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.property.PropInt;
import org.xmlBlaster.util.property.PropString;
import org.xmlBlaster.util.qos.address.AddressBase;

/**
 * This knows how to parse the URL notation of the STOMP protocol. 
 * It holds the hostname and the port.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.stomp.html">The protocol.stomp requirement</a>
 */
public class SocketUrl
{
   private String ME = "SocketUrl";
   private Global glob;
   private static Logger log = Logger.getLogger(SocketUrl.class.getName());
   /** The string representation like "192.168.1.1", useful if multihomed computer */
   private String hostname = null;
   private PropString hostnameProp;
   /** xmlBlaster server host */
   private java.net.InetAddress inetAddress = null;
   /** The port */
   private int port = SocketUrl.DEFAULT_SERVER_PORT;
   private PropInt portProp;
   private boolean isLocal = false;
   // Default port of xmlBlaster socket local side is 0 (choosen by OS) */
   //public static final int DEFAULT_SERVER_CBPORT = 0;
   /** Default port of xmlBlaster stomp server is 61613 */
   public static final int DEFAULT_SERVER_PORT = 61613;
   private boolean isEnforced = false;

   /**
    * @param hostname if null or empty the local IP will be used
    * @param port any port, not checked
    */
   public SocketUrl(Global glob, String hostname, int port) throws XmlBlasterException {
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
    * @param url e.g. "stomp://127.168.1.1:61613" or only "127.168.1.1:61613" or "" (choose default settings)
    * @exception XmlBlasterException if url is null or invalid
    */
   public SocketUrl(Global glob, String url) throws XmlBlasterException {
      this.glob = glob;

      parse(url);
      createInetAddress(); // first check
   }

   /**
    * Extract "hostname" and "port" from environment, if not found use the local host
    * for hostname and the default port 61613. 
    * <br />
    * Updates the raw address from AddressBase
    */
   public SocketUrl(Global glob, AddressBase address) throws XmlBlasterException {
      this(glob, address, false, SocketUrl.DEFAULT_SERVER_PORT);
   }

   /**
    * Extract hostname and port from environment, if not found use the local host
    * for hostname and the given default port (usually ExecutorBase.DEFAULT_SERVER_PORT=61613). 
    * <br />
    * Updates the raw address from AddressBase if isLocal==false
    * @param isLocal If local is set to true "localHostname" and "localPort" will be extracted
    */
   public SocketUrl(Global glob, AddressBase address, boolean isLocal, int defaultServerPort) throws XmlBlasterException {
      this.glob = glob;


      if (isLocal) {
         this.isLocal = true;
         this.portProp = address.getEnv("localPort", defaultServerPort); 
         this.port = this.portProp.getValue();
         this.hostnameProp = address.getEnv("localHostname", glob.getLocalIP());
         this.hostname = this.hostnameProp.getValue();
      }
      else {
         if (address.getRawAddress() != null && address.getRawAddress().length() > 2) {
            parse(address.getRawAddress());
            createInetAddress(); // first check
            this.isEnforced = true;
            return;
         }
         this.portProp = address.getEnv("port", defaultServerPort); 
         this.port = this.portProp.getValue();
         this.hostnameProp = address.getEnv("hostname", glob.getLocalIP());
         this.hostname = this.hostnameProp.getValue();
         address.setRawAddress(getUrl());
      }
      createInetAddress(); // first check
   }
   
   /** @return true if host or port was given by user configuration, false if default is chosen */
   public boolean isEnforced() {
      if (this.isEnforced) return true;
      boolean enforcedHost = (this.hostnameProp == null) ? false : !this.hostnameProp.isDefault();
      boolean enforcedPort = (this.portProp == null) ? false : !this.portProp.isDefault();
      return enforcedHost || enforcedPort;
   }

   public String getHostname() {
      return this.hostname;
   }

   public int getPort() {
      return this.port;
   }

   /**
    * @return for example "stomp://myServer.com:61613"
    */
   public String getUrl() {
      return "stomp://" + this.hostname + ":" + this.port;
   }

   public String toString() {
      return getUrl();
   }

   private void parse(String url) throws XmlBlasterException {
      if (url == null) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "Your given stomp url '" + url + "' is invalid");
      }

      String urlLowerCase = url.toLowerCase();
      if (urlLowerCase.startsWith("stomp://")) {
         url = url.substring("stomp://".length());
      }
      else if (urlLowerCase.startsWith("stomp:")) {
         url = url.substring("stomp:".length());
      }

      int pos = url.indexOf(":");
      String portStr = null;
      if (pos > -1) {
         this.hostname = url.substring(0, pos);
         portStr = url.substring(pos+1);
         if (portStr != null && portStr.length() > 0) {
            pos = portStr.indexOf("/");
            if (pos > -1) {
               portStr = portStr.substring(0, pos); // strip path e.g. "stomp://myHost:8000/path/subpath"
            }
            try {
               this.port = (new Integer(portStr)).intValue();
            }
            catch (NumberFormatException e) {
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, "Your given stomp url '" + url + "' port '" + portStr + "' is invalid");
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
         Thread.dumpStack();
         String txt = "The hostname [" + this.hostname + "] of url '" + getUrl() + "' is invalid, check your '-plugin/stomp/" +
                       (isLocal ? "localHostname" : "hostname") + " <ip>' setting: " + e.toString();
         log.warning(txt);
         // throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, txt);
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, txt);
      }
   }

   public boolean equals(SocketUrl other) {
      if (this.port == other.getPort() && getInetAddress().equals(other.getInetAddress())) {
         return true;
      }
      return false;
   }


   /** java org.xmlBlaster.protocol.stomp.SocketUrl stomp://localhost:7609 */
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

