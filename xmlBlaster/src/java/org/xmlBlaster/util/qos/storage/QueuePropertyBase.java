/*------------------------------------------------------------------------------
Name:      QueueProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.storage;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xml.sax.Attributes;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.property.PropString;
import org.xmlBlaster.util.property.PropLong;
import org.xmlBlaster.util.property.PropEntry;
import org.xmlBlaster.util.qos.address.AddressBase;


/**
 * Helper class holding callback queue properties.
 * <p />
 * See ConnectQos for XML syntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */
public abstract class QueuePropertyBase implements Cloneable
{
   private static final String ME = "QueuePropertyBase";
   protected final Global glob;
   protected final LogChannel log;

   private String propertyPrefix = "";

   /** The queue plugin type "CACHE" "RAM" "JDBC" or others */
   public static final String DEFAULT_type = "CACHE";
   protected PropString type = new PropString(DEFAULT_type);

   /** The queue plugin version "1.0" or similar */
   public static final String DEFAULT_version = "1.0";
   protected PropString version = new PropString(DEFAULT_version);

   ///** The max setting allowed for queue maxMsg is adjustable with property "queue.maxMsg=1000" (1000 messages is default) */
   //public static final long DEFAULT_maxMsgDefault = 1000L;
   //protected long maxMsgDefault = DEFAULT_maxMsgDefault;

   /** The max setting allowed for queue maxMsgCache is adjustable with property "queue.maxMsgCache=1000" (1000 messages is default) */
   public static final long DEFAULT_maxMsgCacheDefault = 1000L;
   protected long maxMsgCacheDefault = DEFAULT_maxMsgCacheDefault;

   /** The max setting allowed for queue max size in bytes is adjustable with property "queue.maxBytes=4194304" (10 MBytes is default) */
   public static final long DEFAULT_bytesDefault = 10485760L; // 10 MB
   protected long maxBytesDefault = DEFAULT_bytesDefault;

   /** The max setting allowed for queue max size of cache in bytes is adjustable with property "queue.maxBytesCache=4000000" (4 MBytes is default) */
   public static final long DEFAULT_bytesCacheDefault = 2097152L; // 2 MB
   protected long c = DEFAULT_bytesCacheDefault;

   /** The default settings (as a ratio relative to the maxBytesCache) for the storeSwapLevel */
   public static final double DEFAULT_storeSwapLevelRatio = 0.70;

   /** The default settings (as a ratio relative to the maxBytesCache) for the storeSwapBytes */
   public static final double DEFAULT_storeSwapBytesRatio = 0.25;

   /** The default settings (as a ratio relative to the maxBytesCache) for the storeSwapLevel */
   public static final double DEFAULT_reloadSwapLevelRatio = 0.30;

   /** The default settings (as a ratio relative to the maxBytesCache) for the storeSwapBytes */
   public static final double DEFAULT_reloadSwapBytesRatio = 0.25;

   /** The unique queue or storage name, e.g. "history" */
   protected String relating = Constants.RELATING_CALLBACK;

   /** The max setting allowed for queue maxMsg is adjustable with property "queue.maxMsg=1000" (1000 messages is default) */
   public long DEFAULT_maxMsg = 1000L;
   /** The max. capacity of the queue in number of entries */
   protected PropLong maxMsg = new PropLong(DEFAULT_maxMsg);
   /** The max. capacity of the queue in Bytes */
   protected PropLong maxBytes = new PropLong(maxBytesDefault);
   /** The max. capacity of the cache of the queue in number of entries */
   protected PropLong maxMsgCache = new PropLong(maxMsgCacheDefault);
   /** The max. capacity of the queue in Bytes for the cache */
   protected PropLong maxBytesCache = new PropLong(c);

   /** The settings for the storeSwapLevel */
   protected long storeSwapLevel = (long)(DEFAULT_storeSwapLevelRatio*this.maxBytesCache.getValue());

   /** The settings for the storeSwapBytes */
   protected long storeSwapBytes = (long)(DEFAULT_storeSwapBytesRatio*this.maxBytesCache.getValue());

