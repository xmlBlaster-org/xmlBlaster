/*------------------------------------------------------------------------------
Name:      ConnectQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: ConnectQos.java,v 1.37 2002/12/24 14:15:33 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.storage.QueueProperty;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.address.ServerRef;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xml.sax.Attributes;
import java.util.Vector;
import java.util.ArrayList;
import java.io.Serializable;


/**
 * This class encapsulates the qos of a login() or connect(). 
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>login</b> qos could look like this:<br />
 * <pre>
 *     &lt;qos>
 *        &lt;securityService type="htpasswd" version="1.0">
 *          &lt;![CDATA[
 *          &lt;user>joe&lt;/user>
 *          &lt;passwd>secret&lt;/passwd>
 *          ]]>
 *        &lt;/securityService>
 *        &lt;session name='/node/heron/client/joe/-9' timeout='3600000' maxSessions='10' clearSessions='false' sessionId='4e56890ghdFzj0'/>
 *        &lt;ptp>true&lt;/ptp>
 *        &lt;!-- The client side queue: -->
 *        &lt;queue relating='client' type='CACHE' version='1.0' maxMsg='1000' maxBytes='4000' onOverflow='exception'>
 *           &lt;address type='IOR' sessionId='4e56890ghdFzj0'>
 *              IOR:10000010033200000099000010....
 *           &lt;/address>
 *        &lt;queue>
 *        &lt;!-- The server side callback queue: -->
 *        &lt;queue relating='session' type='CACHE' version='1.0' maxMsg='1000' maxBytes='4000' onOverflow='deadMessage'>
 *           &lt;callback type='IOR' sessionId='4e56890ghdFzj0'>
 *              IOR:10000010033200000099000010....
 *              &lt;burstMode collectTime='400' />
 *           &lt;/callback>
 *        &lt;queue>
 *     &lt;/qos>
 * </pre>
 * NOTE: As a user of the Java client helper classes (client.protocol.XmlBlasterConnection)
 * you don't need to create the <pre>&lt;callback></pre> element.
 * This is generated automatically from the XmlBlasterConnection class when instantiating
 * the callback driver.
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see org.xmlBlaster.test.classtest.ConnectQosTest
 */
public class ConnectQos extends org.xmlBlaster.util.XmlQoSBase implements Serializable
{
   private String ME = "ConnectQos";
   private transient Global glob = null;
   private transient LogChannel log = null;

   /** PtP messages wanted?
    * <p />
    * <pre>
    * &lt;ptp>false&lt;ptp/> <!-- Don't send me any PtP messages (prevents spamming) -->
    * </pre>
    */
   protected boolean ptpAllowed = true;

   /**
    * Allows to mark that we are an xmlBlaster cluster node.
    */
   protected boolean isClusterNode = false;

   /**
    * If duplicateUpdates=false we will send only one update, even if
    * the same client subscribed multiple times on the same message.
    * Defaults to true.
    */
   protected boolean duplicateUpdates = true;

   /** Default session span of life is one day, given in millis "-session.timeout 86400000" */
   protected long sessionTimeout = Constants.DAY_IN_MILLIS;

   /** Maximum of ten parallel logins for the same client "session.maxSessions 10" */
   public static final int DEFAULT_maxSessions = 10;
   protected int maxSessions = DEFAULT_maxSessions;

   /** Clear on login all other sessions of this user (for recovery reasons) "session.clearSessions false" */
   protected boolean clearSessions = false;

   /** Passing own secret sessionId is not yet supported */
   protected String sessionId = null;
   /** The unified session name which is a clusterwide unique identifier
   */
   protected SessionName sessionName;

   protected transient PluginLoader pMgr;
   protected I_ClientPlugin plugin;
   protected I_SecurityQos securityQos;
   protected transient String tmpSecurityPluginType = null;
   protected transient String tmpSecurityPluginVersion = null;

   /**
    * The server reference, e.g. the CORBA IOR string or the XML-RPC url
    * This is returned from XmlBlaster connect() and not used for login
    */
   private transient boolean inServerRef = false;
   private transient ServerRef tmpServerRef = null;
   protected transient ServerRef[] serverRefArr = null;
   protected Vector serverRefVec = new Vector();  // <serverRef type="IOR">IOR:000122200...</serverRef>


   // helper flags for SAX parsing
   private transient boolean inQueue = false;
   private transient boolean inSecurityService = false;
   private transient boolean inSession = false;
   private transient boolean inSessionId = false;
   private transient boolean inCallback = false;
   private transient boolean inAddress = false;
   
   /** Helper for SAX parsing */
   private transient CbQueueProperty tmpCbProp = null;
   /** Helper for SAX parsing */
   private transient CallbackAddress tmpCbAddr = null;

   /** Holding queue properties for the server side callback queue */
   protected transient Vector cbQueuePropertyVec = new Vector();
   /** Holding queue property if subject related, a reference to a cbQueuePropertyVec entry */
   private transient CbQueueProperty subjectCbQueueProperty = null;
   /** Holding queue property if session related, a reference to a cbQueuePropertyVec entry */
   private transient CbQueueProperty sessionCbQueueProperty = null;


