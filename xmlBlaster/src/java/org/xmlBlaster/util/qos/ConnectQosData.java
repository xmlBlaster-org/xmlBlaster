/*------------------------------------------------------------------------------
Name:      ConnectQosData.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.qos.storage.ClientQueueProperty;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.address.ServerRef;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.property.PropEntry;
import org.xmlBlaster.util.property.PropLong;
import org.xmlBlaster.util.property.PropBoolean;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;

import java.util.Vector;
import java.util.ArrayList;


/**
 * This class encapsulates the qos of a login() or connect(). 
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * NOTE: This class is not synchronized and not Serializable (add these functionality when you need it)
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">The interface.connect requirement</a>
 * @see org.xmlBlaster.util.qos.ConnectQosSaxFactory
 * @see org.xmlBlaster.test.classtest.ConnectQosTest
 */
public final class ConnectQosData extends QosData implements java.io.Serializable, Cloneable
{
   private final String ME = "ConnectQosData";
   protected transient I_ConnectQosFactory factory;
   private ConnectionStateEnum initialConnectionState = ConnectionStateEnum.UNDEF;

   /** 
    * PtP messages wanted? True is default
    * <p />
    * <pre>
    * &lt;ptp>false&lt;ptp/> <!-- Don't send me any PtP messages (prevents spamming) -->
    * </pre>
    */
   protected PropBoolean ptpAllowed = new PropBoolean(true);

   /**
    * Allows to mark that we are an xmlBlaster cluster node.
    */
   protected PropBoolean clusterNode = new PropBoolean(false);

   /**
    * If duplicateUpdates=false we will send only one update, even if
    * the same client subscribed multiple times on the same message.
    * Defaults to true.
    */
   protected PropBoolean duplicateUpdates = new PropBoolean(true);

   /**
    * Used for ConnetReturnQos only: If reconnected==true a client has reconnected to an existing session
    */
   protected PropBoolean reconnected = new PropBoolean(false);

   /** Session settings */
   private SessionQos sessionQos;

   protected transient PluginLoader pMgr;
   protected I_ClientPlugin securityPluginCache;
   protected I_SecurityQos securityQos;

   /**
    * The server reference, e.g. the CORBA IOR string or the XML-RPC url
    * This is returned from XmlBlaster connect() and not used for login
    */
   protected transient ServerRef[] serverRefArr;
   protected Vector serverRefVec = new Vector();  // <serverRef type="IOR">IOR:000122200...</serverRef>

   /** Holding queue property if subject related, configures the queue which holds PtP messages for the client on the server */
   private transient CbQueueProperty subjectQueueProperty;
   /** Holding queue property if session related, configures the callback queue of the client */
   private transient CbQueueProperty sessionCbQueueProperty;


   /** Holding queue properties for the client side invocation queue */
   protected transient ArrayList clientQueuePropertyList = new ArrayList();

   /** The node id to which we want to connect */
   private NodeId nodeId;

   /**
    * Constructor for client side. 
    */
   public ConnectQosData(Global glob) {
      this(glob, null, null, null);
      initialize(glob);
   }
   
   /**
    * Constructs the specialized quality of service object for a connect() or connect-return call. 
    * NOTE: The serialData is not parsed - use the factory for it.
    * NOTE: The security plugin is not initialized, use setSecurityPluginData() to do it
    *
    * @param factory The factory which knows how to serialize and parse me
    * @param serialData The XML based ASCII string (syntax is described in requirement interface.connect)
    * @param nodeId The node id with stripped special characters (see Global#getStrippedId)
    */
   public ConnectQosData(Global glob, I_ConnectQosFactory factory, String serialData, NodeId nodeId) {
      super(glob, serialData);
      this.factory = (factory == null) ? this.glob.getConnectQosFactory() : factory;
      this.nodeId = (nodeId == null) ? new NodeId(this.glob.getStrippedId()) : nodeId;
      this.sessionQos = new SessionQos(this.glob); // , this.nodeId); is handled by SessionName depending on client or server side
   }

