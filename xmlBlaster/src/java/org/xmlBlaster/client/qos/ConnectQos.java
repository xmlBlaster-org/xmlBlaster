/*------------------------------------------------------------------------------
Name:      ConnectQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.SessionQos;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.qos.storage.ClientQueueProperty;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;


/**
 * This class encapsulates the qos of a connect() invocation. 
 * <p />
 * @see org.xmlBlaster.util.qos.ConnectQosSaxFactory
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.connect.html">connect interface</a>
 */
public final class ConnectQos
{
   private String ME = "ConnectQos";
   private final Global glob;
   private final ConnectQosData connectQosData;
   /** Access to encrypt/decrypt framework, used by protocol plugins */
   //private I_MsgSecurityInterceptor securityInterceptor;

   /**
    * Default constructor. 
    * <p>
    * Initializes login credentials from environment e.g. <i>-session.name guest</i> and <i>-passwd secret</i> with
    * the default security plugin as given by <i>-Security.Client.DefaultPlugin htpasswd,1.0</i>
    * </p>
    * <p>
    * To use another security authentication plugin use setSecurity()
    */
   public ConnectQos(Global glob) {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.connectQosData = new ConnectQosData(this.glob); 
   }

   /**
    * Login with the default security plugin as given by <i>-Security.Client.DefaultPlugin htpasswd,1.0</i>
    * @param userId e.g. "joe" or "joe/7" if you want to connect to joe's seventh session
    * @param passwd The password if you use a password based authentication
    * @exception XmlBlasterException if the default security plugin couldn't be loaded
    */
   public ConnectQos(Global glob, String userId, String passwd) throws XmlBlasterException {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.connectQosData = new ConnectQosData(this.glob, this.glob.getConnectQosFactory(), null, null); 
      this.connectQosData.loadClientPlugin(null, null, userId, passwd);
   }

   /**
    * Constructor for special use in cluster environment only. 
    */
   public ConnectQos(Global glob, ConnectQosData connectQosData) {
      this.glob = (glob==null) ? Global.instance() : glob;
      this.connectQosData = connectQosData;
   }

   public ConnectQosData getData() {
      return this.connectQosData;
   }

   /**
    * @return The session QoS which contains all session specific configuration, never null
    */
   public SessionQos getSessionQos() {
      return this.connectQosData.getSessionQos();
   }

   /**
    * Set the login session name. 
    * <p>
    * This will set the security loginName as well (see setUserId()) it not set different.
    * </p>
    * @param sessionName e.g. "joe" which is the loginName (subjectId) only<br />
    *        e.g. "joe/2" which forces a connect on the public session ID 2 of user joe
    */
   public void setSessionName(SessionName sessionName) {
      getSessionQos().setSessionName(sessionName);
   }

   public SessionName getSessionName() {
      return getSessionQos().getSessionName();
   }

   /**
    * Timeout until session expires if no communication happens
    * @param timeout The login session will be destroyed after given milliseconds.<br />
    *                Session lasts forever if set to 0L
    */
   public void setSessionTimeout(long timeout) {
      getSessionQos().setSessionTimeout(timeout);
   }

   /**
    * Set the secret sessionId
    */
   public void setSecretSessionId(String id) {
      this.connectQosData.getSessionQos().setSecretSessionId(id);
   }

   /**
    * If maxSession == 1, only a single login is possible
    * @param max How often the same client may login
    */
   public void setMaxSessions(int max) {
      this.connectQosData.getSessionQos().setMaxSessions(max);
   }

   /**
    * If clearSessions is true, all old sessions of this user are discarded. 
    * @param clear Defaults to false
    */
   public void clearSessions(boolean clear) {
      this.connectQosData.getSessionQos().clearSessions(clear);
   }

   /**
    * If clearSessions is true, all old sessions of this user are discarded. 
    */
   public final boolean clearSessions() {
      return this.connectQosData.getSessionQos().clearSessions();
   }

   /**
    * Allows to set or overwrite the login name for I_SecurityQos. 
    * <p>
    * This will call setSessionName() as well if sessionName is not set yet.
    * </p>
    * This is a convenience method to set the securityQos userId
    *
    * @param loginName The unique user id
    */
   public void setUserId(String loginName) throws XmlBlasterException {
      this.connectQosData.setUserId(loginName);
   }

