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
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.qos.storage.ClientQueueProperty;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.address.ServerRef;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.util.cluster.NodeId;
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
   private static final long serialVersionUID = 1L;
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
    * If the client automatically notifies xmlBlaster that it is alive
    * and the login session is extended
    */
   protected PropBoolean refreshSession = new PropBoolean(false);

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

   protected String serverInstanceId;

   /** Session settings */
   private SessionQos sessionQos;

   private String defaultType; // This address type is chosen from all available addresses (we need a loadbalancing algo!)

   protected transient PluginLoader pMgr;
   protected I_ClientPlugin clientPlugin;
   protected I_SecurityQos securityQos; // We need to cache ita as each call to clientPlugin.createSecurityQos() creates a new instance

   /**
    * The server reference, e.g. the CORBA IOR string or the XMLRPC url
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
   public ConnectQosData(Global glob) throws XmlBlasterException {
      this(glob, null, null, null);
      initialize(glob);
   }
   
   /**
    * Constructs the specialized quality of service object for a connect() or connect-return call. 
    * NOTE: The serialData is not parsed - use the factory for it.
    * NOTE: The security plugin is not initialized, use loadClientPlugin() to do it
    *
    * @param factory The factory which knows how to serialize and parse me
    * @param serialData The XML based ASCII string (syntax is described in requirement interface.connect)
    * @param nodeId The node id with stripped special characters (see Global#getStrippedId)
    */
   public ConnectQosData(Global glob, I_ConnectQosFactory factory, String serialData, NodeId nodeId) throws XmlBlasterException {
      super(glob, serialData, org.xmlBlaster.util.def.MethodName.CONNECT);
      this.factory = (factory == null) ? this.glob.getConnectQosFactory() : factory;
      this.nodeId = (nodeId == null) ? new NodeId(this.glob.getStrippedId()) : nodeId;
      this.sessionQos = new SessionQos(this.glob); // , this.nodeId); is handled by SessionName depending on client or server side
   }

   /**
    * Constructor for cluster server. 
    * <p />
    * @param nodeId The the unique cluster node id, supports configuration per node
    */
   public ConnectQosData(Global glob, NodeId nodeId) throws XmlBlasterException {
      this(glob, null, null, nodeId);
      initialize(glob);
   }

   private void initialize(Global glob) throws XmlBlasterException {
      this.serverInstanceId = this.glob.getInstanceId();
      this.securityQos = getClientPlugin().createSecurityQos();
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
    * You can use loadClientPlugin() or setUserId() instead which loads the 
    * given/default security plugin and does a lookup in the environment.
    */
   void setSecurityQos(I_SecurityQos securityQos) {
      this.securityQos = securityQos;
      if (!this.sessionQos.isSessionNameModified()) {
         SessionName sessionName = new SessionName(glob, this.securityQos.getUserId()); // parse it and strip it if user has given an absolute name
         this.sessionQos.setSessionName(sessionName, false);
      }
   }

   /**
    * Allows to set or overwrite the client side security plugin. 
    * <p />
    * The first goal is a proper connect QoS xml string for authentication.
    * <p />
    * The second goal is to intercept the messages for encryption (or whatever the
    * plugin supports).
    * <p />
    * See xmlBlaster.properties, for example:
    * <pre>
    *   Security.Client.DefaultPlugin=gui,1.0
    *   Security.Client.Plugin[gui][1.0]=org.xmlBlaster.authentication.plugins.gui.ClientSecurityHelper
    * </pre>
    * <p />
    * &lt;securityService type='simple' version='1.0'>...&lt;/securityService>
    *
    * @param credential passwd If null the environment -passwd is checked
    */
   public I_ClientPlugin loadClientPlugin(String type, String version, String userId, String credential) throws XmlBlasterException {
      I_ClientPlugin c = getClientPlugin(type, version);
      this.securityQos = c.createSecurityQos();
      setUserId(userId);
      this.securityQos.setCredential(accessPassword(credential));
      return c;
   }

   /**
    * Allows to set or overwrite the login name for I_SecurityQos. 
    * <p />
    * @param userId The unique user id (loginName)
    */
   public void setUserId(String userId) throws XmlBlasterException {
      if (this.securityQos == null) {
         this.securityQos = getClientPlugin().createSecurityQos();
         this.securityQos.setCredential(accessPassword(null));
      }

      SessionName sessionName = new SessionName(glob, userId); // parse it and strip it if user has given an absolute name
      this.securityQos.setUserId(sessionName.getLoginName());

      if (!this.sessionQos.isSessionNameModified()) {
         this.sessionQos.setSessionName(sessionName, false);
      }
   }

   /**
    * Load a client side security plugin (for internal use only), is needed to create the security QoS string. 
    * @param type If null, the current plugin is used
    * @return
    */
   protected I_ClientPlugin getClientPlugin(String type, String version) throws XmlBlasterException {
      if (this.clientPlugin==null || !this.clientPlugin.getType().equals(type) || !this.clientPlugin.getVersion().equals(version)) {
         if (pMgr==null) pMgr=glob.getClientSecurityPluginLoader();
         if (type != null)
            this.clientPlugin=pMgr.getClientPlugin(type, version);
         else
            this.clientPlugin=pMgr.getCurrentClientPlugin();
      }

      return this.clientPlugin;
   }

   private I_ClientPlugin getClientPlugin() throws XmlBlasterException {
      if (this.clientPlugin == null) {
          getClientPlugin(null, null);  
      }
      return this.clientPlugin;
   }

   /**
    * Private environment lookup if given passwd/credential is null
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
      if (this.sessionQos.getSessionName() == null && getSecurityQos() != null) {
         this.sessionQos.setSessionName(new SessionName(glob, getSecurityQos().getUserId()), false);
      }
      return this.sessionQos.getSessionName();
   }

   /**
    * Set our unique SessionName. 
    */
   public void setSessionName(SessionName sessionName) {
      this.sessionQos.setSessionName(sessionName, true);
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
    * @return refreshSession is true if the client automatically notifies xmlBlaster that it is alive
    * and the login session is extended
    */
   public final boolean getRefreshSession() {
      return this.refreshSession.getValue();
   }

   /**
    * @param refreshSession true: The client automatically notifies xmlBlaster that it is alive
    * and the login session is extended
    */
   public final void setRefreshSession(boolean refreshSession) {
      this.refreshSession.setValue(refreshSession);
   }

   /**
    * @return The isClusterNode flag object
    */
   public PropBoolean getRefreshSessionProp() {
      return this.refreshSession;
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

   public void addAddress(Address address) {
      ClientQueueProperty prop = new ClientQueueProperty(glob, this.nodeId.toString()); // Use default queue properties for this xmlBlaster access address
      prop.setAddress(address);
      addClientQueueProperty(prop, true);
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
   }

   /**
    * The current connection address and properties of the xmlBlaster server
    * we want connect to.
    * @return never null
    */
   public Address getAddress() {
      ClientQueueProperty[] props = getClientQueuePropertyArr();
      Address adr = null;
      for (int i=0; i<props.length; i++) {
         adr = props[i].getCurrentAddress();
         if (getDefaultType().equals(adr.getType())) {
            return adr;
         }
      }
      if (adr == null) {
         log.error(ME, "Internal error, can't access address instance in queue");
         throw new IllegalArgumentException(ME + ": Internal error, can't access address instance in queue");
      }
      return adr;
   }

   /**
    * Try to find the default protocol address as configured for clients
    * @return Never null
    */
   private String getDefaultType() {
      if (this.defaultType == null) {
         synchronized (this) {
            if (this.defaultType == null) {
               Address def = new Address(glob);
               this.defaultType = def.getType();
               if (this.defaultType == null) {
                  this.defaultType = "SOCKET";
               }
            }
         }
      }
      return this.defaultType;
   }

   /**
    * The connection address and properties of the xmlBlaster server
    * we want connect to.
    * @return never null
    */
   public Address[] getAddresses() {
      ClientQueueProperty[] props = getClientQueuePropertyArr();
      ArrayList list = new ArrayList();
      for (int i=0; i<props.length; i++) {
         AddressBase[] adrs = props[i].getAddresses();
         for (int j=0; j<adrs.length; j++) {
            list.add(adrs[j]);
         }
      }
      return (Address[])list.toArray(new Address[list.size()]);
   }

   /**
    * Adds a queue description. 
    * This allows to set all supported attributes of a client side queue and an address to reach xmlBlaster
    * @param prop The property object of the client side queue which shall be established. 
    * @return false if not accepted
    * @see org.xmlBlaster.util.qos.address.Address
    */
   public boolean addClientQueueProperty(ClientQueueProperty prop) {
      return addClientQueueProperty(prop, false);
   }

   private boolean addClientQueueProperty(ClientQueueProperty prop, boolean allowMultiAddress) {
      if (prop == null) return false;
      // We use a list to allow in future mutliple addresses
      if (!allowMultiAddress && this.clientQueuePropertyList.size() > 0) {
         Address addr = prop.getCurrentAddress();
         log.warn(ME, "Clients side load balancing is not implemented, we ignore the additional address" +
                  ((addr==null) ? "" : " '"+addr.toString()+"'"));
         //Thread.currentThread().dumpStack();
         return false;
      }
      this.clientQueuePropertyList.add(prop);
      return true;
   }

   /**
    * The current used (or the default) queue property
    * @return Never null
    */
   public ClientQueueProperty getClientQueueProperty() {
      ClientQueueProperty[] props = getClientQueuePropertyArr();
      for (int i=0; i<props.length; i++) {
         Address[] arr = (Address[])props[i].getAddresses();
         for (int j=0; j<arr.length; j++) {
            if (getDefaultType().equals(arr[j].getType())) {
               return props[i];
            }
         }
      }
      return props[0];
   }

   /**
    * @return never null
    */
   private ClientQueueProperty createClientQueueProperty() {
      if (this.clientQueuePropertyList.size() < 1) {
         if (log.TRACE) log.trace(ME, "Creating default server address instance");
         //setAddress(glob.getBootstrapAddress());
         setAddress(new Address(glob));
      }
      if (this.clientQueuePropertyList.size() < 1) {
         log.error(ME, "Internal error, can't access address instance");
         throw new IllegalArgumentException(ME + ": Internal error, can't access address instance");
      }
      return (ClientQueueProperty)this.clientQueuePropertyList.get(0);
   }

   public boolean hasAddress() {
      if (this.clientQueuePropertyList.size() > 0) return true;
      return false;
   }

   /**
    * At least one entry is delivered
    */
   public ClientQueueProperty[] getClientQueuePropertyArr() {
      if (this.clientQueuePropertyList.size() < 1) {
         createClientQueueProperty(); // force creation
      }
      return (ClientQueueProperty[])this.clientQueuePropertyList.toArray(new ClientQueueProperty[this.clientQueuePropertyList.size()]);
   }

   /**
    * Does the given address belong to this client connection setup?
    */
   public boolean contains(Address other) {
      if (other == null) return false;
      ClientQueueProperty[] props = getClientQueuePropertyArr();
      for (int i=0; i<props.length; i++) {
         if (props[i].contains(other)) {
            return true;
         }
      }
      return false;
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
   public void addCallbackAddress(CallbackAddress callback) {
      // Use default queue properties for this callback address
      CbQueueProperty prop = getSessionCbQueueProperty();
      prop.setCallbackAddress(callback);
   }

   /**
    * Access the currently used callback address. 
    * @return can be null
    */
   public CallbackAddress getCurrentCallbackAddress() {
      CbQueueProperty prop = getSessionCbQueueProperty(); // never null
      return prop.getCurrentCallbackAddress();
   }

   /**
    * @return the login credentials or null if not set
    */
   public I_SecurityQos getSecurityQos() {
      return this.securityQos;
   }

   /**
    * Return the type of the referenced security plugin. 
    * <p/>
    * @return The type or null if not known
    */
   public String getClientPluginType() {
      try {
         return getClientPlugin().getType();
      }
      catch (Exception e) {
         return null;
      }
   }

   /**
    * Return the version of the referenced security plugin. 
    * <p/>
    * @return The version or null if not known
    */
   public String getClientPluginVersion() {
      try {
         return getClientPlugin().getVersion();
      }
      catch (Exception e) {
         return null;
      }
   }

   /**
    * @return The user ID or "NoLoginName" if not known
    */
   public String getUserId() {
      I_SecurityQos securityQos = getSecurityQos();
      if (securityQos==null)
         return "NoLoginName";
      else
         return securityQos.getUserId();
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
    * Unique id of the xmlBlaster server (or a client), changes on each restart. 
    * If 'node/heron' is restarted, the instanceId changes.
    * @return nodeId + timestamp, '/node/heron/instanceId/33470080380'
    */
   public String getInstanceId() {
      return this.serverInstanceId;
   }

   /**
    * Unique id of the xmlBlaster server (or a client), changes on each restart. 
    * If 'node/heron' is restarted, the instanceId changes.
    * @param instanceId e.g. '/node/heron/instanceId/33470080380'
    */
   public void setInstanceId(String instanceId) {
      this.serverInstanceId = instanceId;
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
    * @param extraOffset indenting of tags for nice output
    * @param flag For example Constants.TOXML_FLAG_NOSECURITY
    */
   public String toXml(String extraOffset, int flag) {
      return this.factory.writeObject(this, extraOffset, flag);
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
         newOne.refreshSession = (PropBoolean)this.refreshSession.clone();
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