   /** The settings for the reloadSwapLevel */
   protected long reloadSwapLevel = (long)(DEFAULT_reloadSwapLevelRatio*this.maxBytesCache.getValue());

   /** The settings for the storeSwapBytes */
   protected long reloadSwapBytes = (long)(DEFAULT_reloadSwapBytesRatio*this.maxBytesCache.getValue());


   /** Error handling when queue is full: Constants.ONOVERFLOW_DEADMESSAGE | Constants.ONOVERFLOW_DISCARDOLDEST */
   public static final String DEFAULT_onOverflow = Constants.ONOVERFLOW_DEADMESSAGE;
   protected PropString onOverflow = new PropString(DEFAULT_onOverflow);

   /** Error handling when callback failed (after all retries etc.): Constants.ONOVERFLOW_DEADMESSAGE */
   public static final String DEFAULT_onFailure = Constants.ONOVERFLOW_DEADMESSAGE;
   protected PropString onFailure = new PropString(DEFAULT_onFailure);

   /** The corresponding callback address, is set by derived classes */
   protected AddressBase[] addressArr = new AddressBase[0];

   /** To allow specific configuration parameters for specific cluster nodes */
   protected String nodeId = null;

   /**
    * @param glob The global handle containing env informations
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue.maxMsg and -queue.maxMsg[heron] will be searched<br />
    * The nodeId should be stripped from special characters (see glob.getStrippedId())
    * e.g. '/' or '[' is not allowed in the nodeId
    * @see Global#getStrippedId()
    */
   public QueuePropertyBase(Global glob, String nodeId) {
      if (glob == null) {
         Thread.currentThread().dumpStack();
         this.glob = new Global();
      }
      else
         this.glob = glob;
      this.log = glob.getLog("core");
      this.nodeId = (nodeId == null) ? glob.getStrippedId() : nodeId;
   }

   /**
    * Show some important settings for logging
    */
    /*
   public String getSettings()
   {
      StringBuffer buf = new StringBuffer(256);
      buf.append("onOverflow=").append(getOnOverflow()).append(" onFailure=").append(getOnFailure()).append(" maxMsg=").append(getMaxMsg());
      if (getCurrentCallbackAddress() != null)
         buf.append(" ").append(getCurrentCallbackAddress().getSettings());
      return buf.toString();
   }  */

   /**
    * @return The prefix (relating='') for properties e.g. "history" -> "-history.queue.maxMsg"
    */
   public String getPropertyPrefix() {
      return this.propertyPrefix;
   }

   /**
    * The command line prefix to configure the queue or msgUnitStore
    * @return e.g. "persistence/msgUnitStore/" or "queue/history/"
    */
   public String getPrefix() {
      //@return e.g. "msgUnitStore." or "history.queue."
      //return (this.relating!=null&&this.relating.length() > 0) ? this.relating+"."+getRootTagName()+"." : getRootTagName()+".";
      return getRootTagName() + PropEntry.SEP + ((this.relating!=null&&this.relating.length() > 0) ? (this.relating+PropEntry.SEP) : "");
   }

   /**
    * Helper for logging output, creates the property key for configuration (the command line property). 
    * @param prop e.g. "maxMsg"
    * @return e.g. "-history.queue.maxMsg" or "-history.queue.maxMsgCache"
    */
   public String getPropName(String token) {
      return "-" + getPrefix() + token;
   }

   /**
    * Configure property settings, add your own defaults in the derived class
    * @param relating e.g. "history" or "callback", similar to <queue related='history'/> or
    *                            <persistence related='msgUnitStore'/> etc.
    */
   protected void initialize(String relating) {
      this.relating = (relating == null) ? "" : relating;
      String prefix = getPrefix();
      String context = null; // something like "/topic/HelloWorld"

      // extract the plugin type and version from 'defaultPlugin'
      String propName = null;
      try {
         PropString defaultPlugin = new PropString(this.type.getDefaultValue()+","+this.version.getDefaultValue());
         // Port to linked ContextNode?
         propName = defaultPlugin.setFromEnv(this.glob, nodeId, context, getRootTagName(), relating, "defaultPlugin");
         if (log.TRACE) log.trace(ME, "Lookup of propName=" + propName + " defaultValue=" + defaultPlugin.getValue());
         PluginInfo pluginInfo = new PluginInfo(glob, null, defaultPlugin.getValue());
         this.type.setDefaultValue(pluginInfo.getType());
         this.version.setDefaultValue(pluginInfo.getVersion());
      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "initialize: could not set the default plugin to what indicated by "+propName);
      }
                                