   /**
    * @return The user ID or "NoLoginName" if not known
    */
   public String getUserId() {
      return this.connectQosData.getUserId();
   }

   /**
    * Allows to set or overwrite the client side security plugin. 
    *
    * @param type The client side security plugin to use
    * @param credential For 'htpasswd' the password, if null the environment -passwd is checked (default plugin)
    * @see ConnectQosData#loadClientPlugin(String, String, String, String)
    */
   public I_ClientPlugin loadClientPlugin(String type, String version, String userId, String credential) throws XmlBlasterException {
   //public void setSecurityPluginData(String mechanism, String version, String loginName, String passwd) throws XmlBlasterException {
      return this.connectQosData.loadClientPlugin(type, version, userId, credential);
   }

   /**
    * Access the default plugin or the previously added by load loadClientPlugin()
    */
   public I_ClientPlugin getClientPlugin() {
      return this.getClientPlugin();
   }

   /**
    * Allows to specify how you want to identify yourself. 
    * <p />
    * Usage to login to xmlBlaster with a password approach:
    * <pre>
    *    import org.xmlBlaster.authentication.plugins.simple.SecurityQos;
    *    ...
    *    ConnectQosData qos = new ConnectQosData(null);
    *    qos.setSecurityQos(new SecurityQos("joe", "secret"));
    *    xmlBlasterConnection.connect(qos);
    * </pre>
    * NOTE: Usually loadClientPlugin() is easier to use.
    * @deprecated This is specific to the loaded I_ClientPlugin, please use getClientPlugin().setSecurityQos()
    */
   //public void setSecurityQos(I_SecurityQos securityQos) {
   //   this.connectQosData.setSecurityQos(securityQos);
   //}

   /**
    * This is a convenience method for <code>getClientPlugin().getSecurityQos()</code>. 
    * @return Access the login credentials or null if not set
    */
   public I_SecurityQos getSecurityQos() {
      return this.connectQosData.getSecurityQos();
   }

   /**
    * Return the type of the referenced SecurityPlugin.
    * <p/>
    * This is a convenience access similiar to 
    * <code>getClientPlugin().getPluginType()</code>
    *
    * @return The type or null if not known
    */
   //public String getSecurityPluginType() {
   //   return this.connectQosData.getSecurityPluginType();
   //}

   /**
    * Return the version of the referenced SecurityPlugin.
    * <p/>
    * This is a convenience access similiar to 
    * <code>getClientPlugin().getPluginVersion()</code>
    *
    * @return The version or null if not known
    */
   //public String getSecurityPluginVersion() {
   //   return this.connectQosData.getSecurityPluginVersion();
   //}

   /**
    * @param Set if we accept point to point messages
    */
   public void setPtpAllowed(boolean ptpAllowed) {
      this.connectQosData.setPtpAllowed(ptpAllowed);
   }

   /**
    * @return true if we are accepting PtP messages
    */
   public boolean isPtpAllowed() {
      return this.connectQosData.isPtpAllowed();
   }

   /**
    * @param Set if we allow multiple updates for the same message if we have subscribed multiple times to it. 
    */
   public void setDuplicateUpdates(boolean duplicateUpdates) {
      this.connectQosData.setDuplicateUpdates(duplicateUpdates);
   }

   /**
    * @return true if we allow multiple updates for the same message if we have subscribed multiple times to it. 
    */
   public boolean duplicateUpdates() {
      return this.connectQosData.duplicateUpdates();
   }

   /**
    * The configuration of the local client side queue. 
    * @return never null
    */
   public ClientQueueProperty getClientQueueProperty() {
      return this.connectQosData.getClientQueueProperty();
   }

   /**
    * The configuration of the local client side queue. 
    * @param Your hardcoded configuration
    */
   public void addClientQueueProperty(ClientQueueProperty clientQueueProperty) {
      this.connectQosData.addClientQueueProperty(clientQueueProperty);
   }

   /**
    * Set the address to which we want to connect, with all the configured parameters. 
    * <p />
    * @param address  An object containing the protocol (e.g. EMAIL) the address (e.g. hugo@welfare.org) and the connection properties
    */
   public void setAddress(Address address) {
      this.connectQosData.setAddress(address);
   }

