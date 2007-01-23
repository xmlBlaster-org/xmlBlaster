/*------------------------------------------------------------------------------
Name:      ConnectQosServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.qos;

import java.util.Properties;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.SessionQos;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;


/**
 * This class encapsulates the qos of a connect() invocation. 
 * <p />
 * @see org.xmlBlaster.util.qos.ConnectQosSaxFactory
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.connect.html">connect interface</a>
 */
public final class ConnectQosServer
{
   private final ConnectQosData connectQosData;
   private boolean bypassCredentialCheck = false;
   private long persistenceUniqueId;
   /** The address information got from the protocol plugin. */
   private AddressServer addressServer;

   public ConnectQosServer(Global glob, ConnectQosData connectQosData) {
      this.connectQosData = connectQosData;
      // better keep it for forwarding etc.
      //this.connectQosData.eraseClientQueueProperty(); // not of interest on server side
   }

   public ConnectQosServer(Global glob, String xmlQos) throws XmlBlasterException {
      this.connectQosData = glob.getConnectQosFactory().readObject(xmlQos);
   }

   /**
    * Serialize first to have a clone for security reasons (and to guarantee our Global). 
    * @param glob Use the new Global for the returned clone
    */
   public ConnectQosServer getClone(Global newGlob) throws XmlBlasterException {
      ConnectQosServer aClone = new ConnectQosServer(newGlob, toXml());
      CallbackAddress[] cbArr = getSessionCbQueueProperty().getCallbackAddresses();
      CallbackAddress[] aCloneCbArr = aClone.getSessionCbQueueProperty().getCallbackAddresses(); 
      for (int ii=0; cbArr!=null && ii<cbArr.length && aCloneCbArr != null && ii<aCloneCbArr.length; ii++) {
         aCloneCbArr[ii].setHashkey(cbArr[ii].getHashkey());
      }
      aClone.bypassCredentialCheck(bypassCredentialCheck());
      aClone.isFromPersistenceRecovery(isFromPersistenceRecovery());
      aClone.setPersistenceUniqueId(getPersistenceUniqueId());
      return aClone;
   }

   public ConnectQosData getData() {
      return this.connectQosData;
   }

   /**
    * The address information got from the protocol plugin.
    * @param addressServer The address information of the current protocol plugin
    */
   public void setAddressServer(AddressServer addressServer) {
      this.addressServer = addressServer;
   }

   /**
    * The address information got from the protocol plugin. 
    * @return Can be null
    */
   public AddressServer getAddressServer() {
      return this.addressServer;
   }

   /**
    * Tell authenticate to not check the password.
    * This is an attribute of ConnectQosServer only, ConnectQosData and its
    * toXml() never transport this setting.
    * As the ConnectQosServer facade is only used in the core (inside the protector classes)
    * this is no security hole.
    */
   public void bypassCredentialCheck(boolean bypassCredentialCheck) {
      this.bypassCredentialCheck = bypassCredentialCheck;
   }

   public boolean bypassCredentialCheck() {
      return this.bypassCredentialCheck;
   }

   public void isFromPersistenceRecovery(boolean fromPersistenceRecovery) {
      this.connectQosData.isFromPersistenceRecovery(fromPersistenceRecovery);
   }

   public long getPersistenceUniqueId() {
      return this.persistenceUniqueId;
   }

   public void setPersistenceUniqueId(long persistenceUniqueId) {
      this.persistenceUniqueId = persistenceUniqueId;
   }

   /**
    * Marker if the message comes from persistent store after recovery. 
    */
   public boolean isFromPersistenceRecovery() {
      return this.connectQosData.isFromPersistenceRecovery();
   }

   /**
    * @return The session QoS which contains all session specific configuration, never null
    */
   public SessionQos getSessionQos() {
      return this.connectQosData.getSessionQos();
   }

   public int getMaxSessions() {
      return this.connectQosData.getSessionQos().getMaxSessions();
   }

   /**
    * If clearSessions is true, all old sessions of this user are discarded. 
    */
   public boolean clearSessions() {
      return this.connectQosData.getSessionQos().clearSessions();
   }