      // The newer way:
      this.maxMsg.setFromEnv(this.glob, nodeId, context, getRootTagName(), relating, "maxMsg");
      this.maxMsgCache.setFromEnv(this.glob, nodeId, context, getRootTagName(), relating, "maxMsgCache");
      this.maxBytes.setFromEnv(this.glob, nodeId, context, getRootTagName(), relating, "maxBytes");
      this.maxBytesCache.setFromEnv(this.glob, nodeId, context, getRootTagName(), relating, "maxBytesCache");
      this.type.setFromEnv(this.glob, nodeId, context, getRootTagName(), relating, "type");
      this.version.setFromEnv(this.glob, nodeId, context, getRootTagName(), relating, "version");
      this.onOverflow.setFromEnv(this.glob, nodeId, context, getRootTagName(), relating, "onOverflow");
      this.onFailure.setFromEnv(this.glob, nodeId, context, getRootTagName(), relating, "onFailure");

      // The old way:
      // prefix is e.g. "cb.queue." or "msgUnitStore."
      setStoreSwapLevel(glob.getProperty().get(prefix+"storeSwapLevel", (long)(DEFAULT_storeSwapLevelRatio*this.maxBytesCache.getValue())));
      setStoreSwapBytes(glob.getProperty().get(prefix+"storeSwapBytes", (long)(DEFAULT_storeSwapBytesRatio*this.maxBytesCache.getValue())));
      setReloadSwapLevel(glob.getProperty().get(prefix+"reloadSwapLevel", (long)(DEFAULT_reloadSwapLevelRatio*this.maxBytesCache.getValue())));
      setReloadSwapBytes(glob.getProperty().get(prefix+"reloadSwapBytes", (long)(DEFAULT_reloadSwapBytesRatio*this.maxBytesCache.getValue())));
      if (nodeId != null) {
         setStoreSwapLevel(glob.getProperty().get(prefix+"storeSwapLevel["+nodeId+"]", getStoreSwapLevel()));
         setStoreSwapBytes(glob.getProperty().get(prefix+"storeSwapBytes["+nodeId+"]", getStoreSwapBytes()));
         setReloadSwapLevel(glob.getProperty().get(prefix+"reloadSwapLevel["+nodeId+"]", getReloadSwapLevel()));
         setReloadSwapBytes(glob.getProperty().get(prefix+"reloadSwapBytes["+nodeId+"]", getReloadSwapBytes()));
      }
      