   /**
    * The connection address and properties of the xmlBlaster server
    * we want connect to.
    * @return never null
    */
   public Address getAddress() {
      return this.connectQosData.getAddress();
   }

   /**
    * The connection address and properties of the xmlBlaster server
    * we want connect to.
    * @return never null
    */
   public AddressBase[] getAddresses() {
      return this.connectQosData.getAddresses();
   }

   /**
    * Add a callback address where to send the message (for PtP or subscribes). 
    * <p />
    * Creates a default CbQueueProperty object to hold the callback address argument.<br />
    * Note you can invoke this multiple times to allow multiple callbacks.
    * @param callback  An object containing the protocol (e.g. EMAIL) and the address (e.g. hugo@welfare.org)
    */
   public void addCallbackAddress(CallbackAddress callback) {
      this.connectQosData.addCallbackAddress(callback);
   }

   /**
    * Adds a queue description. 
    * This allows to set all supported attributes of a callback queue and a callback address
    * @param prop The property object of the callback queue which shall be established in the server for calling us back.
    * @see org.xmlBlaster.util.qos.address.CallbackAddress
    */
   public void setSessionCbQueueProperty(CbQueueProperty prop) {
      this.connectQosData.setSessionCbQueueProperty(prop);
   }

   /**
    * Returns never null. 
    * <p />
    * If no CbQueueProperty exists, a RELATING_CALLBACK queue property object is created
    * on the fly.
    */
   public CbQueueProperty getSessionCbQueueProperty() {
      return this.connectQosData.getSessionCbQueueProperty();
   }

   /** 
    * The number of bytes of stringified qos
    */
   public int size() {
      return this.connectQosData.size();
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString() {
      return toXml();
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml() {
      return this.connectQosData.toXml();
   }

   /**
    * Access the security interceptor to encrypt/decrypt. 
    * @return I_MsgSecurityInterceptor plugin or null
    * @deprecated No use for this
    */
   //public I_MsgSecurityInterceptor getSecurityInterceptor() {
   //   return this.securityInterceptor;
   //}

   /**
    * Access the security interceptor to encrypt/decrypt. 
    * @return I_MsgSecurityInterceptor
    * @deprecated No use for this
    */
   //public void setSecurityInterceptor(I_MsgSecurityInterceptor securityInterceptor) {
   //   this.securityInterceptor = securityInterceptor;
   //}

   /**
    * Sets a client property (an application specific property) to the
    * given value
    * @param key
    * @param value
    */
   public void addClientProperty(String key, Object value) {
      this.connectQosData.addClientProperty(key, value);
   }

   /**
    * Read back a property. 
    * @return The client property or null if not found
    */
   public ClientProperty getClientProperty(String key) {
      return this.connectQosData.getClientProperty(key);
   }

   /**
    * sets the persitent flag for this subscription. If this flag is
    * set, the subscription will persit a server crash.
    * @param persitent
    */
   public void setPersistent(boolean persitent) {
      this.connectQosData.setPersistent(persitent);
   }
   
   /**
    * Gets the persitent flag for this subscription. If this flag is
    * set, the subscription will persit a server crash.
    * @return true if persitent false otherwise.
    */
   public boolean getPersistent() {
      return this.connectQosData.getPersistentProp().getValue();
   }
   
   /**
    * Get a usage string for the connection parameters
    */
   public String usage() {
      String text = "\n";
      text += "Control my security settings\n";
      text += "   -security.plugin.type    The security plugin to use [simple]\n";
      text += "   -security.plugin.version The version of the plugin [1.0]\n";
      text += "\n";
      text += getSessionQos().usage();
      return text;
   }

   /**
    *  For testing invoke: java org.xmlBlaster.client.qos.ConnectQos
    */
   public static void main( String[] args ) throws XmlBlasterException {
      Global glob = new Global(args);
      {
         ConnectQos qos =new ConnectQos(new Global(args), "joe/2", "secret");//new SessionName(glob, "joe"));
         System.out.println(qos.toXml());
      }
      {
         ConnectQos qos =new ConnectQos(null);
         System.out.println("Minimal:" + qos.toXml());
      }
   }
}