   /**
    * Timeout until session expires if no communication happens
    */
   public long getSessionTimeout() {
      return this.connectQosData.getSessionQos().getSessionTimeout();
   }

   /**
    * Timeout until session expires if no communication happens
    */
   public void setSessionTimeout(long timeout) {
      this.connectQosData.getSessionQos().setSessionTimeout(timeout);
   }

   public boolean hasPublicSessionId() {
      if (getSessionName() != null) {
         return getSessionName().isSession();
      }
      return false;
   }

   /**
    * Set the login session name. 
    */
   public void setSessionName(SessionName sessionName) {
      getSessionQos().setSessionName(sessionName);
   }

   /**
    */
   public SessionName getSessionName() {
      return getSessionQos().getSessionName();
   }

   /**
    * Allows to set or overwrite the login name for I_SecurityQos. 
    * <p>
    * This will call setSessionName() as well if sessionName is not set yet.
    * </p>
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
    * Allows to set or overwrite the parsed security plugin. 
    * <p />
    * &lt;securityService type='simple' version='1.0'>...&lt;/securityService>
    * @param mechanism The client side security plugin to use
    * @param passwd If null the environment -passwd is checked
    */
   public I_ClientPlugin loadClientPlugin(String mechanism, String version, String loginName, String passwd) throws XmlBlasterException {
      return this.connectQosData.loadClientPlugin(mechanism, version, loginName, passwd);
   }

   /**
    * Allows to specify how you want to identify yourself. 
    * <p />
    * Usage to login to xmlBlaster with a password approach:
    * <pre>
    *    import org.xmlBlaster.authentication.plugins.simple.SecurityQos;
    *    ...
    *    ConnectQosServer qos = new ConnectQosServer(null);
    *    qos.setSecurityQos(new SecurityQos("joe", "secret"));
    *    xmlBlasterConnection.connect(qos);
    * </pre>
    * NOTE: Usually setSecurityPluginData() is easier to use.
    */
   //public void setSecurityQos(I_SecurityQos securityQos) {
   //   this.connectQosData.setSecurityQos(securityQos);
   //}

   /**
    * @return Access the login credentials or null if not set
    */
   public I_SecurityQos getSecurityQos() {
      return this.connectQosData.getSecurityQos();
   }

   /**
    * Return the type of the referenced SecurityPlugin.
    * <p/>
    * @return The type or null if not known
    */
   public String getClientPluginType() {
      return this.connectQosData.getClientPluginType();
   }

   /**
    * Return the version of the referenced SecurityPlugin.
    * <p/>
    * @return The version or null if not known
    */
   public String getClientPluginVersion() {
      return this.connectQosData.getClientPluginVersion();
   }

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
    * Set the address to which we want to connect, with all the configured parameters. 
    * <p />
    * @param address  An object containing the protocol (e.g. EMAIL) the address (e.g. hugo@welfare.org) and the connection properties
    */
   public void setAddress(Address address) {
      this.connectQosData.setAddress(address);
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
    * Returns never null
    */
   public CbQueueProperty getSessionCbQueueProperty() {
      return this.connectQosData.getSessionCbQueueProperty();
   }

   /**
    * Returns never null. 
    * The subjectQueue has never callback addresses, the addresses of the sessions are used
    * if configured.
    */
   public CbQueueProperty getSubjectQueueProperty() {
      return this.connectQosData.getSubjectQueueProperty();
   }

   /**
    * @return Is the client a cluster?
    */
   public boolean isClusterNode() {
      return this.connectQosData.isClusterNode();
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString() {
      return toXml();
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the connect QoS as a XML ASCII string
    */
   public String toXml(String extraOffset) {
      return this.connectQosData.toXml(extraOffset);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @param flag For example Constants.TOXML_NOSECURITY
    * @return internal state of the connect QoS as a XML ASCII string
    */
   public String toXml(String extraOffset, Properties props) {
      return this.connectQosData.toXml(extraOffset, props);
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml() {
      return this.connectQosData.toXml();
   }
}

