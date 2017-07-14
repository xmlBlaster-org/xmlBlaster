/*------------------------------------------------------------------------------
Name:      SocketUrl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SocketUrl knows how to parse the URL notation of our SOCKET protocol
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.protocol.socket;

import org.xmlBlaster.util.Global;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.property.PropInt;
import org.xmlBlaster.util.property.PropString;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.FileLocator;

import java.net.InetAddress;
import java.net.URL;
import java.io.InputStream;

/**
 * This knows how to parse the URL notation of our SOCKET protocol. 
 * It holds the hostname and the port.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The protocol.socket requirement</a>
 * @see org.xmlBlaster.test.classtest.SocketUrlTest
 */
public class SocketUrl {

   /** used to synchronize the handling of System.properties */
   private final static Map<String,String> STORE_DATA = new HashMap<String, String>();
   private final static ReentrantLock STORE_LOCK = new ReentrantLock(false); // not fair

   private final static String TRUST_STORE_PROP = "javax.net.ssl.trustStore";
   private final static String TRUST_STORE_PWD_PROP = TRUST_STORE_PROP + "Password";
   private final static String KEY_STORE_PROP = "javax.net.ssl.keyStore";
   private final static String KEY_STORE_PWD_PROP = KEY_STORE_PROP + "Password";

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
   /** Flag to use TCP/IP */
   public static final boolean SOCKET_TCP = false;
   /** Flag to use UDP */
   public static final boolean SOCKET_UDP = true;
   // Default port of xmlBlaster socket local side is 0 (choosen by OS) */
   //public static final int DEFAULT_SERVER_CBPORT = 0;
   /** Default port of xmlBlaster socket server is 7607 */
   public static final int DEFAULT_SERVER_PORT = 7607;
   private static boolean firstKey = true;
   private static boolean firstTrust = true;
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
    * @param url e.g. "socket://127.168.1.1:7607" or only "127.168.1.1:7607" or "" (choose default settings)
    * @exception XmlBlasterException if url is null or invalid
    */
   public SocketUrl(Global glob, String url) throws XmlBlasterException {
      this.glob = glob;

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
      this(glob, address, false, SocketUrl.DEFAULT_SERVER_PORT);
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

   private final static void checkoutProp(Map<String,String> map, String propKey) {
	      String propVal = System.getProperty(propKey);
	      if (propVal != null)
	      map.put(propKey, propVal);
	   }

   private final static void checkinProp(Map<String,String> map, String propKey) {
	      String propVal = map.get(propKey);
	      if (propVal != null)
	         System.setProperty(propKey, propVal);
	   }

   /**
    * Invoked to serialize the setting of the SSL properties needed to create an SSL Socket with the XMLBLASTER properties.
    */
   public final static void checkoutSSLProps() {
      synchronized(STORE_LOCK) {
         STORE_LOCK.lock();
         STORE_DATA.clear();
         checkoutProp(STORE_DATA, KEY_STORE_PROP);
         checkoutProp(STORE_DATA, KEY_STORE_PWD_PROP);
         checkoutProp(STORE_DATA, TRUST_STORE_PROP);
         checkoutProp(STORE_DATA, TRUST_STORE_PWD_PROP);
	   }
   }

   public final static void checkinSSLProps() {
      synchronized(STORE_LOCK) {
         try {
            checkinProp(STORE_DATA, KEY_STORE_PROP);
            checkinProp(STORE_DATA, KEY_STORE_PWD_PROP);
            checkinProp(STORE_DATA, TRUST_STORE_PROP);
            checkinProp(STORE_DATA, TRUST_STORE_PWD_PROP);
         }
         finally {
            STORE_LOCK.unlock();
         }
      }
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
         Thread.dumpStack();
         String txt = "The hostname [" + this.hostname + "] of url '" + getUrl() + "' is invalid, check your '-plugin/socket/" +
                       (isLocal ? "localHostname" : "hostname") + " <ip>' setting: " + e.toString();
         log.warning(txt);
         // throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, txt);
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, txt);
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

      // checkoutSSLProps();
      /*
       TODO: Make trustStore,keyStore etc. static as they never change after startup, to avoid
             reparsing environment 
      */

      String trustStore = address.getEnv("trustStore", System.getProperty(TRUST_STORE_PROP, "")).getValue();
      if (trustStore.length() != 0) {
         log.info("SSL server socket enabled for " + address.getRawAddress() + ", trustStore="+trustStore);
         System.setProperty(TRUST_STORE_PROP, trustStore);
      }
      else {
         log.warning("SSL server socket is configured but no trustStore is specified, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html#SSL");
      }

      String trustStorePassword = address.getEnv("trustStorePassword", System.getProperty(TRUST_STORE_PWD_PROP, "")).getValue();
      if (trustStorePassword.length() != 0) {
         System.setProperty(TRUST_STORE_PWD_PROP, trustStorePassword);
      }
      else {
         log.fine("SSL client socket is configured but no trustStorePassword is specified, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html#SSL");
      }
      
      String keyStore = address.getEnv("keyStore", System.getProperty(KEY_STORE_PROP, "")).getValue();
      if (keyStore.length() != 0) {
         log.info("SSL server socket enabled for " + address.getRawAddress() + ", keyStore="+keyStore);
         System.setProperty(KEY_STORE_PROP, keyStore);
      }
      else {
         log.warning("SSL server socket is enabled but no keyStore is specified, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html#SSL");
      }

      String keyStorePassword = address.getEnv("keyStorePassword", System.getProperty(KEY_STORE_PWD_PROP, "")).getValue();
      if (keyStorePassword.length() != 0) {
         System.setProperty(KEY_STORE_PWD_PROP, keyStorePassword);
      }
      else {
         log.warning("SSL server socket is enabled but no keyStorePassword is specified, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html#SSL");
      }

      try  {
         javax.net.ssl.SSLServerSocket sock = (javax.net.ssl.SSLServerSocket)
                  javax.net.ssl.SSLServerSocketFactory.getDefault().createServerSocket(getPort(), backlog, getInetAddress());

         boolean needClientAuth = address.getEnv("needClientAuth", false).getValue();
         log.info("SSL server socket is configured with needClientAuth=" + needClientAuth + ": SSL client authentication is " + (needClientAuth==true?"":"NOT ") + "enabled");
         sock.setNeedClientAuth(needClientAuth);
         return sock;
      }
      catch (Exception e) {
         if (log.isLoggable(Level.FINE)) log.fine("Can't switch on SSL socket: " + e.toString() + " : " + e.getCause());
         Throwable tt = (e.getCause() != null) ? e.getCause() : e;
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_SSLSOCKET, ME, 
                        "Can't switch on SSL socket, check your keyStore and keyStorePassword or socket configuration", tt);
      }
      finally {
         // checkinSSLProps();
         System.setProperty(KEY_STORE_PROP, "");
         System.setProperty(KEY_STORE_PWD_PROP, "");
      }
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

