/*------------------------------------------------------------------------------
Name:      AddressBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding connect address and callback address string including protocol
Version:   $Id: AddressBase.java,v 1.17 2002/12/20 15:29:20 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.Constants;
import org.xml.sax.Attributes;


/**
 * Abstract helper class holding connect address and callback address string
 * and protocol string.
 * <p />
 * See examples in the implementing classes
 * @see Address
 * @see CallbackAddress
 */
public abstract class AddressBase
{
   private static final String ME = "AddressBase";
   protected final Global glob;
   protected final LogChannel log;

   /** The root xml element: &lt;callback> or &lt;address>, is set from the derived class */
   protected String rootTag = null;

   /** The unique address, e.g. the CORBA IOR string */
   private String address;

   private String hostname;
   protected boolean isHardcodedHostname = false; // set to true if setHostname() was explicitly called by user

   public static final int DEFAULT_port = 3412;
   private int port = DEFAULT_port;

   /** The unique protocol type, e.g. "IOR" */
   public static final String DEFAULT_type = "IOR";
   protected String type = DEFAULT_type;
   
   /** The protocol version, e.g. "1.0" */
   public static final String DEFAULT_version = "1.0";
   protected String version = DEFAULT_version;
   
   /** BurstMode: The time to collect messages for publish/update */
   public static final long DEFAULT_collectTime = 0L;
   protected long collectTime = DEFAULT_collectTime;
   
   /** BurstMode: The time to collect messages for oneway publish/update */
   public static final long DEFAULT_collectTimeOneway = 0L;
   protected long collectTimeOneway = DEFAULT_collectTimeOneway;

   /** Ping interval: pinging every given milliseconds */
   abstract public long getDefaultPingInterval();
   protected long pingInterval = getDefaultPingInterval();
   
   /** How often to retry if connection fails */
   abstract public int getDefaultRetries();
   protected int retries = getDefaultRetries();
   
   /** Delay between connection retries in milliseconds */
   abstract public long getDefaultDelay();
   protected long delay = getDefaultDelay();
   
   /**
    * Shall the update() or publish() messages be send oneway (no application level ACK). 
    * <p />
    * For more info read the CORBA spec. Only CORBA and our native SOCKET protocol support oneway.
    * Defaults to false (the update() or publish() has a return value and can throw an exception).
    */
   public static final boolean DEFAULT_oneway = false;
   protected boolean oneway = DEFAULT_oneway;
   
   /** Compress messages if set to "gzip" or "zip" */
   public static final String DEFAULT_compressType = "";
   protected String compressType = DEFAULT_compressType;
   
   /** Messages bigger this size in bytes are compressed */
   public static final long DEFAULT_minSize = 0L;
   protected long minSize = DEFAULT_minSize;
   
   /** PtP messages wanted? Defaults to true, false prevents spamming */
   public static final boolean DEFAULT_ptpAllowed = true;
   protected boolean ptpAllowed = DEFAULT_ptpAllowed;
   
   /** The identifier sent to the callback client, the client can decide if he trusts this invocation */
   public static final String DEFAULT_sessionId = "unknown";
   protected String sessionId = DEFAULT_sessionId;

   /** Shall this session callback be used for subjectQueue messages as well? For &lt;callback> only */
   public static final boolean DEFAULT_useForSubjectQueue = true;
   protected boolean useForSubjectQueue = DEFAULT_useForSubjectQueue;

   /**
    * Does client whish a dispatcher plugin. 
    * <p>
    * Set to "undef" forces to switch off, or e.g. "Priority,1.0" to access the PriorizedDeliveryPlugin
    * </p>
    * <p>
    * Setting it to 'null' (which is the default) lets the server choose the plugin
    * </p>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/delivery.control.plugin.html">The delivery.control.plugin requirement</a>
    */
   public String DEFAULT_dispatchPlugin = null;
   protected String dispatchPlugin = DEFAULT_dispatchPlugin;

   /**
    */
   public AddressBase(Global glob, String rootTag) {
      this.glob = glob;
      this.log = glob.getLog("core");
      setRootTag(rootTag);
   }

   /**
    * A nice human readable name for this address (used for logging)
    */
   public final String getName() {
      return getHostname()+":"+getPort();
   }