   /** Holding queue properties for the client side invocation queue */
   protected transient ArrayList clientQueuePropertyList = new ArrayList();
   /** Helper for SAX parsing */
   private transient QueueProperty tmpProp = null;
   /** Helper for SAX parsing */
   private transient Address tmpAddr = null;

   /** The node id to which we want to connect */
   private String nodeId = null;

   /**
    * Default constructor for clients without asynchronous callbacks
    * and default security plugin (as specified in xmlBlaster.properties)
    * @param glob A global instance, holding properties, command line arguments and logging object
    */
   public ConnectQos(Global glob) throws XmlBlasterException
   {
      super(glob);
      initialize(glob);
   }
   
   /**
    * Parses the given ASCII login QoS. 
    * @param glob A global instance, holding properties, command line arguments and logging object
    * @param xmlQoS_literal An xml string to be parsed
    */
   public ConnectQos(Global glob, String xmlQoS_literal) throws XmlBlasterException
   {
      super(glob);
      //if (log.DUMP) log.dump(ME, "Creating ConnectQos(" + xmlQoS_literal + ")");
      //addressArr = null;
      initialize(glob);
      init(xmlQoS_literal);
      //if (log.DUMP) log.dump(ME, "Parsed ConnectQos to\n" + toXml());
   }

   /**
    * Constructor for simple access with login name and password. 
    * @param glob A global instance, holding properties, command line arguments and logging object
    * @param mechanism may be null to use the default security plugin
    *                  as specified in xmlBlaster.properties
    * @param version may be null to use the default
    * @param loginName The unique userId
    * @param password  Your credentials, depends on the plugin type
    */
   public ConnectQos(Global glob, String mechanism, String version, String loginName, String password) throws XmlBlasterException
   {
      super(glob);
      initialize(glob);
      securityQos = getPlugin(mechanism,version).getSecurityQos();
      securityQos.setUserId(loginName);
      securityQos.setCredential(password);
   }

   /**
    * For clients who whish to use the default security plugin. 
    * @param loginName The unique userId
    * @param password  Your credentials, depends on the plugin type
    */
   public ConnectQos(Global glob, String loginName, String password) throws XmlBlasterException
   {
      super(glob);
      initialize(glob);
      securityQos = getPlugin(null, null).getSecurityQos();
      securityQos.setUserId(loginName);
      securityQos.setCredential(password);
   }

   /**
    * Allows to specify how you want to identify yourself. 
    * <p />
    * Usage to login to xmlBlaster:
    * <pre>
    *    import org.xmlBlaster.authentication.plugins.simple.SecurityQos;
    *    ...
    *    ConnectQos qos = new ConnectQos(new SecurityQos("joe", "secret"));
    *    xmlBlasterConnection.connect(qos);
    * </pre>
    */
   public ConnectQos(Global glob, I_SecurityQos securityQos) throws XmlBlasterException
   {
      super(glob);
      initialize(glob);
      this.securityQos = securityQos;
   }

   /**
    * Default constructor with a specified callback address. 
    * <p />
    * @param callback The object containing the callback address.<br />
    *        To add more callbacks, us the addCallbackAddress() method.
    */
   public ConnectQos(Global glob, CallbackAddress callback) throws XmlBlasterException
   {
      super(glob);
      initialize(glob);
      addCallbackAddress(callback);
   }

   /**
    * Constructor for cluster server. 
    * <p />
    * @param nodeId The the unique cluster node id, supports configuration per node
    */
   public ConnectQos(String nodeId, Global glob) throws XmlBlasterException
   {
      super(glob);
      this.nodeId = nodeId;
      initialize(glob);
   }

   private final void initialize(Global glob) throws XmlBlasterException
   {
      tmpAddr = null;
      tmpProp = null;
      tmpCbAddr = null;
      tmpCbProp = null;
      if (glob == null) {
         //Thread.currentThread().dumpStack();
         glob = new Global();
      }
      this.glob = glob;
      this.log = glob.getLog(null);
      setSessionTimeout(glob.getProperty().get("session.timeout", Constants.DAY_IN_MILLIS)); // One day
      setMaxSessions(glob.getProperty().get("session.maxSessions", DEFAULT_maxSessions));
      clearSessions(glob.getProperty().get("session.clearSessions", false));
      if (nodeId != null) {
         setSessionTimeout(glob.getProperty().get("session.timeout["+nodeId+"]", getSessionTimeout()));
         setMaxSessions(glob.getProperty().get("session.maxSessions["+nodeId+"]", getMaxSessions()));
         clearSessions(glob.getProperty().get("session.clearSessions["+nodeId+"]", clearSessions()));
      }

      String loginName = glob.getProperty().get("loginName", "guest");
      String passwd = glob.getProperty().get("passwd", "secret");
      if (nodeId != null) {
         loginName = glob.getProperty().get("loginName["+nodeId+"]", loginName);
         passwd = glob.getProperty().get("passwd["+nodeId+"]", passwd);
      }
      if (log.TRACE) log.trace(ME, "initialize loginName=" + loginName + " passwd=" + passwd + " nodeId=" + nodeId);
      securityQos = getPlugin(null,null).getSecurityQos();
      securityQos.setUserId(loginName);
      securityQos.setCredential(passwd);
   }