   /**
    * Constructor for cluster server. 
    * <p />
    * @param nodeId The the unique cluster node id, supports configuration per node
    */
   public ConnectQosData(Global glob, NodeId nodeId) {
      this(glob, null, null, nodeId);
      initialize(glob);
   }

   private void initialize(Global glob) {
      this.securityQos = getSecurityPlugin(null,null).getSecurityQos();
      this.securityQos.setCredential(accessPassword(null));
      if (this.sessionQos.getSessionName() != null) {
         this.securityQos.setUserId(this.sessionQos.getSessionName().getLoginName());
      }
   }

   /**
    * The subjectQueue is exactly one instance for a subjectId (a loginName), it
    * is used to hold the PtP messages send to this subject.
    * <p>
    * The subjectQueue has never callback addresses, the addresses of the sessions are used
    * if configured.
    * </p>
    * @return never null. 
    */
   public CbQueueProperty getSubjectQueueProperty() {
      if (this.subjectQueueProperty == null) {
         this.subjectQueueProperty = new CbQueueProperty(glob, Constants.RELATING_SUBJECT, nodeId.toString());
      }
      return this.subjectQueueProperty;
   }

   public void setSubjectQueueProperty(CbQueueProperty subjectQueueProperty) {
      this.subjectQueueProperty = subjectQueueProperty;
   }

   public boolean hasSubjectQueueProperty() {
      return (this.subjectQueueProperty != null);
   }

