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
import org.xmlBlaster.util.def.ErrorCode;
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
   private static boolean first = true;

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

   /**
    * Helper to create a server side SSL socket, uses reflection to compile with JDK 1.3
    * SSL support can't be used with a standard JDK 1.3
    * <p />
    * Setup:
    * <pre>
keytool -genkey -keystore testStore -keyalg RSA   (using password 'testtest')

java org.xmlBlaster.Main -plugin/socket/SSL true -plugin/socket/keyStore testStore -plugin/socket/keyStorePassword testtest  

java javaclients.HelloWorldPublish -plugin/socket/SSL true -plugin/socket/keyStore testStore -plugin/socket/keyStorePassword testtest
    * </pre>
    * @param backlog Socket parameter
    * @param address The configuration environment
    */
   public java.net.ServerSocket createServerSocketSSL(int backlog, AddressBase address) throws XmlBlasterException {

      /*
       TODO: Make trustStore,keyStore etc. static as they never change after startup, to avoid
             reparsing environment 
      */
      String trustStore = address.getEnv("trustStore", (String)System.getProperty("javax.net.ssl.trustStore", "")).getValue();
      if (trustStore != "") {
         log.info(ME, "SSL server socket enabled, trustStore="+trustStore);
         System.setProperty("javax.net.ssl.trustStore", trustStore);
      }
      else {
         log.warn(ME, "SSL server socket is configured but no trustStore is specified, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html#SSL");
      }

      String trustStorePassword = address.getEnv("trustStorePassword", (String)System.getProperty("javax.net.ssl.trustStorePassword", "")).getValue();
      if (trustStorePassword != "") {
      }
      else {
         log.trace(ME, "SSL client socket is configured but no trustStorePassword is specified, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html#SSL");
         System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);
      }
      
      String keyStore = address.getEnv("keyStore", System.getProperty("javax.net.ssl.keyStore", "")).getValue();
      if (keyStore != "") {
         log.info(ME, "SSL server socket enabled, keyStore="+keyStore);
         System.setProperty("javax.net.ssl.keyStore", keyStore);
      }
      else {
         log.warn(ME, "SSL server socket is enabled but no keyStore is specified, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html#SSL");
      }

      String keyStorePassword = address.getEnv("keyStorePassword", System.getProperty("javax.net.ssl.keyStorePassword", "")).getValue();
      if (keyStorePassword != "") {
         System.setProperty("javax.net.ssl.keyStorePassword", keyStorePassword);
      }
      else {
         log.warn(ME, "SSL server socket is enabled but no keyStorePassword is specified, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html#SSL");
      }

      java.net.ServerSocket returnSock = null;

      try  {
         // Use Reflection because of JDK 1.3 has no SSL
         Class clazz = java.lang.Class.forName("javax.net.ssl.SSLServerSocketFactory");
         Class[] paramCls = new Class[0];
         Object[] params = new Object[0];
         java.lang.reflect.Method method = clazz.getMethod("getDefault", paramCls);
         Object socketFactory = method.invoke(clazz, params); // Returns "SSLServerSocketFactory"

         paramCls = new Class[] {
            int.class,                     // port
            int.class,                     // backlog
            java.net.InetAddress.class,    // address
         };
         params = new Object[] {
            new Integer(getPort()),
            new Integer(backlog),
            getInetAddress(),
         };
         method = clazz.getMethod("createServerSocket", paramCls);
         //method = socketFactory.getClass().getMethod("createServerSocket", paramCls);
         Object serverSocket = method.invoke(socketFactory, params);  // Returns "SSLServerSocket"


         paramCls = new Class[] { boolean.class };
         params = new Object[] {  Boolean.FALSE };
         // serverSocket: can not access a member of class com.sun.net.ssl.internal.ssl.SSLServerSocketImpl with modifiers "public"
         // so we force access to the base class:
         Class clazzI = java.lang.Class.forName("javax.net.ssl.SSLServerSocket");
         method = clazzI.getMethod("setNeedClientAuth", paramCls);
         method.invoke(serverSocket, params);

         returnSock = (java.net.ServerSocket)serverSocket;

         /* JDK 1.4 and higher:
            javax.net.ssl.SSLServerSocket sock = (javax.net.ssl.SSLServerSocket)
                    javax.net.ssl.SSLServerSocketFactory.getDefault().createServerSocket(getPort(), backlog, getInetAddress());
            sock.setNeedClientAuth(false);
            returnSock = sock;
         */
      }
      catch (Exception e) {
         log.trace(ME, "Can't switch on SSL socket: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_SSLSOCKET, ME, 
                        "Can't switch on SSL socket, check your keyStore and keyStorePassword or socket configuration", e);
      }
      finally {
         System.setProperty("javax.net.ssl.keyStore", "");
         System.setProperty("javax.net.ssl.keyStorePassword", "");
      }
      return returnSock;
   }

   /**
    * Helper to create a SSL socket, uses reflection to compile with JDK 1.3
    * SSL support can't be used with a standard JDK 1.3
    * @param localSocketUrl null or a configured local socket setting
    * @param address The configuration environment
    */
   public java.net.Socket createSocketSSL(SocketUrl localSocketUrl, AddressBase address) throws XmlBlasterException {

      /*
       NOTE: In a cluster environment you may have established a SSL server socket before.
       These settings are still valid when we later are a SSL client to a remote cluster node
       in which case no additional settings are needed here (they are actually ignored by the JDK ssl implementation)
      */

      // The trustStore file can be identical to the server side keyStore file:
      String trustStore = address.getEnv("trustStore", (String)System.getProperty("javax.net.ssl.trustStore", "")).getValue();
      String pass = address.getEnv("trustStorePassword", (String)System.getProperty("javax.net.ssl.trustStorePassword", "")).getValue();
      if (trustStore != "") {
         if (first) {
            log.info(ME, "SSL client socket enabled, trustStore="+trustStore);
            first = false;
         }
      }
      else {
         if (log.TRACE) log.trace(ME, "SSL client socket is configured but no trustStore is specified we now check your keyStore setting ..., see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html#SSL");
         // Reuse a server store if one is found
         trustStore = address.getEnv("keyStore", (String)System.getProperty("javax.net.ssl.keyStore", "")).getValue();
      }
      if (pass != "") {
      }
      else {
         if (log.TRACE) log.trace(ME, "SSL client socket is configured but no trustStorePassword is specified we now check your keyStorePassword setting ..., see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html#SSL");
         // Reuse a server store if one is found
         pass = address.getEnv("keyStorePassword", (String)System.getProperty("javax.net.ssl.keyStorePassword", "")).getValue();
      }

      if (trustStore != "") {
         System.setProperty("javax.net.ssl.trustStore", trustStore);
      }
      if (pass != "") {
         System.setProperty("javax.net.ssl.trustStorePassword", pass);
      }

      java.net.Socket retSock = null;

      try  {
         // Use Reflection because of JDK 1.3 has no SSL
         Class clazz = java.lang.Class.forName("javax.net.ssl.SSLSocketFactory");
         Class[] paramCls = new Class[0];
         Object[] params = new Object[0];
         java.lang.reflect.Method method = clazz.getMethod("getDefault", paramCls);
         Object socketFactory = method.invoke(clazz, params); // Returns "SocketFactory"

         if (localSocketUrl != null && localSocketUrl.getPort() > -1) {
            paramCls = new Class[] {
               java.net.InetAddress.class,        // address
               int.class,                         // port
               java.net.InetAddress.class,        // localAddress
               int.class,                         // localPort
            };
            params = new Object[] {
               getInetAddress(),
               new Integer(getPort()),
               localSocketUrl.getInetAddress(),
               new Integer(localSocketUrl.getPort())
            };
         }
         else {
            paramCls = new Class[] {
               java.net.InetAddress.class,
               int.class,
            };
            params = new Object[] {
               getInetAddress(),
               new Integer(getPort()),
            };
         }

         method = socketFactory.getClass().getMethod("createSocket", paramCls);
         retSock = (java.net.Socket)method.invoke(socketFactory, params);

         /* JDK 1.4 and higher:
            if (localSocketUrl != null && localSocketUrl.getPort() > -1)
               retSock = javax.net.ssl.SSLSocketFactory.getDefault().createSocket(getInetAddress(),
                        getPort(), localSocketUrl.getInetAddress(), localSocketUrl.getPort());
            else
               retSock = javax.net.ssl.SSLSocketFactory.getDefault().createSocket(getInetAddress(), getPort());
         */
      }
      catch (Exception e) {
         log.trace(ME, "Can't switch on SSL socket: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, 
                        "SSL XmlBlaster server is unknown, '-dispatch/connection/plugin/socket/hostname=<ip>'", e);
      }
      finally {
         System.setProperty("javax.net.ssl.trustStore", "");
         System.setProperty("javax.net.ssl.trustStorePassword", "");
      }
      return retSock;
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