   /**
    * Accessing the Callback addresses of the client
    * this may be a CORBA-IOR or email or URL ...
    * <p />
    * @return An array of CallbackAddress objects, containing the address and the protocol type
    *         If no callback available, return an array of 0 length
    */
    /*
   public CallbackAddress[] getCallbackAddresses()
   {
      if (addressArr == null) {
         addressArr = new CallbackAddress[addressVec.size()];
         for (int ii=0; ii<addressArr.length; ii++)
            addressArr[ii] = (CallbackAddress)addressVec.elementAt(ii);
      }
      return addressArr;
   }
      */

   /**
    * Returns never null. 
    * <p />
    * If no CbQueueProperty exists, a RELATING_SESSION queue property object is created
    * on the fly.
    * <p />
    * If more than one CbQueueProperty exists, the first is chosen. (Verify this behavior)!
    */
   public final CbQueueProperty getCbQueueProperty() {
      if (cbQueuePropertyVec.size() > 0)
         return (CbQueueProperty)cbQueuePropertyVec.elementAt(0);

      addCbQueueProperty(new CbQueueProperty(glob, Constants.RELATING_SESSION, nodeId));
      return (CbQueueProperty)cbQueuePropertyVec.elementAt(0);
   }

   /**
    * Returns never null. 
    * The subjectQueue has never callback addresses, the addresses of the sessions are used
    * if configured.
    */
   public final CbQueueProperty getSubjectCbQueueProperty() {
      if (this.subjectCbQueueProperty == null) {
         this.subjectCbQueueProperty = new CbQueueProperty(glob, Constants.RELATING_SUBJECT, nodeId);
      }
      return this.subjectCbQueueProperty;
   }

   public void setSubjectCbQueueProperty(CbQueueProperty subjectCbQueueProperty) {
      this.subjectCbQueueProperty = subjectCbQueueProperty;
   }

   public boolean subjectCbQueuePropertyIsInitialized() {
      return (this.subjectCbQueueProperty != null);
   }

   /**
    * Returns never null
    */
   public final CbQueueProperty getSessionCbQueueProperty() {
      if (this.sessionCbQueueProperty == null)
         this.sessionCbQueueProperty = new CbQueueProperty(glob, Constants.RELATING_SESSION, nodeId);
      return this.sessionCbQueueProperty;
   }

   /**
    * Allows to set or overwrite the parsed security plugin. 
    * <p />
    * &lt;securityService type='simple' version='1.0'>...&lt;/securityService>
    */
   public final void setSecurityPluginData(String mechanism, String version, String loginName, String password) throws XmlBlasterException
   {
      org.xmlBlaster.client.PluginLoader loader = glob.getClientSecurityPluginLoader();
      I_ClientPlugin plugin = loader.getClientPlugin(mechanism, version);
      securityQos = plugin.getSecurityQos();
      securityQos.setUserId(loginName);
      securityQos.setCredential(password);
   }

   /**
    * Allows to set or overwrite the login name. 
    * <p />
    * @param loginName The unique user id
    */
   public final void setUserId(String loginName) throws XmlBlasterException
   {
      if (securityQos == null) {
         org.xmlBlaster.client.PluginLoader loader = glob.getClientSecurityPluginLoader();
         I_ClientPlugin plugin = loader.getClientPlugin(null, null);
         securityQos = plugin.getSecurityQos();
         String passwd = glob.getProperty().get("passwd", "secret");
         if (nodeId != null) {
            passwd = glob.getProperty().get("passwd["+nodeId+"]", passwd);
         }
         if (log.TRACE) log.trace(ME, "Initializing loginName=" + loginName + " passwd=" + passwd + " nodeId=" + nodeId);
         securityQos.setCredential(passwd);
      }
      securityQos.setUserId(loginName);
   }

   /**
    * @param mechanism If null, the current plugin is used
    */
   protected I_ClientPlugin getPlugin(String mechanism, String version) throws XmlBlasterException
   {
      // Performance TODO: for 'null' mechanism every time a new plugin is incarnated
      if (plugin==null || !plugin.getType().equals(mechanism) || !plugin.getVersion().equals(version)) {
         if (pMgr==null) pMgr=glob.getClientSecurityPluginLoader();
         try {
            if (mechanism != null)
               plugin=pMgr.getClientPlugin(mechanism, version);
            else
               plugin=pMgr.getCurrentClientPlugin();
         }
         catch (XmlBlasterException e) {
            log.error(ME+".ConnectQos", "Security plugin initialization failed. Reason: "+e.toString());
            log.error(ME+".ConnectQos", "No plugin. Trying to continue without the plugin.");
         }
      }

      return plugin;
   }

