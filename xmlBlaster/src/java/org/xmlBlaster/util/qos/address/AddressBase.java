/*------------------------------------------------------------------------------
Name:      AddressBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding connect address and callback address string including protocol
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.address;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;
import org.xml.sax.Attributes;

import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.property.PropEntry;
import org.xmlBlaster.util.property.PropString;
import org.xmlBlaster.util.property.PropInt;
import org.xmlBlaster.util.property.PropLong;
import org.xmlBlaster.util.property.PropBoolean;

import java.util.Hashtable;
import java.util.Properties;


/**
 * Abstract helper class holding connect address and callback address string
 * and protocol string.
 * <p />
 * See examples in the implementing classes
 * @see Address
 * @see CallbackAddress
 * @see org.xmlBlaster.test.classtest.qos.AddressBaseTest
 */
public abstract class AddressBase
{
   private static final String ME = "AddressBase";
   protected final Global glob;
   protected final LogChannel log;

   private Hashtable pluginAttributes;
   private Properties pluginInfoParameters = new Properties();

   /** The root xml element: &lt;callback> or &lt;address>, is set from the derived class */
   protected String rootTag = null;

   protected String instanceName;

   protected final String className = "dispatch";
   protected final String context = null;
   /** For example "plugin/socket/" */
   protected String envPrefix = "";

   /** The unique address, e.g. the CORBA IOR string */
   private PropString rawAddress = new PropString("");

   private PropString bootstrapHostname = new PropString(""); // initially not "localhost" to ask bootstrap hostname

   public static final int DEFAULT_bootstrapPort = Constants.XMLBLASTER_PORT; // 3412
   private PropInt bootstrapPort = new PropInt(DEFAULT_bootstrapPort);

   /** The unique protocol type, e.g. "IOR" */
   public static final String DEFAULT_type = "IOR";
   protected PropString type = new PropString(DEFAULT_type);
   
   /** The protocol version, e.g. "1.0" */
   public static final String DEFAULT_version = "1.0";
   protected PropString version = new PropString(DEFAULT_version);
   
   /** BurstMode: The time to collect messages for publish/update */
   public static final long DEFAULT_collectTime = 0L;
   protected PropLong collectTime = new PropLong(DEFAULT_collectTime);
   
   /** Ping interval: pinging every given milliseconds */
   abstract public long getDefaultPingInterval();
   protected PropLong pingInterval = new PropLong(getDefaultPingInterval());
   
   /** How often to retry if connection fails */
   abstract public int getDefaultRetries();
   protected PropInt retries = new PropInt(getDefaultRetries());
   
   /** Delay between connection retries in milliseconds */
   abstract public long getDefaultDelay();
   protected PropLong delay = new PropLong(getDefaultDelay());
   
   /**
    * Shall the update() or publish() messages be send oneway (no application level ACK). 
    * <p />
    * For more info read the CORBA spec. Only CORBA and our native SOCKET protocol support oneway.
    * Defaults to false (the update() or publish() has a return value and can throw an exception).
    */
   public static final boolean DEFAULT_oneway = false;
   protected PropBoolean oneway = new  PropBoolean(DEFAULT_oneway);
   
   public static final boolean DEFAULT_dispatcherActive = true;
   /**
    * Control if the dispatcher is activated on login, i.e. if it is 
    * able to deliver asynchronous messages from the callback queue.
    * defaults to true
    */
   protected PropBoolean dispatcherActive = new PropBoolean(DEFAULT_dispatcherActive);

   /** Compress messages if set to "gzip" or "zip" */
   public static final String DEFAULT_compressType = "";
   protected PropString compressType = new PropString("compressType", DEFAULT_compressType);
   
   /** Messages bigger this size in bytes are compressed */
   public static final long DEFAULT_minSize = 0L;
   protected PropLong minSize = new PropLong("minSize", DEFAULT_minSize);

   public static final int DEFAULT_burstModeMaxEntries = -1;
   protected PropInt burstModeMaxEntries = new PropInt(DEFAULT_burstModeMaxEntries);

   public static final long DEFAULT_burstModeMaxBytes = -1L;
   protected PropLong burstModeMaxBytes = new PropLong(DEFAULT_burstModeMaxBytes);
   
   /** PtP messages wanted? Defaults to true, false prevents spamming */
   public static final boolean DEFAULT_ptpAllowed = true;
   protected PropBoolean ptpAllowed = new  PropBoolean(DEFAULT_ptpAllowed);
   
   /** The identifier sent to the callback client, the client can decide if he trusts this invocation */
   public static final String DEFAULT_sessionId = "unknown";
   protected PropString sessionId = new PropString(DEFAULT_sessionId);

   /** Shall this session callback be used for subjectQueue messages as well? For &lt;callback> only */
   public static final boolean DEFAULT_useForSubjectQueue = true;
   protected PropBoolean useForSubjectQueue = new  PropBoolean(DEFAULT_useForSubjectQueue);