      checkConsistency();
   }

   /**
    * @param relating    To what is this queue related: Constants.RELATING_CALLBACK | Constants.RELATING_SUBJECT | Constants.RELATING_CLIENT
    */
   public void setRelating(String relating) {
      if (Constants.RELATING_CALLBACK.equalsIgnoreCase(relating))
         this.relating = Constants.RELATING_CALLBACK;
      else if (Constants.RELATING_SUBJECT.equalsIgnoreCase(relating))
         this.relating = Constants.RELATING_SUBJECT;
      else if (Constants.RELATING_CLIENT.equalsIgnoreCase(relating))
         this.relating = Constants.RELATING_CLIENT;
      else if (Constants.RELATING_HISTORY.equalsIgnoreCase(relating))
         this.relating = Constants.RELATING_HISTORY;
      else if (Constants.RELATING_MSGUNITSTORE.equalsIgnoreCase(relating))
         this.relating = Constants.RELATING_MSGUNITSTORE;
      else if (Constants.RELATING_TOPICSTORE.equalsIgnoreCase(relating))
         this.relating = Constants.RELATING_TOPICSTORE;
      else {
         log.warn(ME, "Ignoring relating='" + relating + "'");
         Thread.currentThread().dumpStack();
      }
   }

   /**
    * Returns the queue id.
    * @return relating    To what is this queue related: Constants.RELATING_CALLBACK | Constants.RELATING_SUBJECT
    */
   public final String getRelating() {
      return this.relating;
   }

   /**
    * Max number of messages for this queue.
    * <br />
    * @return number of messages
    */
   public final long getMaxMsg() {
      return this.maxMsg.getValue();
   }

   /**
    * Max number of messages for this queue.
    * <br />
    * @param maxMsg
    */
   public final void setMaxMsg(long maxMsg) {
      setMaxMsgUnchecked(maxMsg);
      checkConsistency();
   }
   private final void setMaxMsgUnchecked(long maxMsg) {
      this.maxMsg.setValue(maxMsg);
   }

   /**
    * The plugin type. 
    * <br />
    * @return e.g. "CACHE" or null to choose current configured default plugin
    */
   public final String getType() {
      return this.type.getValue();
   }

   /**
    * The plugin type
    * <br />
    * @param type
    */
   public final void setType(String type) {
      this.type.setValue(type);
   }

   /**
    * The plugin version. 
    * <br />
    * @return e.g. "1.0" or null to configure current default plugin
    */
   public final String getVersion() {
      return this.version.getValue();
   }

   /**
    * The plugin version
    * <br />
    * @param version
    */
   public final void setVersion(String version) {
      this.version.setValue(version);
   }

   /**
    * Max number of messages for the cache of this queue.
    * <br />
    * @return number of messages
    */
   public final long getMaxMsgCache() {
      return this.maxMsgCache.getValue();
   }

   /**
    * Max number of messages for the cache of this queue.
    * <br />
    * @param maxMsgCache
    */
   public final void setMaxMsgCache(long maxMsgCache) {
      this.maxMsgCache.setValue(maxMsgCache);
      checkConsistency();
   }
   private final void setMaxMsgCacheUnchecked(long maxMsgCache) {
      this.maxMsgCache.setValue(maxMsgCache);
   }

   /**
    * Max message queue size.
    * <br />
    * @return Get max. message queue size in Bytes
    */
   public final long getMaxBytes() {
      return this.maxBytes.getValue();
   }

   /**
    * Max message queue size.
    * <br />
    * @return Set max. message queue size in Bytes
    */
   public final void setMaxBytes(long maxBytes) {
      this.maxBytes.setValue(maxBytes);
      checkConsistency();
   }
   private final void setMaxBytesUnchecked(long maxBytes) {
      this.maxBytes.setValue(maxBytes);
   }


   /**
    * Max message queue size for the cache of this queue.
    * <br />
    * @return Get max. message queue size in Bytes
    */
   public final long getMaxBytesCache() {
      return this.maxBytesCache.getValue();
   }


   /**
    * Gets the storeSwapLevel for the queue (only used on cache queues).
    * <br />
    * @return Get storeSwapLevel in bytes.
    */
   public final long getStoreSwapLevel() {
      return this.storeSwapLevel;
   }

   /**
    * Sets the storeSwapLevel for the queue (only used on cache queues).
    * <br />
    * @param Set storeSwapLevel in bytes.
    */
   public final void setStoreSwapLevel(long storeSwapLevel) {
      this.storeSwapLevel = storeSwapLevel;
   }

   /**
    * Gets the storeSwapBytes for the queue (only used on cache queues).
    * <br />
    * @return Get storeSwapBytes in bytes.
    */
   public final long getStoreSwapBytes() {
      return this.storeSwapBytes;
   }

   /**
    * Sets the storeSwapBytes for the queue (only used on cache queues).
    * <br />
    * @param Set storeSwapBytes in bytes.
    */
   public final void setStoreSwapBytes(long storeSwapBytes) {
      this.storeSwapBytes = storeSwapBytes;
   }

   /**
    * Gets the reloadSwapLevel for the queue (only used on cache queues).
    * <br />
    * @return Get reloadSwapLevel in bytes.
    */
   public final long getReloadSwapLevel() {
      return this.reloadSwapLevel;
   }

   /**
    * Sets the reloadSwapLevel for the queue (only used on cache queues).
    * <br />
    * @param Set reloadSwapLevel in bytes.
    */
   public final void setReloadSwapLevel(long reloadSwapLevel) {
      this.reloadSwapLevel = reloadSwapLevel;
   }

   /**
    * Gets the reloadSwapBytes for the queue (only used on cache queues).
    * <br />
    * @return Get reloadSwapBytes in bytes.
    */
   public final long getReloadSwapBytes() {
      return this.reloadSwapBytes;
   }

   /**
    * Sets the reloadSwapBytes for the queue (only used on cache queues).
    * <br />
    * @param Set reloadSwapBytes in bytes.
    */
   public final void setReloadSwapBytes(long reloadSwapBytes) {
      this.reloadSwapBytes = reloadSwapBytes;
   }

   /**
    * Max message queue size for the cache of this queue.
    * <br />
    * @return Set max. message queue size in Bytes
    */
   public final void setMaxBytesCache(long maxBytesCache) {
      this.maxBytesCache.setValue(maxBytesCache);
      checkConsistency();
   }
   private final void setMaxBytesCacheUnchecked(long maxBytesCache) {
      this.maxBytesCache.setValue(maxBytesCache);
   }


   /**
    * Set the callback onOverflow, it should fit to the protocol-relating.
    *
    * @param onOverflow The callback onOverflow, e.g. "et@mars.univers"
    */
   public final void setOnOverflow(String onOverflow) {
      /*
      if (Constants.ONOVERFLOW_BLOCK.equalsIgnoreCase(onOverflow)) {
         this.onOverflow = Constants.ONOVERFLOW_BLOCK;
      }
      */
      if (Constants.ONOVERFLOW_DEADMESSAGE.equalsIgnoreCase(onOverflow)) {
         this.onOverflow.setValue(Constants.ONOVERFLOW_DEADMESSAGE);
      }
      else if (Constants.ONOVERFLOW_DISCARDOLDEST.equalsIgnoreCase(onOverflow)) {
         this.onOverflow.setValue(Constants.ONOVERFLOW_DISCARDOLDEST);

         this.onOverflow.setValue(Constants.ONOVERFLOW_DEADMESSAGE); // TODO !!!
         log.error(ME, getRootTagName() + " onOverflow='" + Constants.ONOVERFLOW_DISCARDOLDEST + "' is not implemented, switching to " + this.onOverflow + " mode");
      }
      else {
         this.onOverflow.setValue(Constants.ONOVERFLOW_DEADMESSAGE);
         log.warn(ME, "The " + getRootTagName() + " onOverflow attribute is invalid '" + onOverflow + "', setting to '" + this.onOverflow + "'");
      }
   }

   /**
    * Returns the onOverflow.
    * @return e.g. "IOR:00001100022...." or "et@universe.com"
    */
   public final String getOnOverflow() {
      return onOverflow.getValue();
   }

   /*
    * The default mode, when queue is full the publisher blocks until
    * there is space again.
   public final boolean onOverflowBlock() {
      if (Constants.ONOVERFLOW_BLOCK.equalsIgnoreCase(getOnOverflow()))
         return true;
      return false;
   }
    */

   /**
    * Set the callback onFailure, it should fit to the protocol-relating.
    *
    * @param onFailure The callback onFailure, e.g. "et@mars.univers"
    */
   public final void setOnFailure(String onFailure) {
      if (Constants.ONOVERFLOW_DEADMESSAGE.equalsIgnoreCase(onFailure))
         this.onFailure.setValue(Constants.ONOVERFLOW_DEADMESSAGE);
      else {
         log.warn(ME, "The " + getRootTagName() + " onFailure attribute is invalid '" + onFailure + "', setting to 'deadMessage'");
         this.onFailure.setValue(Constants.ONOVERFLOW_DEADMESSAGE);
      }
   }

   /**
    * Returns the onFailure.
    * @return e.g. "IOR:00001100022...." or "et@universe.com"
    */
   public final String getOnFailure() {
      return onFailure.getValue();
   }

   /**
    * The default mode is to send a dead letter if callback fails permanently
    */
   public final boolean onFailureDeadMessage() {
      if (Constants.ONOVERFLOW_DEADMESSAGE.equalsIgnoreCase(getOnFailure()))
         return true;
      return false;
   }

   /**
    * @return null if none available
    */
   public AddressBase[] getAddresses() {
      return this.addressArr;
   }

   /**
    * Called for queue start tag
    */
   public final void startElement(String uri, String localName, String name, Attributes attrs) {
      if (attrs != null) {
         int len = attrs.getLength();
         int ii=0;
         for (ii = 0; ii < len; ii++) {
            if (attrs.getQName(ii).equalsIgnoreCase("relating")) {
               setRelating(attrs.getValue(ii).trim());
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("maxMsg")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setMaxMsgUnchecked(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <" + getRootTagName() + " maxMsg='" + tmp + "'>, expected a long, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("type")) {
               setType(attrs.getValue(ii).trim());
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("version")) {
               setVersion(attrs.getValue(ii).trim());
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("maxMsgCache")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setMaxMsgCacheUnchecked(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <" + getRootTagName() + " maxMsgCache='" + tmp + "'>, expected an long, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("maxBytes")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setMaxBytesUnchecked(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <" + getRootTagName() + " maxBytes='" + tmp + "'>, expected a long in bytes, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("maxBytesCache")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setMaxBytesCacheUnchecked(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <" + getRootTagName() + " maxBytesCache='" + tmp + "'>, expected a long in bytes, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("storeSwapLevel")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setStoreSwapLevel(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <" + getRootTagName() + " storeSwapLevel='" + tmp + "'>, expected a long in bytes, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("storeSwapBytes")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setStoreSwapBytes(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <" + getRootTagName() + " storeSwapBytes='" + tmp + "'>, expected a long in bytes, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("reloadSwapLevel")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setReloadSwapLevel(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <" + getRootTagName() + " reloadSwapLevel='" + tmp + "'>, expected a long in bytes, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("reloadSwapBytes")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setReloadSwapBytes(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <" + getRootTagName() + " reloadSwapBytes='" + tmp + "'>, expected a long in bytes, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("onOverflow")) {
               setOnOverflow(attrs.getValue(ii).trim());
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("onFailure")) {
               setOnFailure(attrs.getValue(ii).trim());
            }
            else
               log.warn(ME, "Ignoring unknown attribute '" + attrs.getQName(ii) + "' in connect QoS <" + getRootTagName() + ">");
         }
      }
      else {
         log.warn(ME, "Missing 'relating' attribute in connect QoS <" + getRootTagName() + ">");
      }
      checkConsistency();
   }

   public String usage() {
      return usage("Control the "+getRootTagName()+"/"+this.relating+" storage properties:");
   }

   /**
    * Defaults to queue for &lt;queue .../>, other used tag name is &lt;persistence .../>
    */
   public String getRootTagName() {
      return "queue";
   }

   /**
    * Get a usage string for queue configuration (in xmlBlaster.properties or on command line)
    */
   protected String usage(String headerline) {
      String prefix = getPrefix();
      String text = "";
      text += headerline + "\n";
      text += "   -"+prefix+"maxMsg       The maximum allowed number of messages [" + this.maxMsg.getDefaultValue() + "].\n";
      text += "   -"+prefix+"maxMsgCache  The maximum allowed number of messages in the cache [" + this.maxMsgCache.getDefaultValue() + "].\n";
      text += "   -"+prefix+"maxBytes      The maximum size in bytes of the storage [" + this.maxBytes.getDefaultValue() + "].\n";
      text += "   -"+prefix+"maxBytesCache The maximum size in bytes in the cache [" + this.maxBytesCache.getDefaultValue() + "].\n";
      text += "   -"+prefix+"onOverflow What happens if storage is full [" + this.onOverflow.getDefaultValue() + "]\n";
      text += "   -"+prefix+"onFailure  Error handling when storage failed [" + this.onFailure.getDefaultValue() + "]\n";
      text += "   -"+prefix+"type       The plugin type [" + this.type.getDefaultValue() + "]\n";
      text += "   -"+prefix+"version    The plugin version [" + this.version.getDefaultValue() + "]\n";
      return text;
   }

   /**
    * Should be called after parsing
    */
   public final void checkConsistency() { // throws XmlBlasterException {
      if (getMaxMsgCache() > getMaxMsg()) {
         log.warn(ME, "maxMsgCache=" + getMaxMsgCache() + " is bigger than maxMsg=" + getMaxMsg() + ", we reduce maxMsgCache to maxMsg and continue.");
         this.maxMsgCache.setValue(getMaxMsg());
      }
      if (getMaxBytesCache() > getMaxBytes()) {
         log.warn(ME, "maxBytesCache=" + getMaxBytesCache() + " is bigger than maxBytes=" + getMaxBytes() + ", we reduce maxBytesCache to maxBytes and continue.");
         this.maxBytesCache.setValue(getMaxBytes());
      }
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
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      // open <queue ...
      sb.append(offset).append("<").append(getRootTagName());
      sb.append(" relating='").append(getRelating()).append("'");
      if (this.type.isModified())
         sb.append(" type='").append(getType()).append("'");
      if (this.version.isModified())
         sb.append(" version='").append(getVersion()).append("'");
      if (this.maxMsg.isModified())
         sb.append(" maxMsg='").append(getMaxMsg()).append("'");
      if (this.maxMsgCache.isModified())
         sb.append(" maxMsgCache='").append(getMaxMsgCache()).append("'");
      if (this.maxBytes.isModified())
         sb.append(" maxBytes='").append(getMaxBytes()).append("'");
      if (this.maxBytesCache.isModified())
         sb.append(" maxBytesCache='").append(getMaxBytesCache()).append("'");

      /* Deactivated until implemented in queue framework
      if ((long)(DEFAULT_storeSwapLevelRatio*this.maxBytesCache.getValue()) != getStoreSwapLevel())
         sb.append(" storeSwapLevel='").append(getStoreSwapLevel()).append("'");
      if ((long)(DEFAULT_storeSwapBytesRatio*this.maxBytesCache.getValue()) != getStoreSwapBytes())
         sb.append(" storeSwapBytes='").append(getStoreSwapBytes()).append("'");
      if ((long)(DEFAULT_reloadSwapLevelRatio*this.maxBytesCache.getValue()) != getReloadSwapLevel())
         sb.append(" reloadSwapLevel='").append(getReloadSwapLevel()).append("'");
      if ((long)(DEFAULT_reloadSwapBytesRatio*this.maxBytesCache.getValue()) != getReloadSwapBytes())
         sb.append(" reloadSwapBytes='").append(getReloadSwapBytes()).append("'");
      */
      if (this.onOverflow.isModified())
         sb.append(" onOverflow='").append(getOnOverflow()).append("'");
      if (this.onFailure.isModified())
         sb.append(" onFailure='").append(getOnFailure()).append("'");

      if (addressArr.length > 0 && addressArr[0] != null) {
         sb.append(">");
         for (int ii=0; ii<addressArr.length; ii++) {
            AddressBase ad = addressArr[ii];
            sb.append(ad.toXml(extraOffset+Constants.INDENT));
         }
         sb.append(offset).append("</").append(getRootTagName()).append(">");  // closing </queue>
      }
      else
         sb.append("/>");

      return sb.toString();
   }

   /**
    * returns the global object
    */
   public Global getGlobal() {
      return this.glob;
   }

   /**
    * Returns a shallow clone, you can change savely all basic or immutable types
    * like boolean, String, int.
    */
   public Object clone() {
      QueuePropertyBase newOne = null;
      try {
         newOne = (QueuePropertyBase)super.clone();
         synchronized(this) {
            newOne.maxMsg = (PropLong)this.maxMsg.clone();
            newOne.maxMsgCache = (PropLong)this.maxMsgCache.clone();
            newOne.maxBytes = (PropLong)this.maxBytes.clone();
            newOne.maxBytesCache = (PropLong)this.maxBytesCache.clone();
            newOne.type = (PropString)this.type.clone();
            newOne.version = (PropString)this.version.clone();
            newOne.onOverflow = (PropString)this.onOverflow.clone();
            newOne.onFailure = (PropString)this.onFailure.clone();
         }
         return newOne;
      }
      catch (CloneNotSupportedException e) {
         return null;
      }
   }
}