   /**
    * Return the SecurityPlugin specific information.
    * <p/>
    * @return String depending on plugin, or null e.g.
    * <pre>
    *  &lt;securityService type=\"gui\" version=\"3.0\">
    *     &lt;![CDATA[
    *        &lt;user>aUser&lt;/user>
    *        &lt;passwd>theUsersPwd&lt;/passwd>
    *     ]]>
    *  &lt;/securityService>
    * </pre>
    */
   public String getSecurityData() {
      if (securityQos != null)
         return securityQos.toXml("   ");
      else
         return null;
   }

   /**
    * Allows to specify how you want to identify yourself. 
    * <p />
    * Usage to login to xmlBlaster:
    * <pre>
    *    import org.xmlBlaster.authentication.plugins.simple.SecurityQos;
    *    ...
    *    ConnectQos qos = new ConnectQos(null);
    *    qos.setCredential(new SecurityQos("joe", "secret"));
    *    xmlBlasterConnection.connect(qos);
    * </pre>
    */
   public final void setSecurityQos(I_SecurityQos securityQos)
   {
      this.securityQos = securityQos;
   }

   /**
    * Allows to set session specific informations. 
    *
    * @param timeout The login session will be destroyed after given milliseconds.<br />
    *                Session lasts forever if set to 0L
    * @param maxSessions The client wishes to establish this maximum of sessions in parallel
    */
   public final void setSessionData(long timeout, int maxSessions)
   {
      this.sessionTimeout = timeout;
      this.maxSessions = maxSessions;
   }

   /**
    * Timeout until session expires if no communication happens
    */
   public final long getSessionTimeout()
   {
      return this.sessionTimeout;
   }

   /**
    * Timeout until session expires if no communication happens
    * @param timeout The login session will be destroyed after given milliseconds.<br />
    *                Session lasts forever if set to 0L
    */
   public final void setSessionTimeout(long timeout)
   {
      if (timeout < 0L)
         this.sessionTimeout = 0L;
      else
         this.sessionTimeout = timeout;
   }

   /**
    * If maxSession == 1, only a single login is possible
    */
   public final int getMaxSessions()
   {
      return this.maxSessions;
   }

   /**
    * If maxSession == 1, only a single login is possible
    * @param max How often the same client may login
    */
   public final void setMaxSessions(int max)
   {
      if (max < 0)
         this.maxSessions = 0;
      else
         this.maxSessions = max;
   }

   /**
    * If clearSessions is true, all old sessions of this user are discarded. 
    */
   public final boolean clearSessions()
   {
      return this.clearSessions;
   }

   /**
    * If clearSessions is true, all old sessions of this user are discarded. 
    * @param clear Defaults to false
    */
   public void clearSessions(boolean clear)
   {
      this.clearSessions = clear;
   }

   /**
    * Set our session identifier which authenticates us for xmlBlaster. 
    * <p />
    * This is used server side only.
    * @param id The unique and secret sessionId
    */
   public void setSessionId(String id)
   {
      if(id==null || id.equals("")) id = null;
      sessionId = id;
   }

   /**
    * Get our secret session identifier which authenticates us for xmlBlaster. 
    * <p />
    * @return The unique, secret sessionId
    */
   public final String getSessionId()
   {
      return sessionId;
   }

   /**
    * Set our unique SessionName. 
    * @param sessionName
    */
   public void setSessionName(SessionName sessionName) {
      this.sessionName = sessionName;
   }

   /**
    * Get our unique SessionName. 
    * <p />
    * @return The unique SessionName (null if not known)
    */
   public final SessionName getSessionName() {
      return this.sessionName;
   }

   /**
    * Accessing the ServerRef addresses of the xmlBlaster server
    * this may be a CORBA-IOR or email or URL ...
    * <p />
    * Only for results of connect() calls (used by clients)
    * @return An array of ServerRef objects, containing the address and the protocol type
    *         If no serverRef available, return an array of 0 length
    */
   public final ServerRef[] getServerRefs()
   {
      if (serverRefArr == null) {
         serverRefArr = new ServerRef[serverRefVec.size()];
         for (int ii=0; ii<serverRefArr.length; ii++)
            serverRefArr[ii] = (ServerRef)serverRefVec.elementAt(ii);
      }
      return serverRefArr;
   }


   /**
    * Accessing the ServerRef address of the xmlBlaster server
    * this may be a CORBA-IOR or email or URL ...
    * <p />
    * Only for results of connect() calls (used by clients)
    * @return The first ServerRef object in the list, containing the address and the protocol type
    *         If no serverRef available we return null
    */
   public final ServerRef getServerRef()
   {
      if (serverRefArr == null) {
         serverRefArr = new ServerRef[serverRefVec.size()];
         for (int ii=0; ii<serverRefArr.length; ii++)
            serverRefArr[ii] = (ServerRef)serverRefVec.elementAt(ii);
      }
      if (serverRefArr.length > 0)
         return serverRefArr[0];

      return null;
   }

   /**
    * Adds a server reference
    */
   public final void setServerRef(ServerRef addr) {
      serverRefVec.addElement(addr);
      serverRefArr = null; // reset to be recalculated on demand
   }

   /**
    * @param Set if we accept point to point messages
    */
   public final void setPtpAllowed(boolean ptpAllowed) {
      this.ptpAllowed = ptpAllowed;
   }