   /**
    * Does client whish a dispatcher plugin. 
    * <p>
    * Set to "undef" forces to switch off, or e.g. "Priority,1.0" to access the PriorizedDispatchPlugin
    * </p>
    * <p>
    * Setting it to 'null' (which is the default) lets the server choose the plugin
    * </p>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/dispatch.control.plugin.html">The dispatch.control.plugin requirement</a>
    */
   public String DEFAULT_dispatchPlugin = PluginManagerBase.NO_PLUGIN_TYPE; // "undef";
   protected PropString dispatchPlugin = new PropString(DEFAULT_dispatchPlugin);


   /** The node id to which we want to connect */
   protected String nodeId;

   /**
    */
   public AddressBase(Global glob, String rootTag) {
      this.glob = glob;
      this.log = glob.getLog("core");
      setRootTag(rootTag);
   }

   /**
    * @throws IllegalArgumentException Not implemented. 
    */
   public Object clone() {
      throw new IllegalArgumentException("AddressBase.clone() is not implemented");
   }

   /**
    * Configure property settings. 
    * "-/node/heron/dispatch/connection/delay 20" has precedence over "-delay 10"
    */
   protected void initialize()
   {
      /* This is always set on server side from ServerAddress.java
         but not always on client side
         Shall we switch it on always here?
      if (this.nodeId == null) {
         this.nodeId = glob.getId();
      }
      */
      // SOCKET, IOR, XMLRPC, RMI, ...
      this.type.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, "protocol");

      // dispatch/callback/plugin/socket/hostname
      // dispatch/connection/plugin/ior/localPort
      this.envPrefix = "plugin/"+this.type.getValue().toLowerCase()+"/";
      if (log.TRACE) log.trace(ME, "type=" + this.type.getValue() + " nodeId=" + this.nodeId + " context=" + context +
                         " className=" + className + " instanceName=" + this.instanceName + " envPrefix=" + this.envPrefix);

      // On server side for SOCKET protocol we support compression types:
      // Constants.COMPRESS_ZLIB_STREAM="zlib:stream" or "zlib" with minSize=1234 bytes
      // This default setting comes from environment or protocol plugin property
      // None stream compressions can be overwritten by CallbackAddress for each client individually
      // Here follows the plugin initialization, further down we overwrite this with Address specific settings
      // Example on server side: "-plugin/socket/compress/type stream"
      this.compressType = getEnv("compress/type", this.compressType.getValue());
      this.minSize = getEnv("compress/minSize", this.minSize.getValue());