   /**
    * Check if supplied address would connect to the address of this instance
    */
   public final boolean isSameAddress(AddressBase other) {
      String oa = other.getAddress();
      if (oa != null && oa.length() > 1 && oa.equals(getAddress()))
         return true;
      String oh = other.getHostname();
      int op = other.getPort();
      if (op > 0 && op == getPort() && oh != null && oh.equals(getHostname()))
         return true;
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
      buf.append("type=").append(type).append(" oneway=").append(oneway).append(" burstMode.collectTime=").append(getCollectTime());
      return buf.toString();
   }

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    */
   public final void setType(String type) {
      if (type == null)
         this.type = "";
      else
         this.type = type;
   }

   /**
    * @param version   The protocol version, e.g. "1.0"
    */
   public final void setVersion(String version) {
      this.version = version;
   }

   /**
    * Updates the internal address as well. 
    * @param host An IP or DNS
    */
   public final void setHostname(String host) {
      initHostname(host);
      isHardcodedHostname = true;
   }

   protected final void initHostname(String hostname) {
      this.hostname = hostname;
      this.address = null; // reset cache
   }

   /**
    * @return true if the hostname is explicitly set by user with setHostname()
    * false if it is determined automatically
    */
   public boolean isHardcodedHostname() {
      return isHardcodedHostname;
   }

   /**
    * Check if a hostname is set already
    */
   public boolean hasHostname() {
      return this.hostname != null;
   }

   /**
    * @return The Hostname, IP or "" if not known
    */
   public final String getHostname() {
      if (this.hostname == null) {
         this.hostname = glob.getBootstrapHostname();
         this.address = null; // reset cache
      }
      return this.hostname;
   }

   /**
    * Set the bootstrapping port. 
    * Updates the internal address as well. 
    */
   public final void setPort(int port) {
      this.port = port;
      this.address = null; // reset cache
   }

   public final int getPort() {
      /*
      if (this.port == 0) {
         this.port = glob.getBootstrapPort();
         this.address = null; // reset cache
      }
      */
      return this.port;
   }

   /**
    * Set the callback address, it should fit to the protocol-type.
    *
    * @param address The callback address, e.g. "et@mars.univers"
    */
   public final void setAddress(String address) {
      if (address == null) { Thread.currentThread().dumpStack(); throw new IllegalArgumentException("AddressBase.setAddress(null) null argument is not allowed"); }
      this.address = address;
   }

   /**
    * Returns the address.
    * @return e.g. "IOR:00001100022...." or "et@universe.com" or ""
    */
   public final String getAddress() {
      if (this.address == null) {
         this.address = "http://" + getHostname();
         if (getPort() > 0)
            this.address += ":" + getPort();
      }
      return address;
   }

   /**
    * Returns the protocol type.
    * @return e.g. "EMAIL" or "IOR" (never null).
    */
   public final String getType() {
      return type;
   }