   /**
    * Allow to receive Point to Point messages (default). 
    * Every callback may suppress PtP as well.
    */
   public final void allowPtP() {
      setPtpAllowed(true);
   }

   /**
    * I don't want to receive any PtP messages.
    */
   public void disallowPtP() {
      setPtpAllowed(false);
   }

   /**
    * @param Set if we are a cluster node. 
    */
   public final void setIsClusterNode(boolean isClusterNode) {
      this.isClusterNode = isClusterNode;
   }

   /**
    * @return Are we a cluster?
    */
   public final boolean isClusterNode() {
      return this.isClusterNode;
   }

   /**
    * @param Set if we allow multiple updates for the same message if we have subscribed multiple times to it. 
    */
   public final void setDuplicateUpdates(boolean duplicateUpdates) {
      this.duplicateUpdates = duplicateUpdates;
   }

   /**
    * @return Are we a cluster?
    */
   public final boolean duplicateUpdates() {
      return this.duplicateUpdates;
   }

   /**
    * Add an address to which we want to connect, with all the configured parameters. 
    * <p />
    * @param address  An object containing the protocol (e.g. EMAIL) the address (e.g. hugo@welfare.org) and the connection properties
    */
   public final void setAddress(Address address) {
      QueueProperty prop = new QueueProperty(glob, nodeId); // Use default queue properties for this xmlBlaster access address
      prop.setAddress(address);
      addClientQueueProperty(prop);
      //clientQueuePropertyArr = null; // reset to be recalculated on demand
   }

   /**
    * The connection address and properties of the xmlBlaster server
    * we want connect to.
    * @return never null
    */
   public final Address getAddress() {
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
   public final AddressBase[] getAddresses() {
      return getClientQueueProperty().getAddresses();
   }

   /**
    * Adds a queue description. 
    * This allows to set all supported attributes of a client side queue and an address to reach xmlBlaster
    * @param prop The property object of the client side queue which shall be established. 
    * @see org.xmlBlaster.util.qos.address.Address
    */
   public final void addClientQueueProperty(QueueProperty prop) {
      if (prop == null) return;
      clientQueuePropertyList.add(prop);
   }

   /**
    * @return never null
    */
   public QueueProperty getClientQueueProperty() {
      if (clientQueuePropertyList.size() < 1) {
         if (log.TRACE) log.trace(ME, "Creating default server address instance");
         setAddress(glob.getBootstrapAddress());
      }
      if (clientQueuePropertyList.size() < 1) {
         log.error(ME, "Internal error, can't access address instance");
         throw new IllegalArgumentException(ME + ": Internal error, can't access address instance");
      }
      return (QueueProperty)clientQueuePropertyList.get(0);
   }

   /**
    * Add a callback address where to send the message (for PtP). 
    * <p />
    * Creates a default CbQueueProperty object to hold the callback address argument.<br />
    * Note you can invoke this multiple times to allow multiple callbacks.
    * @param callback  An object containing the protocol (e.g. EMAIL) and the address (e.g. hugo@welfare.org)
    */
   public final void addCallbackAddress(CallbackAddress callback) {
      CbQueueProperty prop = new CbQueueProperty(glob, null, nodeId); // Use default queue properties for this callback address
      prop.setCallbackAddress(callback);
      addCbQueueProperty(prop);
      //queuePropertyArr = null; // reset to be recalculated on demand
   }

   /**
    * Adds a queue description. 
    * This allows to set all supported attributes of a callback queue and a callback address
    * @param prop The property object of the callback queue which shall be established in the server for calling us back.
    * @see org.xmlBlaster.util.qos.address.CallbackAddress
    */
   public final void addCbQueueProperty(CbQueueProperty prop) {
      if (prop == null) return;
      if (prop.isSessionRelated()) {
         if (this.sessionCbQueueProperty != null) {
            log.warn(ME, "addCbQueueProperty() overwrites previous session queue setting");
            Thread.currentThread().dumpStack();
         }
         this.sessionCbQueueProperty = prop;
         cbQueuePropertyVec.addElement(prop);
      }
      else if (prop.isSubjectRelated()) {
         if (this.subjectCbQueueProperty != null) log.warn(ME, "addCbQueueProperty() overwrites previous subject queue setting");
         this.subjectCbQueueProperty = prop;
         cbQueuePropertyVec.addElement(prop);
      }
   }

   /**
    * @return the login credentials or null if not set
    */
   public final I_SecurityQos getSecurityQos() throws XmlBlasterException
   {
      return this.securityQos;
   }

   /**
    * Return the type of the referenced SecurityPlugin.
    * <p/>
    * @return String
    */
   public final String getSecurityPluginType() throws XmlBlasterException
   {
      if (securityQos != null)
         return securityQos.getPluginType();
      return null;
   }

   /**
    * Return the version of the referenced SecurityPlugin.
    * <p/>
    * @return String
    */
   public final String getSecurityPluginVersion() throws XmlBlasterException
   {
      if (securityQos != null)
         return securityQos.getPluginVersion();
      return null;
   }

   public final String getUserId()
   {
      if (securityQos==null)
         return "NoLoginName";
      else
         return securityQos.getUserId();
   }

   

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public void startElement(String uri, String localName, String name, Attributes attrs)
   {
      if (super.startElementBase(uri, localName, name, attrs) == true)
         return;

      //if (log.TRACE) log.trace(ME, "Entering startElement for uri=" + uri + " localName=" + localName + " name=" + name);

      if (name.equalsIgnoreCase("serverRef")) {
         inServerRef = true;
         String tmp = character.toString().trim(); // The address (if before inner tags)
         String type = null;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
                if( attrs.getQName(i).equalsIgnoreCase("type") ) {
                  type = attrs.getValue(i).trim();
                  break;
                }
            }
         }
         if (type == null) {
            log.error(ME, "Missing 'serverRef' attribute 'type' in login-qos");
            type = "IOR";
         }
         tmpServerRef = new ServerRef(type);
         if (tmp.length() > 0) {
            tmpServerRef.setAddress(tmp);
            character.setLength(0);
         }
         return;
      }