   /**
    * Returns never null
    */
   public CbQueueProperty getSessionCbQueueProperty() {
      if (this.sessionCbQueueProperty == null)
         this.sessionCbQueueProperty = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, nodeId.toString());
      return this.sessionCbQueueProperty;
   }

   public void setSessionCbQueueProperty(CbQueueProperty sessionCbQueueProperty) {
      this.sessionCbQueueProperty = sessionCbQueueProperty;
   }

   public boolean hasSessionCbQueueProperty() {
      return (this.sessionCbQueueProperty != null);
   }

   /**
    * Force a security configuration. 
    * <p>
    * You can use setSecurityPluginData() or setUserId() instead which loads the 
    * given/default security plugin and does a lookup in the environment.
    * </p>
    * @see org.xmlBlaster.client.qos.ConnectQos#setSecurityQos(I_SecurityQos)
    */
   public void setSecurityQos(I_SecurityQos securityQos) {
      this.securityQos = securityQos;
      if (!this.sessionQos.isSessionNameModified()) {
         SessionName sessionName = new SessionName(glob, this.securityQos.getUserId()); // parse it and strip it if user has given an absolute name
         this.sessionQos.setSessionName(sessionName, false);
      }
   }

   /**
    * Allows to set or overwrite the parsed security plugin. 
    * <p />
    * &lt;securityService type='simple' version='1.0'>...&lt;/securityService>
    * @param passwd If null the environment -passwd is checked
    */
   public void setSecurityPluginData(String mechanism, String version, String loginName, String passwd) throws XmlBlasterException {
      this.securityQos = getSecurityPlugin(mechanism, version).getSecurityQos();
      setUserId(loginName);
      this.securityQos.setCredential(accessPassword(passwd));
   }

   /**
    * Allows to set or overwrite the login name for I_SecurityQos. 
    * <p />
    * @param loginName The unique user id
    */
   public void setUserId(String loginName) throws XmlBlasterException {
      if (this.securityQos == null) {
         this.securityQos = getSecurityPlugin(null, null).getSecurityQos();
         this.securityQos.setCredential(accessPassword(null));
      }

      SessionName sessionName = new SessionName(glob, loginName); // parse it and strip it if user has given an absolute name
      this.securityQos.setUserId(sessionName.getLoginName());

      if (!this.sessionQos.isSessionNameModified()) {
         this.sessionQos.setSessionName(sessionName, false);
      }
   }

   /**
    * Load a client side security plugin (for internal use only), is needed to create the security QoS string. 
    * @param mechanism If null, the current plugin is used
    */
   protected I_ClientPlugin getSecurityPlugin(String mechanism, String version) {
      // Performance TODO: for 'null' mechanism every time a new plugin is incarnated
      if (this.securityPluginCache==null || !this.securityPluginCache.getType().equals(mechanism) || !this.securityPluginCache.getVersion().equals(version)) {
         if (pMgr==null) pMgr=glob.getClientSecurityPluginLoader();
         try {
            if (mechanism != null)
               this.securityPluginCache=pMgr.getClientPlugin(mechanism, version);
            else
               this.securityPluginCache=pMgr.getCurrentClientPlugin();
         }
         catch (XmlBlasterException e) {
            log.error(ME+".ConnectQosData", "Security plugin initialization failed. Reason: "+e.toString());
            log.error(ME+".ConnectQosData", "No plugin. Trying to continue without the plugin.");
         }
      }

      return this.securityPluginCache;
   }

   /**
    * Private environment lookup if given passwd is null
    * @return Never null, defaults to "secret"
    */
   private String accessPassword(String passwd) {
      if (passwd != null) {
         return passwd;
      }
      passwd = glob.getProperty().get("passwd", "secret");
      if (this.nodeId != null) {
         passwd = glob.getProperty().get("passwd["+this.nodeId+"]", passwd);
      }
      if (log.TRACE) log.trace(ME, "Initializing passwd=" + passwd + " nodeId=" + this.nodeId);
      return passwd;
   }

   /**
    * @return The session QoS, never null
    */
   public SessionQos getSessionQos() {
      return this.sessionQos;
   }

   /**
    * Get our unique SessionName. 
    * <p />
    * @return The unique SessionName (never null)
    */
   public SessionName getSessionName() {
      if (this.sessionQos.getSessionName() == null && this.securityQos != null) {
         this.sessionQos.setSessionName(new SessionName(glob, this.securityQos.getUserId()), false);
      }
      return this.sessionQos.getSessionName();
   }

   /**
    * Accessing the ServerRef addresses of the xmlBlaster server
    * this may be a CORBA-IOR or email or URL ...
    * <p />
    * Only for results of connect() calls (used by clients)
    * @return An array of ServerRef objects, containing the address and the protocol type
    *         If no serverRef available, return an array of 0 length
    */
   public ServerRef[] getServerRefs() {
      if (this.serverRefArr == null) {
         this.serverRefArr = new ServerRef[this.serverRefVec.size()];
         for (int ii=0; ii<this.serverRefArr.length; ii++)
            this.serverRefArr[ii] = (ServerRef)this.serverRefVec.elementAt(ii);
      }
      return this.serverRefArr;
   }

   /**
    * Accessing the ServerRef address of the xmlBlaster server
    * this may be a CORBA-IOR or email or URL ...
    * <p />
    * Only for results of connect() calls (used by clients)
    * @return The first ServerRef object in the list, containing the address and the protocol type
    *         If no serverRef available we return null
    */
   public ServerRef getServerRef() {
      ServerRef[] arr = getServerRefs();
      if (arr.length > 0)
         return arr[0];
      return null;
   }

   /**
    * Adds a server reference, we check for identical addresses to no contain duplicates. 
    */
   public void addServerRef(ServerRef addr) {
      int n = this.serverRefVec.size();
      for(int i=0; i<n; i++) {
         if (((ServerRef)this.serverRefVec.elementAt(i)).equals(addr)) {
            return;   // ignore identical
         }
      }
      this.serverRefVec.addElement(addr);
      this.serverRefArr = null; // reset to be recalculated on demand
   }

   /**
    * @return true If the entry was found and removed
    */
   public boolean removeServerRef(ServerRef addr) {
      boolean rem = this.serverRefVec.remove(addr);
      if (rem) {
         this.serverRefArr = null; // reset to be recalculated on demand
      }
      return rem;
   }

   /**
    * @return true If the entry of protocol given by type was found and removed
    */
   public boolean removeServerRef(String type) {
      ServerRef[] refArr = getServerRefs();
      for(int i=0; i<refArr.length; i++) {
         if (refArr[i].getType().equalsIgnoreCase(type)) {
            return removeServerRef(refArr[i]);
         }
      }
      return false;
   }

   /**
    * @param Set if we accept point to point messages
    */
   public void setPtpAllowed(boolean ptpAllowed) {
      this.ptpAllowed.setValue(ptpAllowed);
   }

   public boolean isPtpAllowed() {
      return this.ptpAllowed.getValue();
   }

   public PropBoolean isPtpAllowedProp() {
      return this.ptpAllowed;
   }

   /**
    * @param Set if we are a cluster node. 
    */
   public void setClusterNode(boolean clusterNode) {
      this.clusterNode.setValue(clusterNode);
   }

   /**
    * @return Are we a cluster?
    */
   public boolean isClusterNode() {
      return this.clusterNode.getValue();
   }

   /**
    * @return The isClusterNode flag object
    */
   public PropBoolean getClusterNodeProp() {
      return this.clusterNode;
   }

   /**
    * @param Set if we allow multiple updates for the same message if we have subscribed multiple times to it. 
    */
   public void setDuplicateUpdates(boolean duplicateUpdates) {
      this.duplicateUpdates.setValue(duplicateUpdates);
   }

   /**
    * @return true if we allow multiple updates for the same message if we have subscribed multiple times to it. 
    */
   public boolean duplicateUpdates() {
      return this.duplicateUpdates.getValue();
   }

   /**
    */
   public PropBoolean duplicateUpdatesProp() {
      return this.duplicateUpdates;
   }

   /**
    * Used for ConnetReturnQos only: If reconnected==true a client has reconnected to an existing session
    */
   public void setReconnected(boolean reconnected) {
      this.reconnected.setValue(reconnected);
   }

   /**
    * Used for ConnetReturnQos only. 
    * @return true A client has reconnected to an existing session
    */
   public boolean isReconnected() {
      return this.reconnected.getValue();
   }

   /**
    */
   public PropBoolean getReconnectedProp() {
      return this.reconnected;
   }

   /**
    * Add an address to which we want to connect, with all the configured parameters. 
    * <p />
    * @param address  An object containing the protocol (e.g. EMAIL) the address (e.g. hugo@welfare.org) and the connection properties
    */
   public void setAddress(Address address) {
      ClientQueueProperty prop = new ClientQueueProperty(glob, this.nodeId.toString()); // Use default queue properties for this xmlBlaster access address
      prop.setAddress(address);
      addClientQueueProperty(prop);
      //clientQueuePropertyArr = null; // reset to be recalculated on demand
   }

   /**
    * The connection address and properties of the xmlBlaster server
    * we want connect to.
    * @return never null
    */
   public Address getAddress() {
      Address address = getClientQueueProperty().getCurrentAddress();
      if (address == null) {
         log.error(ME, "Internal error, can't access address instance in queue");
         throw new IllegalArgumentException(ME + ": Internal error, can't access address instance in queue");
      }
      return address;
   }

   /**
    * The connection address and properties of the xmlBlaster server
    * we want connect to.
    * @return never null
    */
   public AddressBase[] getAddresses() {
      return getClientQueueProperty().getAddresses();
   }

   /**
    * Adds a queue description. 
    * This allows to set all supported attributes of a client side queue and an address to reach xmlBlaster
    * @param prop The property object of the client side queue which shall be established. 
    * @see org.xmlBlaster.util.qos.address.Address
    */
   public void addClientQueueProperty(ClientQueueProperty prop) {
      if (prop == null) return;
      // We use a list to allow in future mutliple addresses
      this.clientQueuePropertyList.add(prop);
   }

   /**
    * @return never null
    */
   public ClientQueueProperty getClientQueueProperty() {
      if (this.clientQueuePropertyList.size() < 1) {
         if (log.TRACE) log.trace(ME, "Creating default server address instance");
         setAddress(glob.getBootstrapAddress());
      }
      if (this.clientQueuePropertyList.size() < 1) {
         log.error(ME, "Internal error, can't access address instance");
         throw new IllegalArgumentException(ME + ": Internal error, can't access address instance");
      }
      return (ClientQueueProperty)this.clientQueuePropertyList.get(0);
   }

   public ClientQueueProperty[] getClientQueuePropertyArr() {
      if (this.clientQueuePropertyList.size() < 1) {
         getClientQueueProperty(); // force creation
      }
      return (ClientQueueProperty[])this.clientQueuePropertyList.toArray(new ClientQueueProperty[this.clientQueuePropertyList.size()]);
   }

   /**
    * Usually done on server side as the server is not interested in it
    */
   public void eraseClientQueueProperty() {
      this.clientQueuePropertyList.clear();
   }

   /**
    * Add a callback address where to send the message
    * <p />
    * Creates a default CbQueueProperty object to hold the callback address argument.<br />
    * @param callback  An object containing the protocol (e.g. EMAIL) and the address (e.g. hugo@welfare.org)
    */
    //* Note you can invoke this multiple times to allow multiple callbacks.
   public void addCallbackAddress(CallbackAddress callback) {
      // Use default queue properties for this callback address
      CbQueueProperty prop = getSessionCbQueueProperty();
      prop.setCallbackAddress(callback);
   }

   /**
    * @return the login credentials or null if not set
    */
   public I_SecurityQos getSecurityQos() {
      return this.securityQos;
   }

   /**
    * Return the type of the referenced SecurityPlugin.
    * <p/>
    * @return The type or null if not known
    */
   public String getSecurityPluginType() {
      if (this.securityQos != null)
         return this.securityQos.getPluginType();
      return null;
   }

   /**
    * Return the version of the referenced SecurityPlugin.
    * <p/>
    * @return The version or null if not known
    */
   public String getSecurityPluginVersion() {
      if (this.securityQos != null)
         return this.securityQos.getPluginVersion();
      return null;
   }

   /**
    * @return The user ID or "NoLoginName" if not known
    */
   public String getUserId() {
      if (this.securityQos==null)
         return "NoLoginName";
      else
         return this.securityQos.getUserId();
   }

   /**
    * Returns the connection state directly after the connect() method returns (client side only). 
    * @return Usually ConnectionStateEnum.ALIVE or ConnectionStateEnum.POLLING
    */
   public ConnectionStateEnum getInitialConnectionState() {
      return this.initialConnectionState;
   }

   /**
    * Set the connection state directly after the connect() (client side only). 
    */
   public void setInitialConnectionState(ConnectionStateEnum initialConnectionState) {
      this.initialConnectionState = initialConnectionState;
   }

   /** 
    * The number of bytes of stringified qos
    */
   public int size() {
      return this.toXml().length();
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
    */
   public String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the connect QoS as a XML ASCII string
    */
   public String toXml(String extraOffset) {
      return this.factory.writeObject(this, extraOffset);
   }

   /**
    * Returns a shallow clone, you can change safely all basic or immutable types
    * like boolean, String, int.
    * Currently TopicProperty and RouteInfo is not cloned (so don't change it)
    */
   public Object clone() {
      log.error(ME, "clone() is not tested");
      ConnectQosData newOne = null;
      newOne = (ConnectQosData)super.clone();
      synchronized(this) {
         newOne.ptpAllowed = (PropBoolean)this.ptpAllowed.clone();
         newOne.clusterNode = (PropBoolean)this.clusterNode.clone();
         newOne.duplicateUpdates = (PropBoolean)this.duplicateUpdates.clone();
         newOne.reconnected = (PropBoolean)this.reconnected.clone();
         //newOne.sessionQos = (SessionQos)this.sessionQos.clone();
         //newOne.securityQos = (I_SecurityQos)this.securityQos.clone();
         newOne.serverRefVec = (Vector)this.serverRefVec;
         newOne.nodeId = (NodeId)this.nodeId;
      }
      return newOne;
   }

}