   public synchronized javax.net.ssl.SSLSocketFactory createSocketFactorySSL(AddressBase address) throws XmlBlasterException {
      try {
         checkoutSSLProps();
         return createSocketFactorySSLInternal(address);
      }
      finally {
         checkinSSLProps();
         // System.setProperty(TRUST_STORE_PROP, "");
         // System.setProperty(TRUST_STORE_PWD_PROP, "");
      }
         
   }
   
   private javax.net.ssl.SSLSocketFactory createSocketFactorySSLInternal(AddressBase address) throws XmlBlasterException {

      javax.net.ssl.SSLSocketFactory ssf = null;
      /*
       NOTE: In a cluster environment you may have established a SSL server socket before.
       These settings are still valid when we later are a SSL client to a remote cluster node
       in which case no additional settings are needed here (they are actually ignored by the JDK ssl implementation)
      */

      // Configure a stand alone client key store (containing the clients private key)
      String keyStore = address.getEnv("keyStore", System.getProperty(KEY_STORE_PROP, "")).getValue();
      if (keyStore != "") {
         log.info("SSL client socket enabled for " + address.getRawAddress() + ", keyStore="+keyStore);
         System.setProperty(KEY_STORE_PROP, keyStore);
      }

      String keyStorePassword = address.getEnv("keyStorePassword", System.getProperty(KEY_STORE_PWD_PROP, "")).getValue();
      if (keyStorePassword != "") {
         System.setProperty(KEY_STORE_PWD_PROP, keyStorePassword);
      }
      else {
         log.warning("SSL client socket is enabled but no keyStorePassword is specified, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html#SSL");
      }

      // The trustStore file can be identical to the server side keyStore file:
      String trustStore = address.getEnv("trustStore", System.getProperty("javax.net.ssl.trustStore", "")).getValue();
      String trustStorePassword = address.getEnv("trustStorePassword", System.getProperty(TRUST_STORE_PWD_PROP, "")).getValue();
      if (trustStore != "") {
         if (firstTrust) {
            log.info("SSL client socket enabled, trustStore="+trustStore);
            firstTrust = false;
         }
      }
      else {
         if (log.isLoggable(Level.FINE)) log.fine("SSL client socket is configured but no trustStore is specified we now check your keyStore setting ..., see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html#SSL");
         // Reuse a server store if one is found
         trustStore = address.getEnv("keyStore", System.getProperty(KEY_STORE_PROP, "")).getValue();
      }
      if (trustStorePassword != "") {
      }
      else {
         if (log.isLoggable(Level.FINE)) log.fine("SSL client socket is configured but no trustStorePassword is specified we now check your keyStorePassword setting ..., see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html#SSL");
         // Reuse a server store if one is found
         trustStorePassword = address.getEnv("keyStorePassword", System.getProperty(KEY_STORE_PWD_PROP, "")).getValue();
      }

      if (trustStore != "") {
         System.setProperty(TRUST_STORE_PROP, trustStore);
      }
      if (trustStorePassword != "") {
         System.setProperty(TRUST_STORE_PWD_PROP, trustStorePassword);
      }

      try  {
         boolean findStoreInXmlBlasterSearchPath = address.getEnv("findStoreInXmlBlasterSearchPath", false).getValue();

         if (findStoreInXmlBlasterSearchPath) {
            javax.net.ssl.KeyManagerFactory kmf = null;    // since JDK 1.4
            javax.net.ssl.TrustManagerFactory tmf = null;

            // Can be changed by "keystore.type" in JAVA_HOME/lib/security/java.security, defaults to "jks"
            // "JKS" in caps works ok on java 1.4.x.. on java 1.5 you must use "jks" in lowercase
            String storeType = address.getEnv("keystore.type", java.security.KeyStore.getDefaultType()).getValue();

            {  // keyStore with my private key
               FileLocator locator = new FileLocator(glob);
               URL url = locator.findFileInXmlBlasterSearchPath((String)null, keyStore);
               if (url != null) {
                  InputStream in = url.openStream();
                  java.security.KeyStore ks = java.security.KeyStore.getInstance(storeType); // since JDK 1.2
                  ks.load(in, keyStorePassword.toCharArray());
                  kmf = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
                  kmf.init(ks, keyStorePassword.toCharArray());
                  if (firstKey) {
                     log.info("SSL client socket keyStore="+url.getFile().toString());
                     firstKey = false;
                  }
               }
               else {
                  log.warning("SSL client socket can't find keyStore=" + keyStore + " in xmlBlaster search pathes, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html#SSL");
               }
            }
            {  // trustStore with others public keys
               FileLocator locator = new FileLocator(glob);
               URL url = locator.findFileInXmlBlasterSearchPath((String)null, trustStore);
               if (url != null) {
                  InputStream in = url.openStream();
                  java.security.KeyStore ks = java.security.KeyStore.getInstance(storeType);
                  ks.load(in, trustStorePassword.toCharArray());
                  tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
                  tmf.init(ks);
                  if (firstTrust) {
                     log.info("SSL client socket trustStore="+url.getFile().toString());
                     firstTrust = false;
                  }
               }
               else {
                  log.warning("SSL client socket can't find trustStore=" + trustStore + " in xmlBlaster search pathes, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html#SSL");
               }
            }

            javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("SSLv3");
            java.security.SecureRandom random = null; // since JDK 1.2
            ctx.init((kmf==null)?null:kmf.getKeyManagers(), (tmf==null)?null:tmf.getTrustManagers(), random);
            ssf = ctx.getSocketFactory(); // since JDK 1.4
         }
         else {
            if (!new java.io.File(keyStore).canRead()) {
               log.warning("SSL client socket is enabled but i can't read keyStore=" + keyStore + ", see http://www.xmlblaster.org/xmlBlaster/doc/requirements/protocol.socket.html#SSL");
            }
            ssf = (javax.net.ssl.SSLSocketFactory)javax.net.ssl.SSLSocketFactory.getDefault();
         }
      }
      catch (Exception e) {
         if (log.isLoggable(Level.FINE)) log.fine("Can't switch on SSL socket: " + e.toString() + " : " + e.getCause());
         Throwable tt = (e.getCause() != null) ? e.getCause() : e;
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, 
                        "SSL XmlBlaster server is unknown, '-dispatch/connection/plugin/socket/hostname=<ip>'", tt);
      }
      return ssf;
   }
   
   /**
    * Helper to create a SSL socket, uses reflection to compile with JDK 1.3
    * SSL support can't be used with a standard JDK 1.3
    * @param localSocketUrl null or a configured local socket setting
    * @param address The configuration environment
    */
   public java.net.Socket createSocketSSL(SocketUrl localSocketUrl, AddressBase address) throws XmlBlasterException {

      java.net.Socket retSock = null;
      try {
         checkoutSSLProps();
         javax.net.ssl.SSLSocketFactory ssf = createSocketFactorySSLInternal(address);
         if (localSocketUrl != null && localSocketUrl.getPort() > -1)
            retSock = ssf.createSocket(getInetAddress(),
                     getPort(), localSocketUrl.getInetAddress(), localSocketUrl.getPort());
         else
            retSock = ssf.createSocket(getInetAddress(), getPort());
      }
      catch (Exception e) {
         if (log.isLoggable(Level.FINE)) log.fine("Can't switch on SSL socket: " + e.toString() + " : " + e.getCause());
         Throwable tt = (e.getCause() != null) ? e.getCause() : e;
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, 
                        "SSL XmlBlaster server is unknown, '-dispatch/connection/plugin/socket/hostname=<ip>'", tt);
      }
      finally {
         checkinSSLProps();
         // System.setProperty(TRUST_STORE_PROP, "");
         // System.setProperty(TRUST_STORE_PWD_PROP, "");
      }
      return retSock;
   }

}