      if (inCallback) {
         tmpCbAddr.startElement(uri, localName, name, character, attrs);
         return;
      }

      if (name.equalsIgnoreCase("callback")) {
         inCallback = true;
         if (!inQueue) {
            tmpCbProp = new CbQueueProperty(glob, null, null); // Use default queue properties for this callback address
            addCbQueueProperty(tmpCbProp);
         }
         tmpCbAddr = new CallbackAddress(glob);
         tmpCbAddr.startElement(uri, localName, name, character, attrs);
         tmpCbProp.setCallbackAddress(tmpCbAddr);
         return;
      }

      if (name.equalsIgnoreCase("address")) {
         inAddress = true;
         if (!inQueue) {
            tmpProp = new QueueProperty(glob, null); // Use default queue properties for this connection address
            addClientQueueProperty(tmpProp);
         }
         tmpAddr = new Address(glob);
         tmpAddr.startElement(uri, localName, name, character, attrs);
         tmpProp.setAddress(tmpAddr);
         return;
      }

      if (name.equalsIgnoreCase("queue")) {
         inQueue = true;
         if (inCallback) {
            log.error(ME, "<queue> tag is not allowed inside <callback> tag, element ignored.");
            character.setLength(0);
            return;
         }
         if (inAddress) {
            log.error(ME, "<queue> tag is not allowed inside <address> tag, element ignored.");
            character.setLength(0);
            return;
         }
         String related = attrs.getValue("relating");
         if ("client".equalsIgnoreCase(related)) {
            tmpProp = new QueueProperty(glob, null);
            tmpProp.startElement(uri, localName, name, attrs);
            addClientQueueProperty(tmpProp);
         }
         else {
            tmpCbProp = new CbQueueProperty(glob, null, null);
            tmpCbProp.startElement(uri, localName, name, attrs);
            addCbQueueProperty(tmpCbProp);
         }
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("securityService")) {
         inSecurityService = true;
         boolean existsTypeAttr = false;
         boolean existsVersionAttr = false;
         if (attrs != null) {
            int len = attrs.getLength();
            int ii=0;
            for (ii = 0; ii < len; ii++) {
               if (attrs.getQName(ii).equalsIgnoreCase("type")) {
                  existsTypeAttr = true;
                  tmpSecurityPluginType = attrs.getValue(ii).trim();
               }
               else if (attrs.getQName(ii).equalsIgnoreCase("version")) {
                  existsVersionAttr = true;
                  tmpSecurityPluginVersion = attrs.getValue(ii).trim();
               }
            }
         }
         if (!existsTypeAttr) log.error(ME, "Missing 'type' attribute in login-qos <securityService>");
         if (!existsVersionAttr) log.error(ME, "Missing 'version' attribute in login-qos <securityService>");
         character.setLength(0);
         // Fall through and collect xml, will be parsed later by appropriate security plugin
      }

      if (name.equalsIgnoreCase("session")) {
         inSession = true;
         if (attrs != null) {
            int len = attrs.getLength();
            int ii=0;
            for (ii = 0; ii < len; ii++) {
               if (attrs.getQName(ii).equalsIgnoreCase("name"))
                  this.sessionName = new SessionName(glob, attrs.getValue(ii).trim());
               else if (attrs.getQName(ii).equalsIgnoreCase("timeout"))
                  this.sessionTimeout = (new Long(attrs.getValue(ii).trim())).longValue();
               else if (attrs.getQName(ii).equalsIgnoreCase("maxSessions"))
                  this.maxSessions = (new Integer(attrs.getValue(ii).trim())).intValue();
               else if (attrs.getQName(ii).equalsIgnoreCase("clearSessions"))
                  this.clearSessions = (new Boolean(attrs.getValue(ii).trim())).booleanValue();
               else
                  log.warn(ME, "Ignoring unknown attribute '" + attrs.getQName(ii) + "' of <session> element");
            }
         }
         character.setLength(0);

         return;
      }