      this.bootstrapHostname.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, "bootstrapHostname");
      this.bootstrapPort.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, "bootstrapPort");

      //this.bootstrapHostname.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, envPrefix+"bootstrapHostname");
      //this.bootstrapPort.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, envPrefix+"bootstrapPort");
      //log.error(ME, "DEBUG ONLY: Checking " + this.instanceName + ": " + envPrefix+"port to result=" + this.bootstrapPort.getValue() );

      // These are protocol unspecific values
      this.burstModeMaxEntries.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, "burstMode/maxEntries");
      this.burstModeMaxBytes.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, "burstMode/maxBytes");
      this.collectTime.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, "burstMode/collectTime");
      this.pingInterval.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, "pingInterval");
      this.retries.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, "retries");
      this.delay.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, "delay");
      this.oneway.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, "oneway");
      this.dispatcherActive.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, "dispatcherActive");
      this.compressType.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, "compress/type");
      this.minSize.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, "compress/minSize");
      this.ptpAllowed.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, "ptpAllowed");
      this.sessionId.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, "sessionId");
      this.dispatchPlugin.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, "DispatchPlugin/defaultPlugin");

      //log.error(ME, getType() + " " + "DEBUG ONLY " + this.compressType + " " + this.minSize + toXml());
   }

   /**
    * Set a protocol specific property. 
    * <p>
    * Setting a property here forces the setting in the plugin, it
    * has precedence over any environment, xmlBlaster.properties or command line setting.
    * <br />
    * You typically use this method in your client code to overwrite settings,.
    * please check the protocol specific documentation about the supported settings.
    * </p>
    * @param key    The property, e.g. "SOLingerTimeout" (WITHOUT any prefix like "plugin/socket/")
    *               The searched property is depending on the type (here "socket")
    *               and instance (here "connection") e.g. "plugin/socket/SOLingerTimeout"
    *               and with higher precedence "dispatch/connection/plugin/socket/SOLingerTimeout"
    * @param value  The value, e.g. "10000"
    */
   public void setPluginProperty(String key, String value) {
      if (this.pluginAttributes == null) this.pluginAttributes = new Hashtable();
      this.pluginAttributes.put(key, value);
      // refresh compressType or minSize: Those attributes are double used:
      // Once SOCKET specific and again as a common setting in <address ...>
      // TODO: clean up this mess: no SOCKET specific code in here!
      if ("compress/type".equals(key) || "compress/minSize".equals(key)) {
         initialize();
      }
   }

   /**
    * Set the PluginInfo parameters (derived from xmlBlasterPlugins.xml or xmlBlaster.properties). 
    * <br />
    * As a protocol plugin developer you should call this method if you have a PluginInfo instance
    * to use the default paramaters of the plugin.
    * <br />
    * Example from xmlBlasterPlugins.xml:
    * <br />
    *  &lt;plugin id='SOCKET_UDP' className='org.xmlBlaster.protocol.socket.SocketDriver'>
    *     ...
    *     &lt;attribute id='useUdpForOneway'>true</attribute>
    *  &lt;/plugin>
    * <p/>
    * These settings are used as default settings for the plugin with lowest priority
    * <p/>
    * Calls initialize() to reinitialize compression.
    */
   public void setPluginInfoParameters(Properties parameters) {
      if (parameters == null) {
         this.pluginInfoParameters = new Properties();
      }
      else {
         this.pluginInfoParameters = parameters;
      }
      initialize();
   }

   public String getEnvPrefix() {
      return this.envPrefix;
   }

   /**
    * Plugins may query their properties here
    * @param key  The property, e.g. "SOLingerTimeout" (WITHOUT any prefix like "plugin/socket/")
    */
   public PropString getEnv(String key, String defaultValue) {
      PropString tmp = new PropString(key, this.pluginInfoParameters.getProperty(key,defaultValue));
      if (this.pluginAttributes != null) {
         Object val = this.pluginAttributes.get(key);
         if (val != null) {
            tmp.setValue((String)val, PropEntry.CREATED_BY_SETTER);
            return tmp;
         }
      }
      tmp.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, this.envPrefix+key);
      return tmp;
   }

   /**
    * Plugins may query their properties here
    * @param key  The property, e.g. "SOLingerTimeout" (WITHOUT any prefix like "plugin/socket/")
    */
   public PropInt getEnv(String key, int defaultValue) {
      String defaultStr = this.pluginInfoParameters.getProperty(key,""+defaultValue);
      defaultValue = Integer.valueOf(defaultStr).intValue();

      PropInt tmp = new PropInt(key, defaultValue);
      if (this.pluginAttributes != null) {
         Object val = this.pluginAttributes.get(key);
         if (val != null) {
            tmp.setValue((String)val, PropEntry.CREATED_BY_SETTER);
            return tmp;
         }
      }
      tmp.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, this.envPrefix+key);
      return tmp;
   }

   /**
    * Plugins may query their properties here
    * @param key  The property, e.g. "SOLingerTimeout" (WITHOUT any prefix like "plugin/socket/")
    */
   public PropLong getEnv(String key, long defaultValue) {
      String defaultStr = this.pluginInfoParameters.getProperty(key,""+defaultValue);
      defaultValue = Long.valueOf(defaultStr).longValue();

      PropLong tmp = new PropLong(key, defaultValue);
      if (this.pluginAttributes != null) {
         Object val = this.pluginAttributes.get(key);
         if (val != null) {
            tmp.setValue((String)val, PropEntry.CREATED_BY_SETTER);
            return tmp;
         }
      }
      tmp.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, this.envPrefix+key);
      return tmp;
   }

   /**
    * Plugins may query their properties here
    * @param key  The property, e.g. "SOLingerTimeout" (WITHOUT any prefix like "plugin/socket/")
    */
   public PropBoolean getEnv(String key, boolean defaultValue) {
      String defaultStr = this.pluginInfoParameters.getProperty(key,""+defaultValue);
      defaultValue = Boolean.valueOf(defaultStr).booleanValue();

      PropBoolean tmp = new PropBoolean(key, defaultValue);
      if (this.pluginAttributes != null) {
         Object val = this.pluginAttributes.get(key);
         if (val != null) {
            tmp.setValue((String)val, PropEntry.CREATED_BY_SETTER);
            return tmp;
         }
      }
      tmp.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, this.envPrefix+key);
      return tmp;
   }

   /**
    * Returns the completed key which was found and chosen. 
    * @return For "responseTimeout" it could be "dispatch/connection/plugin/socket/responseTimeout"
    */
   public String getEnvLookupKey(String key) {
      PropString tmp = new PropString("");
      String k = tmp.setFromEnv(this.glob, this.nodeId, context, className, this.instanceName, this.envPrefix+key);
      if (k != null && k.length() > 0)
         return k;
      return key;
   }

   /**
    * A nice human readable name for this address (used for logging)
    */
   public final String getName() {
      return getLogId();
   }

   /**
    * Check if supplied address would connect to the address of this instance
    */
   public final boolean isSameAddress(AddressBase other) {
      if (other.getType().equals(getType())) {  // what about two different SOCKET connections??

         String or = other.getRawAddress();
         if (or != null && or.length() > 0) {
            if (or.equals(getRawAddress())) {
               return true;
            }
            else {
               return false;
            }
         }

         String oh = other.getBootstrapHostname();
         int op = other.getBootstrapPort();
         if (op > 0 && oh != null) {
            if (op == getBootstrapPort() && oh.equals(getBootstrapHostname()))
               return true;
            else
               return false;
         }
      }
      return false;
   }

   /**
    * Sets the root xml tag, &lt;callback> or &lt;address>
    */
   private final void setRootTag(String rootTag) {
      this.rootTag = rootTag;
   }

   /**
    * Show some important settings for logging
    */
   public String getSettings() {
      StringBuffer buf = new StringBuffer(126);
      buf.append("type=").append(getType()).append(" oneway=").append(oneway()).append(" dispatcherActive=").append(isDispatcherActive()).append(" burstMode.collectTime=").append(getCollectTime());
      return buf.toString();
   }

   /**
    * NOTE: This setting has precedence over all environment or command line settings
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XMLRPC"
    *                If you pass null the value is reset to its default setting
    */
   public final void setType(String type) {
      if (type == null) this.type.setValue(this.type.getDefaultValue(), PropEntry.CREATED_BY_DEFAULT);
      else this.type.setValue(type);
   }

   /**
    * @param version   The protocol version, e.g. "1.0"
    */
   public final void setVersion(String version) {
      this.version.setValue(version);
   }

   /**
    * @return A human readable address for logging only
    */
   public String getLogId() {
      if (getRawAddress() != null && getRawAddress().length() > 0 && getRawAddress().length() < 50) {
         return getRawAddress();
      }
      return getBootstrapUrl();
   }

   /**
    * Updates the internal address as well. 
    * <p>NOTE:</p>
    * <p>This bootstrapping bootstrapPort is currently only used by the CORBA plugin.</p>
    * <p>To set other protocols try e.g.:</p>
    * <pre>
    *  String[] args = { "-protocol", "SOCKET",
    *                    "-dispatch/connection/plugin/socket/hostname", "myHost",
    *                    "-dispatch/connection/plugin/socket/port", "7666",
    *                    "-dispatch/connection/plugin/socket/localHostname", "myHost",   // optional
    *                    "-dispatch/connection/plugin/socket/localPort", "8888" };       // optional
    *  glob.init(args);
    * </pre>
    * @param host An IP or DNS
    */
   public final void setBootstrapHostname(String host) {
      if (host == null) this.bootstrapHostname.setValue("");
      else this.bootstrapHostname.setValue(host);
   }

   public final void setDefaultBootstrapHostname(String host) {
      if (host == null) this.bootstrapHostname.setValue("", PropEntry.CREATED_BY_DEFAULT);
      else this.bootstrapHostname.setValue(host, PropEntry.CREATED_BY_DEFAULT);
   }

   /**
    * Check if a bootstrapHostname is set already
    */
   public boolean hasBootstrapHostname() {
      return (this.bootstrapHostname.getValue().length() > 0);
   }

   /**
    * @return The Hostname, IP or "" if not known
    */
   public final String getBootstrapHostname() {
      if (!hasBootstrapHostname()) {
         this.bootstrapHostname.setValue(glob.getLocalIP(), PropEntry.CREATED_BY_DEFAULT);
      }
      return this.bootstrapHostname.getValue();
   }

   /**
    * Returns a URL markup of the bootstrap server, currently it looks like
    * <i>xmlBlaster://myServer.com:3412</i> but will probably change in a future release.
    */
   public final String getBootstrapUrl() {
      return "xmlBlaster://" + getBootstrapHostname() + ":" + getBootstrapPort(); // + "/" + getType();
   }

   /**
    * Set the bootstrapping port. 
    * Updates the internal address as well. 
    * <p>NOTE:</p>
    * <p>This bootstrapping bootstrapPort is currently only used by the CORBA plugin.</p>
    * <p>To set other protocols try e.g.:</p>
    * <pre>
    *  String[] args = { "-protocol", "SOCKET",
    *                    "-dispatch/connection/plugin/socket/hostname", "myHost",
    *                    "-dispatch/connection/plugin/socket/port", "7666",
    *                    "-dispatch/connection/plugin/socket/localHostname", "myHost",   // optional
    *                    "-dispatch/connection/plugin/socket/localPort", "8888" };       // optional
    *  glob.init(args);
    * </pre>
    */
   public final void setBootstrapPort(int bootstrapPort) {
      this.bootstrapPort.setValue(bootstrapPort);
   }

   public final int getBootstrapPort() {
      return this.bootstrapPort.getValue();
   }

   /**
    * The creation default will be overwritten by the given defaultPort. 
    * <p>
    * If the bootstrapPort was changed by environment setting, this setting has precedence
    * over the given defaultPort and nothing happens.
    * </p>
    * <p>
    * This is used by the protocol plugins which all have different defaults
    * </p>
    */
   public final void setDefaultBootstrapPort(int defaultBootstrapPort) {
      if (this.bootstrapPort.isDefault()) {
         this.bootstrapPort.setValue(defaultBootstrapPort, PropEntry.CREATED_BY_DEFAULT);
      }
   }

   /**
    * Set the callback address, it should fit to the protocol-type. 
    *
    * <p>
    * If you set an address here you need to set it compatible to the
    * protocol from getType().<br />
    * For XmlRpc it looks typically like <i>http://myServer:8080</i>
    * for CORBA like <i>IOR:00005395....</i> and
    * for SOCKET like <i>socket://128.56.44.12:7608</i>
    * </p>
    * <p>
    * Setting the address here has precedence over any environment settings
    * like <i>-dispatch/connection/plugin/socket/port 7666</i> on command line
    * or
    * <pre>
    *  String[] args = { "-protocol", "SOCKET",
    *                    "-dispatch/connection/plugin/socket/hostname", "myHost",
    *                    "-dispatch/connection/plugin/socket/port", "7666",
    *                    "-dispatch/connection/plugin/socket/localHostname", "myHost",   // optional
    *                    "-dispatch/connection/plugin/socket/localPort", "8888" };       // optional
    *  glob.init(args);
    * </pre>
    * @param rawAddress The address specific for the protocol, e.g. "et@mars.univers" for EMAIL
    */
   public final void setRawAddress(String rawAddress) {
      if (rawAddress == null) rawAddress = "";
      this.rawAddress.setValue(rawAddress);
      if (log.TRACE) log.trace(ME, "setRawAddress=" + this.rawAddress.getValue());
   }

   /**
    * Returns the rawAddress which is specific for each protocol. 
    * @return e.g. "IOR:00001100022...." or "et@universe.com" or "" (never null)
    */
   public final String getRawAddress() {
      return this.rawAddress.getValue();
   }

   /**
    * Returns the protocol type.
    * @return e.g. "EMAIL" or "IOR" (never null).
    */
   public final String getType() {
      return type.getValue();
   }

   /**
    * Returns the protocol version.
    * @return e.g. "1.0" or null
    */
   public final String getVersion() {
      return version.getValue();
   }

   /**
    * What to do if max retries is exhausted. 
    * <p />
    * This mode is currently not configurable, we always destroy the login session. 
    * This is interpreted only server side if callback fails.
    * @return Constants.ONEXHAUST_KILL_SESSION="killSession"
    */
   public final String getOnExhaust() {
      return Constants.ONEXHAUST_KILL_SESSION; // in future possibly Constants.ONEXHAUST_KILL_CALLBACK
   }

   /**
    * Kill login session if max callback retries is exhausted?
    */
   public final boolean getOnExhaustKillSession() {
      return getOnExhaust().equalsIgnoreCase(Constants.ONEXHAUST_KILL_SESSION);
   }

   /**
    * BurstMode: The time span to collect messages before sending. 
    * @return The time to collect in milliseconds (default is 0 == switched off)
    */
   public final long getCollectTime() {
      return this.collectTime.getValue();
   }

   /**
    * BurstMode: The time to collect messages for sending in a bulk. 
    * @param The time to collect in milliseconds
    */
   public void setCollectTime(long collectTime) {
      if (collectTime < 0L) this.collectTime.setValue(0L);
      else this.collectTime.setValue(collectTime);
   }

   /**
    * How many messages maximum shall the callback thread take in one bulk out of the
    * callback queue and deliver to the client in one bulk. 
    */
   public int getBurstModeMaxEntries() {
      return this.burstModeMaxEntries.getValue();
   }

   public void setBurstModeMaxEntries(int burstModeMaxEntries) {
      if (burstModeMaxEntries == 0)
         log.warn(ME, "<burstMode maxEntries='" + burstModeMaxEntries + "'> is not supported and may cause strange behavior");
      else if (burstModeMaxEntries < -1)
         burstModeMaxEntries = -1;
      
      this.burstModeMaxEntries.setValue(burstModeMaxEntries);
   }

   /**
    * How many bytes maximum shall the callback thread take in one bulk out of the
    * callback queue and deliver to the client in one bulk. 
    */
   public long getBurstModeMaxBytes() {
      return this.burstModeMaxBytes.getValue();
   }

   public void setBurstModeMaxBytes(long burstModeMaxBytes) {
      if (burstModeMaxBytes == 0)
         log.warn(ME, "<burstMode maxBytes='" + burstModeMaxBytes + "'> is not supported and may cause strange behavior");
      else if (burstModeMaxBytes < -1L)
         burstModeMaxBytes = -1L;

      this.burstModeMaxBytes.setValue(burstModeMaxBytes);
   }

   /**
    * How long to wait between pings to the callback server. 
    * @return The pause time between pings in millis
    */
   public final long getPingInterval() {
      return pingInterval.getValue();
   }

   /**
    * How long to wait between pings to the callback server. 
    * @param pingInterval The pause time between pings in millis
    */
   public void setPingInterval(long pingInterval) {
      if (pingInterval <= 0L) this.pingInterval.setValue(0L);
      else if (pingInterval < 10L) {
         log.warn(ME, "pingInterval=" + pingInterval + " msec is too short, setting it to 10 millis");
         this.pingInterval.setValue(10L);
      }
      else
         this.pingInterval.setValue(pingInterval);
   }

   /**
    * How often shall we retry callback attempt on callback failure
    * @return -1 forever, 0 no retry, > 0 number of retries
    */
   public final int getRetries() {
      return retries.getValue();
   }

   /**
    * How often shall we retry callback attempt on callback failure
    * @param -1 forever, 0 no retry, > 0 number of retries
    */
   public void setRetries(int retries) {
      if (retries < -1) this.retries.setValue(-1);
      else this.retries.setValue(retries);
   }

   /**
    * Delay between callback retries in milliseconds, defaults to one minute
    * @return The delay in millisconds
    */
   public final long getDelay() {
      return delay.getValue();
   }

   /**
    * Delay between callback retries in milliseconds, defaults to one minute
    */
   public void setDelay(long delay) {
      if (delay <= 0L) this.delay.setValue(getDefaultDelay());
      else this.delay.setValue(delay);
   }

   /**
    * Shall the publish() or callback update() message be oneway. 
    * Is only with CORBA and our native SOCKET protocol supported
    * @return true if you want to force oneway sending
    */
   public final boolean oneway() {
      return oneway.getValue();
   }

   /**
    * Shall the publish() or callback update() message be oneway. 
    * Is only with CORBA and our native SOCKET protocol supported
    * @param oneway false is default
    */
   public void setOneway(boolean oneway) {
      this.oneway.setValue(oneway);
   }

   /**
    * Inhibits/activates the delivery of asynchronous dispatches of messages.
    * @param dispatcherActive
    */
   public void setDispatcherActive(boolean dispatcherActive) {
      this.dispatcherActive.setValue(dispatcherActive);
   }
   
   /**
    * @return true if the dispatcher is currently activated, i.e. if it is 
    * able to deliver asynchronous messages from the queue.
    */
   public boolean isDispatcherActive() {
      return this.dispatcherActive.getValue();
   }

   /**
    * @param Set if we accept point to point messages
    */
   public void setPtpAllowed(boolean ptpAllowed) {
      this.ptpAllowed.setValue(ptpAllowed);
   }

   /**
    * @return true if we may send PtP messages
    */
   public final boolean isPtpAllowed() {
      return this.ptpAllowed.getValue();
   }

   public void setCompressType(String compressType) {
      if (compressType == null) this.compressType.setValue("");
      this.compressType.setValue(compressType);
   }

   /**
    * The identifier sent to the callback client, the client can decide if he trusts this invocation
    * @return never null
    */
   public final String getSecretSessionId() {
      return sessionId.getValue();
   }

   /** The identifier sent to the callback client, the client can decide if he trusts this invocation */
   public void setSecretSessionId(String sessionId) {
      this.sessionId.setValue(sessionId);
   }

   /**
    * Get the compression method. 
    * @return "" No compression
    */
   public final String getCompressType() {
      return this.compressType.getValue();
   }

   /** 
    * Messages bigger this size in bytes are compressed. 
    * <br />
    * Note: This value is only used if compressType is set to a supported value
    * @return size in bytes
    */
   public long getMinSize() {
      return this.minSize.getValue();
   }

   /** 
    * Messages bigger this size in bytes are compressed. 
    * <br />
    * Note: This value is only evaluated if compressType is set to a supported value
    * @return size in bytes
    */
   public void setMinSize(long minSize) {
      this.minSize.setValue(minSize);
   }

   /**
    * Specify your dispatcher plugin configuration. 
    * <p>
    * Set to "undef" to switch off, or to e.g. "Priority,1.0" to access the PriorizedDispatchPlugin
    * </p>
    * <p>
    * This overwrites the xmlBlaster.properties default setting e.g.:
    * <pre>
    * DispatchPlugin[Priority][1.0]=org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDispatchPlugin
    * DispatchPlugin[SlowMotion][1.0]=org.xmlBlaster.util.dispatch.plugins.motion.SlowMotion
    * DispatchPlugin/defaultPlugin=Priority,1.0
    * </pre>
    * </p>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/dispatch.control.plugin.html">The dispatch.control.plugin requirement</a>
    */
   public void setDispatchPlugin(String dispatchPlugin) {
      this.dispatchPlugin.setValue(dispatchPlugin);
   }

   /**
    * @return "undef" or e.g. "Priority,1.0"
    */
   public String getDispatchPlugin() {
      return this.dispatchPlugin.getValue();
   }

   /**
    * Called for SAX callback start tag
    */
   public final void startElement(String uri, String localName, String name, StringBuffer character, Attributes attrs) {
      // log.info(ME, "startElement(rootTag=" + rootTag + "): name=" + name + " character='" + character.toString() + "'");

      String tmp = character.toString().trim(); // The address
      if (tmp.length() > 0) {
         setRawAddress(tmp);
      }
      character.setLength(0);

      if (name.equalsIgnoreCase(rootTag)) { // "callback"
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getQName(i).equalsIgnoreCase("type") ) {
                  setType(attrs.getValue(i).trim());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("version") ) {
                  setVersion(attrs.getValue(i).trim());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("bootstrapHostname") ) {
                  setBootstrapHostname(attrs.getValue(i).trim());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("hostname") ) { // deprecated -> use bootstrapHostname
                  setBootstrapHostname(attrs.getValue(i).trim());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("bootstrapPort") ) {
                  String ll = attrs.getValue(i).trim();
                  try {
                     setBootstrapPort(new Integer(ll).intValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <" + rootTag + " bootstrapPort='" + ll + "'>, expected an integer number.");
                  }
               }
               else if( attrs.getQName(i).equalsIgnoreCase("port") ) {  // deprecated -> use bootstrapPort
                  String ll = attrs.getValue(i).trim();
                  try {
                     setBootstrapPort(new Integer(ll).intValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <" + rootTag + " port='" + ll + "'>, expected an integer number.");
                  }
               }
               else if( attrs.getQName(i).equalsIgnoreCase("sessionId") ) {
                  setSecretSessionId(attrs.getValue(i).trim());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("pingInterval") ) {
                  String ll = attrs.getValue(i).trim();
                  try {
                     setPingInterval(new Long(ll).longValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <" + rootTag + " pingInterval='" + ll + "'>, expected a long in milliseconds.");
                  }
               }
               else if( attrs.getQName(i).equalsIgnoreCase("retries") ) {
                  String ll = attrs.getValue(i).trim();
                  try {
                     setRetries(new Integer(ll).intValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <" + rootTag + " retries='" + ll + "'>, expected an integer number.");
                  }
               }
               else if( attrs.getQName(i).equalsIgnoreCase("delay") ) {
                  String ll = attrs.getValue(i).trim();
                  try {
                     setDelay(new Long(ll).longValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <" + rootTag + " delay='" + ll + "'>, expected a long in milliseconds.");
                  }
               }
               else if( attrs.getQName(i).equalsIgnoreCase("oneway") ) {
                  setOneway(new Boolean(attrs.getValue(i).trim()).booleanValue());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("dispatcherActive") ) {
                  setDispatcherActive(new Boolean(attrs.getValue(i).trim()).booleanValue());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("useForSubjectQueue") ) {
                  this.useForSubjectQueue.setValue(new Boolean(attrs.getValue(i).trim()).booleanValue());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("dispatchPlugin") ) {
                  this.dispatchPlugin.setValue(attrs.getValue(i).trim());
               }
               else {
                  log.error(ME, "Ignoring unknown attribute " + attrs.getQName(i) + " in " + rootTag + " section.");
               }
            }
         }
         if (getType() == null) {
            log.error(ME, "Missing '" + rootTag + "' attribute 'type' in QoS");
            setType("IOR");
         }
         if (getSecretSessionId() == null) {
            log.warn(ME, "Missing '" + rootTag + "' attribute 'sessionId' QoS");
         }
         return;
      }

      if (name.equalsIgnoreCase("burstMode")) {
         if (attrs != null) {
            int len = attrs.getLength();
            int ii=0;
            for (ii = 0; ii < len; ii++) {
               if (attrs.getQName(ii).equalsIgnoreCase("collectTime")) {
                  String ll = attrs.getValue(ii).trim();
                  try {
                     setCollectTime(new Long(ll).longValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <burstMode collectTime='" + ll + "'>, expected a long in milliseconds, burst mode is switched off sync messages.");
                  }
               }
               else if( attrs.getQName(ii).equalsIgnoreCase("maxEntries") ) {
                  String ll = attrs.getValue(ii).trim();
                  try {
                     setBurstModeMaxEntries(new Integer(ll).intValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <burstMode maxEntries='" + ll + "'>, expected an integer number.");
                  }
               }
               else if( attrs.getQName(ii).equalsIgnoreCase("maxBytes") ) {
                  String ll = attrs.getValue(ii).trim();
                  try {
                     setBurstModeMaxBytes(new Long(ll).longValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <burstMode maxBytes='" + ll + "'>, expected a long in bytes.");
                  }
               }
            }
         }
         else {
            log.error(ME, "Missing 'collectTime' attribute in login-qos <burstMode>");
         }
         return;
      }

      if (name.equalsIgnoreCase("compress")) {
         if (attrs != null) {
            int len = attrs.getLength();
            for (int ii = 0; ii < len; ii++) {
               if (attrs.getQName(ii).equalsIgnoreCase("type")) {
                  setCompressType(attrs.getValue(ii).trim());
               }
               else if (attrs.getQName(ii).equalsIgnoreCase("minSize")) {
                  String ll = attrs.getValue(ii).trim();
                  try {
                     setMinSize(new Long(ll).longValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <compress minSize='" + ll + "'>, expected a long in bytes, compress is switched off.");
                  }
               }
            }
         }
         else {
            log.error(ME, "Missing 'type' attribute in qos <compress>");
         }
         return;
      }

      if (name.equalsIgnoreCase("ptp")) {
         setPtpAllowed(true);
         character.setLength(0);
         return;
      }
   }

   /**
    * Handle SAX parsed end element
    */
   public final void endElement(String uri, String localName, String name, StringBuffer character) {
      if (name.equalsIgnoreCase(rootTag)) { // "callback"
         String tmp = character.toString().trim(); // The address (if after inner tags)
         if (tmp.length() > 0)
            setRawAddress(tmp);
         else if (getRawAddress() == null)
            log.error(ME, rootTag + " QoS contains no rawAddress data");
      }
      else if (name.equalsIgnoreCase("burstMode")) {
      }
      else if (name.equalsIgnoreCase("compress")) {
      }
      else if (name.equalsIgnoreCase("ptp")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            setPtpAllowed(new Boolean(tmp).booleanValue());
         return;
      }

      character.setLength(0);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * Only none default values are dumped for performance reasons
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1200);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<").append(rootTag).append(" type='").append(getType()).append("'");
      // For debugging only:
      //sb.append(" nodeId='").append(this.nodeId).append("'");
      //sb.append(" context='").append(this.context).append("'");
      //sb.append(" className='").append(this.className).append("'");
      //sb.append(" instanceName='").append(this.instanceName).append("'");
      //sb.append(" envPrefix='").append(this.envPrefix).append("'");
      if (this.version.isModified())
          sb.append(" version='").append(getVersion()).append("'");
      if (this.bootstrapHostname.isModified())
          sb.append(" bootstrapHostname='").append(getBootstrapHostname()).append("'");
      if (this.bootstrapPort.isModified())
          sb.append(" bootstrapPort='").append(getBootstrapPort()).append("'");
      if (this.sessionId.isModified())
          sb.append(" sessionId='").append(getSecretSessionId()).append("'");
      if (this.pingInterval.isModified())
          sb.append(" pingInterval='").append(getPingInterval()).append("'");
      if (this.retries.isModified())
          sb.append(" retries='").append(getRetries()).append("'");
      if (this.delay.isModified())
          sb.append(" delay='").append(getDelay()).append("'");
      if (this.oneway.isModified())
          sb.append(" oneway='").append(oneway()).append("'");
      if (this.dispatcherActive.isModified())
          sb.append(" dispatcherActive='").append(isDispatcherActive()).append("'");
      if (this.useForSubjectQueue.isModified())
          sb.append(" useForSubjectQueue='").append(this.useForSubjectQueue.getValue()).append("'");
      if (this.dispatchPlugin.isModified())
          sb.append(" dispatchPlugin='").append(this.dispatchPlugin.getValue()).append("'");
      sb.append(">");
      sb.append(offset).append(" ").append(getRawAddress());
      if (this.collectTime.isModified() || this.burstModeMaxEntries.isModified() || this.burstModeMaxBytes.isModified()) {
         sb.append(offset).append(" ").append("<burstMode");
         if (this.collectTime.isModified())
            sb.append(" collectTime='").append(getCollectTime()).append("'");
         if (this.burstModeMaxEntries.isModified())
            sb.append(" maxEntries='").append(getBurstModeMaxEntries()).append("'");
         if (this.burstModeMaxBytes.isModified())
            sb.append(" maxBytes='").append(getBurstModeMaxBytes()).append("'");
         sb.append("/>");
      }
      if (this.compressType.isModified())
         sb.append(offset).append(" ").append("<compress type='").append(getCompressType()).append("' minSize='").append(getMinSize()).append("'/>");
      if (this.ptpAllowed.isModified()) {
         if (this.ptpAllowed.getValue()) {
            sb.append(offset).append(" ").append("<ptp/>");
         }
         else {
            sb.append(offset).append(" ").append("<ptp>").append(this.ptpAllowed).append("</ptp>");
         }
      }
      sb.append(offset).append("</").append(rootTag).append(">");

      return sb.toString();
   }

   /**
    * Get a usage string for the connection parameters. 
    * Currently only for client side usage
    */
   public String usage()
   {
      String text = "";
    //text += "   -oneway             Shall the publish() messages be send oneway (no application level ACK) [" + Address.DEFAULT_oneway + "]\n";
      text += "   -dispatch/" + this.instanceName + "/protocol\n";
      text += "                       Protocol to use [" + DEFAULT_type + "]\n";
     // text += "   -dispatch/" + this.instanceName + "/plugin/" + this.type + "/port\n";
     // text += "                       Port to use for the protocol [" + DEFAULT_port + "]\n";
      text += "   -dispatch/" + this.instanceName + "/pingInterval\n";
      text += "                       Pinging every given milliseconds [" + getDefaultPingInterval() + "]\n";
      text += "   -dispatch/" + this.instanceName + "/retries\n";
      text += "                       How often to retry if connection fails (-1 is forever) [" + getDefaultRetries() + "]\n";
      text += "   -dispatch/" + this.instanceName + "/delay\n";
      text += "                       Delay between connection retries in milliseconds [" + getDefaultDelay() + "]\n";
      text += "                       A delay value > 0 switches fails save mode on, 0 switches it off\n";
      // other settings like burstMode are in the derived classes
      return text;
   }
}