   /**
    * Returns the protocol version.
    * @return e.g. "1.0" or null
    */
   public final String getVersion() {
      return version;
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
    * @return The time to collect in milliseconds
    */
   public final long getCollectTime() {
      return collectTime;
   }

   /**
    * BurstMode: The time span to collect oneway messages before sending. 
    * @return The time to collect in milliseconds
    */
   public final long getCollectTimeOneway() {
      return collectTimeOneway;
   }

   /**
    * BurstMode: The time to collect messages for sending in a bulk. 
    * @param The time to collect in milliseconds
    */
   public void setCollectTime(long collectTime) {
      if (collectTime < 0L)
         this.collectTime = 0L;
      else
         this.collectTime = collectTime;
   }

   /**
    * BurstMode: The time to collect oneway messages for sending in a bulk. 
    * @param The time to collect in milliseconds
    */
   public void setCollectTimeOneway(long collectTimeOneway) {
      if (collectTimeOneway < 0L)
         this.collectTimeOneway = 0L;
      else
         this.collectTimeOneway = collectTimeOneway;
   }

   /**
    * How long to wait between pings to the callback server. 
    * @return The pause time between pings in millis
    */
   public final long getPingInterval() {
      return pingInterval;
   }

   /**
    * How long to wait between pings to the callback server. 
    * @param pingInterval The pause time between pings in millis
    */
   public void setPingInterval(long pingInterval) {
      if (pingInterval <= 0L)
         this.pingInterval = 0L;
      else if (pingInterval < 10L) {
         log.warn(ME, "pingInterval=" + pingInterval + " msec is too short, setting it to 10 millis");
         this.pingInterval = 10L;
      }
      else
         this.pingInterval = pingInterval;
   }

   /**
    * How often shall we retry callback attempt on callback failure
    * @return -1 forever, 0 no retry, > 0 number of retries
    */
   public final int getRetries() {
      return retries;
   }

   /**
    * How often shall we retry callback attempt on callback failure
    * @param -1 forever, 0 no retry, > 0 number of retries
    */
   public void setRetries(int retries) {
      if (retries < -1)
         this.retries = -1;
      else
         this.retries = retries;
   }

   /**
    * Delay between callback retries in milliseconds, defaults to one minute
    * @return The delay in millisconds
    */
   public final long getDelay() {
      return delay;
   }

   /**
    * Delay between callback retries in milliseconds, defaults to one minute
    */
   public void setDelay(long delay) {
      if (delay < 0L)
         this.delay = 0L;
      else
         this.delay = delay;
   }

   /**
    * Shall the publish() or callback update() message be oneway. 
    * Is only with CORBA and our native SOCKET protocol supported
    * @return true if you want to force oneway sending
    */
   public final boolean oneway() {
      return oneway;
   }

   /**
    * Shall the publish() or callback update() message be oneway. 
    * Is only with CORBA and our native SOCKET protocol supported
    * @param oneway false is default
    */
   public void setOneway(boolean oneway) {
      this.oneway = oneway;
   }

   /**
    * @param Set if we accept point to point messages
    */
   public void setPtpAllowed(boolean ptpAllowed) {
      this.ptpAllowed = ptpAllowed;
   }

   /**
    * @return true if we may send PtP messages
    */
   public final boolean isPtpAllowed() {
      return this.ptpAllowed;
   }

   public void setCompressType(String compressType) {
      if (compressType == null) compressType = "";
      this.compressType = compressType;

      // TODO !!!
      if (compressType.length() > 0)
         log.warn(ME, "Compression of messages is not yet supported");
   }

   /**
    * The identifier sent to the callback client, the client can decide if he trusts this invocation
    * @return never null
    */
   public final String getSessionId() {
      return sessionId;
   }

   /** The identifier sent to the callback client, the client can decide if he trusts this invocation */
   public void setSessionId(String sessionId) {
      this.sessionId = sessionId;
   }

   /**
    * Get the compression method. 
    * @return "" No compression
    */
   public final String getCompressType() {
      return compressType;
   }

   /** 
    * Messages bigger this size in bytes are compressed. 
    * <br />
    * Note: This value is only used if compressType is set to a supported value
    * @return size in bytes
    */
   public long getMinSize() {
      return minSize;
   }

   /** 
    * Messages bigger this size in bytes are compressed. 
    * <br />
    * Note: This value is only evaluated if compressType is set to a supported value
    * @return size in bytes
    */
   public void setMinSize(long minSize) {
      this.minSize = minSize;
   }

   /**
    * Specify your dispatcher plugin configuration. 
    * <p>
    * Set to "undef" to switch off, or to e.g. "Priority,1.0" to access the PriorizedDeliveryPlugin
    * </p>
    * <p>
    * This overwrites the xmlBlaster.properties default setting e.g.:
    * <pre>
    * DispatchPlugin[Priority][1.0]=org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDeliveryPlugin
    * DispatchPlugin[SlowMotion][1.0]=org.xmlBlaster.util.dispatch.plugins.motion.SlowMotion
    * DispatchPlugin.defaultPlugin=Priority,1.0
    * </pre>
    * </p>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/delivery.control.plugin.html">The delivery.control.plugin requirement</a>
    */
   public void setDispatchPlugin(String dispatchPlugin) {
      this.dispatchPlugin = dispatchPlugin;
   }

   /**
    * @return "undef" or e.g. "Priority,1.0"
    */
   public String getDispatchPlugin() {
      return this.dispatchPlugin;
   }

   /**
    * Called for SAX callback start tag
    */
   public final void startElement(String uri, String localName, String name, StringBuffer character, Attributes attrs) {
      // log.info(ME, "startElement(rootTag=" + rootTag + "): name=" + name + " character='" + character.toString() + "'");

      String tmp = character.toString().trim(); // The address
      if (tmp.length() > 0) {
         setAddress(tmp);
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
               else if( attrs.getQName(i).equalsIgnoreCase("hostname") ) {
                  setHostname(attrs.getValue(i).trim());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("port") ) {
                  String ll = attrs.getValue(i).trim();
                  try {
                     setPort(new Integer(ll).intValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <" + rootTag + " port='" + ll + "'>, expected an integer number.");
                  }
               }
               else if( attrs.getQName(i).equalsIgnoreCase("sessionId") ) {
                  setSessionId(attrs.getValue(i).trim());
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
               else if( attrs.getQName(i).equalsIgnoreCase("useForSubjectQueue") ) {
                  this.useForSubjectQueue = new Boolean(attrs.getValue(i).trim()).booleanValue();
               }
               else if( attrs.getQName(i).equalsIgnoreCase("dispatchPlugin") ) {
                  this.dispatchPlugin = attrs.getValue(i).trim();
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
         if (getSessionId() == null) {
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
               else if (attrs.getQName(ii).equalsIgnoreCase("collectTimeOneway")) {
                  String ll = attrs.getValue(ii).trim();
                  try {
                     setCollectTimeOneway(new Long(ll).longValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <burstMode collectTimeOneway='" + ll + "'>, expected a long in milliseconds, burst mode is switched off for oneway messages.");
                  }
               }
            }
         }
         else {
            log.error(ME, "Missing 'collectTime' or 'collectTimeOneway' attribute in login-qos <burstMode>");
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
            setAddress(tmp);
         else if (getAddress() == null)
            log.error(ME, rootTag + " QoS contains no address data");
      }
      else if (name.equalsIgnoreCase("burstMode")) {
      }
      else if (name.equalsIgnoreCase("compress")) {
      }
      else if (name.equalsIgnoreCase("ptp")) {
         this.ptpAllowed = new Boolean(character.toString().trim()).booleanValue();
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
      StringBuffer sb = new StringBuffer(300);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<").append(rootTag).append(" type='").append(getType()).append("'");
      if (getVersion() != null && !getVersion().equals(DEFAULT_version))
          sb.append(" version='").append(getVersion()).append("'");
      if (getHostname() != null)
          sb.append(" hostname='").append(getHostname()).append("'");
      if (DEFAULT_port != getPort())
          sb.append(" port='").append(getPort()).append("'");
      if (!DEFAULT_sessionId.equals(getSessionId()))
          sb.append(" sessionId='").append(getSessionId()).append("'");
      if (getDefaultPingInterval() != getPingInterval())
          sb.append(" pingInterval='").append(getPingInterval()).append("'");
      if (getDefaultRetries() != getRetries())
          sb.append(" retries='").append(getRetries()).append("'");
      if (getDefaultDelay() != getDelay())
          sb.append(" delay='").append(getDelay()).append("'");
      if (DEFAULT_oneway != oneway())
          sb.append(" oneway='").append(oneway()).append("'");
      if (DEFAULT_useForSubjectQueue != this.useForSubjectQueue)
          sb.append(" useForSubjectQueue='").append(this.useForSubjectQueue).append("'");
      if (DEFAULT_dispatchPlugin != this.dispatchPlugin)
          sb.append(" dispatchPlugin='").append(this.dispatchPlugin).append("'");
      sb.append(">");
      if (getAddress() != null)
         sb.append(offset).append("   ").append(getAddress());
      if (getCollectTime() != DEFAULT_collectTime || getCollectTimeOneway() != DEFAULT_collectTimeOneway) {
         sb.append(offset).append("   ").append("<burstMode");
         if (getCollectTime() != DEFAULT_collectTime)
            sb.append(" collectTime='").append(getCollectTime()).append("'");
         if (getCollectTimeOneway() != DEFAULT_collectTimeOneway)
            sb.append(" collectTimeOneway='").append(getCollectTimeOneway()).append("'");
         sb.append("/>");
      }
      if (!getCompressType().equals(DEFAULT_compressType))
         sb.append(offset).append("   ").append("<compress type='").append(getCompressType()).append("' minSize='").append(getMinSize()).append("'/>");
      if (ptpAllowed != DEFAULT_ptpAllowed)
         sb.append(offset).append("   ").append("<ptp>").append(this.ptpAllowed).append("</ptp>");
      sb.append(offset).append("</").append(rootTag).append(">");

      return sb.toString();
   }
}