      if (name.equalsIgnoreCase("ptp")) {
         setPtpAllowed(true);
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("isClusterNode")) {
         setIsClusterNode(true);
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("duplicateUpdates")) {
         setDuplicateUpdates(true);
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("sessionId")) {
         if (!inSession)
            return;
         inSessionId = true;
         character.setLength(0);
         return;
      }

      if (inSecurityService) {
         //Collect everything in character buffer
         character.append("<").append(name);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               character.append(" ").append(attrs.getQName(i)).append("=\"").append(attrs.getValue(i)).append("\"");
            }
         }
         character.append(">");
         if (name.equalsIgnoreCase("securityService"))
            character.append("<![CDATA[");
      }
   }


   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name)
   {
      if (super.endElementBase(uri, localName, name) == true)
         return;

      //if (log.TRACE) log.trace(ME, "Entering endElement for " + name);

      if (name.equalsIgnoreCase("serverRef")) {
         inServerRef = false;
         String tmp = character.toString().trim(); // The address (if after inner tags)
         if (tmpServerRef != null) {
            if (tmp.length() > 0) tmpServerRef.setAddress(tmp);
            serverRefVec.addElement(tmpServerRef);
         }
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("queue")) {
         inQueue = false;
         character.setLength(0);
         return;
      }

      if (inCallback) {
         if (name.equalsIgnoreCase("callback")) inCallback = false;
         tmpCbAddr.endElement(uri, localName, name, character);
         return;
      }

      if (inAddress) {
         if (name.equalsIgnoreCase("address")) inAddress = false;
         tmpAddr.endElement(uri, localName, name, character);
         return;
      }

      if (name.equalsIgnoreCase("ptp")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            setPtpAllowed(new Boolean(tmp).booleanValue());
         return;
      }

      if (name.equalsIgnoreCase("isClusterNode")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            setIsClusterNode(new Boolean(tmp).booleanValue());
         return;
      }

      if (name.equalsIgnoreCase("duplicateUpdates")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            setDuplicateUpdates(new Boolean(tmp).booleanValue());
         return;
      }

      if (name.equalsIgnoreCase("sessionId")) {
         if (inSession) {
            inSessionId = false;
            setSessionId(character.toString().trim());
         }
      }

      if (name.equalsIgnoreCase("securityService")) {
        inSecurityService = false;
        character.append("]]>\n");
        character.append("</").append(name).append(">");
        String tmp = character.toString().trim();
        // delegate the collected tags to our security plugin
        try {
           securityQos = getPlugin(tmpSecurityPluginType, tmpSecurityPluginVersion).getSecurityQos();
           /*
           "<securityService type=\""+tmpSecurityPluginType+"\" version=\""+tmpSecurityPluginVersion+"\">\n"+
               <user>user</user>
               <passwd>passwd</passwd>
            "</securityService>";
           */
           securityQos.parse(tmp);
         }
         catch(XmlBlasterException e) {
            log.warn(ME, "Can't parse security string - " + e.toString() + "\n Check:\n" + tmp);
            throw new StopParseException(); // Enough error handling??
         }
      }

      if (name.equalsIgnoreCase("session")) {
         inSession = false;
      }

      if (inSecurityService) {
         character.append("</"+name+">");
      }

   }


   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString()
   {
      return toXml();
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * The default is to include the security string
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml()
   {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml(String extraOffset)
   {
      if(plugin!=null && securityQos==null) {
         try {
            securityQos = getPlugin(null,null).getSecurityQos();
         } catch(XmlBlasterException e) {
            log.warn(ME+".toXml", e.toString());
         }
      }

      StringBuffer sb = new StringBuffer(1000);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<qos>");

      // <securityService ...
      if(securityQos!=null) sb.append(securityQos.toXml(extraOffset)); // includes the qos of the ClientSecurityHelper

      sb.append(offset).append(" <ptp>").append(ptpAllowed).append("</ptp>");
   
      if (isClusterNode())
         sb.append(offset).append(" <isClusterNode>").append(isClusterNode()).append("</isClusterNode>");

      if (duplicateUpdates() == false)
         sb.append(offset).append(" <duplicateUpdates>").append(duplicateUpdates()).append("</duplicateUpdates>");

      sb.append(offset).append(" <session timeout='").append(sessionTimeout);
      sb.append("' maxSessions='").append(maxSessions);
      sb.append("' clearSessions='").append(clearSessions());
      if (getSessionName() != null)
         sb.append("' name='").append(getSessionName().getAbsoluteName());
      if(sessionId!=null) {
         sb.append("'>");
         sb.append(offset).append("  <sessionId>").append(sessionId).append("</sessionId>");
         sb.append(offset).append(" </session>");
      }
      else
         sb.append("'/>");

      for (int ii=0; ii<clientQueuePropertyList.size(); ii++) {
         QueueProperty ad = (QueueProperty)clientQueuePropertyList.get(ii);
         sb.append(ad.toXml(extraOffset+Constants.INDENT));
      }
      
      for (int ii=0; ii<cbQueuePropertyVec.size(); ii++) {
         CbQueueProperty ad = (CbQueueProperty)cbQueuePropertyVec.elementAt(ii);
         sb.append(ad.toXml(extraOffset+Constants.INDENT));
      }
      
      for (int ii=0; ii<serverRefVec.size(); ii++) {
         ServerRef ref = (ServerRef)serverRefVec.elementAt(ii);
         sb.append(ref.toXml(extraOffset+Constants.INDENT));
         if (ii < serverRefVec.size()-1)
            sb.append("\n");
      }

      sb.append(offset).append("</qos>");

      return sb.toString();
   }

   /**
    * Get a usage string for the connection parameters
    */
   public final String usage()
   {
      String text = "\n";
      text += "Control my session and security settings\n";
      text += "   -session.timeout    How long lasts our login session in milliseconds, 0 is forever, defaults to one day [" + Constants.DAY_IN_MILLIS + "].\n";
      text += "   -session.maxSessions     Maximum number of simultanous logins per client [" + DEFAULT_maxSessions + "].\n";
      text += "   -session.clearSessions   Kill other sessions running under my login name [false]\n";
      text += "   -security.plugin.type    The security plugin to use [simple]\n";
      text += "   -security.plugin.version The version of the plugin [1.0]\n";
      text += "   -loginName          The name for login []\n";
      text += "   -passwd             My password []\n";
      text += "\n";
      return text;
   }

   /** For testing: java org.xmlBlaster.util.ConnectQos */
   public static void main(String[] args)
   {
      try {
         Global glob = new Global(args);
         ConnectQos qos;
         qos = new ConnectQos(glob, new CallbackAddress(glob, "IOR"));
         I_SecurityQos securityQos = new org.xmlBlaster.authentication.plugins.simple.SecurityQos(glob, "joe", "secret");
         qos.setSecurityQos(securityQos);
         qos.getAddress(); // Force creation of default address
         System.out.println("Output from manually crafted QoS:\n" + qos.toXml());

         String xml =
            "<qos>\n" +
            /*
            "   <securityService type='htpasswd' version='1.0'>\n" +
            "      <![CDATA[\n" +
            "         <user>aUser</user>\n" +
            "         <passwd>theUsersPwd</passwd>\n" +
            "      ]]>\n" +
            "   </securityService>\n" +
            */
            "   <ptp>true</ptp>\n" +
            "   <isClusterNode>true</isClusterNode>\n" +
            "   <duplicateUpdates>false</duplicateUpdates>\n" +
            "   <session timeout='3600000' maxSessions='20' clearSessions='false'>\n" +
            "      <sessionId>anId</sessionId>\n" +
            "   </session>\n" +
            "   <queue relating='session' maxMsg='1000' maxBytes='4000' onOverflow='deadMessage'>\n" +
            "      <callback type='IOR' sessionId='4e56890ghdFzj0' pingInterval='60000' retries='1' delay='60000' useForSubjectQueue='true'>\n" +
            "         <ptp>true</ptp>\n" +
            "         IOR:00011200070009990000....\n" +
            "         <compress type='gzip' minSize='1000' />\n" +
            "         <burstMode collectTime='400' />\n" +
            "      </callback>\n" +
            "   </queue>\n" +
            "   <callback type='IOR'>\n" +
            "      IOR:00000461203\n" +
            "   </callback>\n" +
            "   <callback type='EMAIL'>\n" +
            "      et@mars.universe\n" +
            "      <ptp>false</ptp>\n" +
            "   </callback>\n" +
            "   <callback type='XML-RPC'>\n" +
            "      <ptp>true</ptp>\n" +
            "      http:/www.mars.universe:8080/RPC2\n" +
            "   </callback>\n" +
            "   <queue relating='session' maxMsg='1600' maxBytes='2000'/>\n" +
            "   <queue relating='client' maxMsg='9600' maxBytes='92000' expires='960000000'>\n" +
            "      <address type='IOR' sessionId='clientAAXX' pingInterval='99000' retries='9' delay='90000'>\n" +
            "         IOR:00011200070009990000....\n" +
            "         <compress type='gzip' minSize='1000' />\n" +
            "         <burstMode collectTime='400' />\n" +
            "      </address>\n" +
            "   </queue>\n" +
            "   <serverRef type='IOR'>\n" +
            "      IOR:00011200070009990000....\n" +
            "   </serverRef>\n" +
            "   <serverRef type='EMAIL'>\n" +
            "      et@mars.universe\n" +
            "   </serverRef>\n" +
            "   <serverRef type='XML-RPC'>\n" +
            "      http:/www.mars.universe:8080/RPC2\n" +
            "   </serverRef>\n" +
            "</qos>\n";

         System.out.println("=====Original XML========:\n");
         System.out.println(xml);
         qos = new ConnectQos(glob, xml);
         System.out.println("=====Parsed and dumped===:\n");
         System.out.println(qos.toXml());
         
         qos.setSecurityPluginData("htpasswd", "1.0", "joe", "secret");
         System.out.println("=====Added security======\n");
         System.out.println(qos.toXml());
      }
      catch(Throwable e) {
         e.printStackTrace();
         Global.instance().getLog(null).error("TestFailed", e.toString());
      }
   }
}
