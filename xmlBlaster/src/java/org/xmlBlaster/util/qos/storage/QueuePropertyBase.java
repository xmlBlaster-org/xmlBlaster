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
   public static String DEFAULT_type = "CACHE";
   protected String type;

   /** The queue plugin version "1.0" or similar */
   public static String DEFAULT_version = "1.0";
   protected String version;

   /** The max setting allowed for queue maxMsg is adjustable with property "queue.maxMsg=1000" (1000 messages is default) */
   public static final long DEFAULT_maxMsgDefault = 1000L;
   protected long maxMsgDefault;

   /** The max setting allowed for queue maxMsgCache is adjustable with property "queue.maxMsgCache=1000" (1000 messages is default) */
   public static final long DEFAULT_maxMsgCacheDefault = 1000L;
   protected long maxMsgCacheDefault;

   /** The max setting allowed for queue max size in bytes is adjustable with property "queue.maxBytes=4194304" (4 MBytes is default) */
   public static final long DEFAULT_bytesDefault = 10485760L; // 10 MB
   protected long maxBytesDefault;

   /** The max setting allowed for queue max size of cache in bytes is adjustable with property "queue.maxBytesCache=4000000" (4 MBytes is default) */
   public static final long DEFAULT_bytesCacheDefault = 2097152L; // 2 MB
   protected long maxBytesCacheDefault;

   /** The default settings (as a ratio relative to the maxBytesCache) for the storeSwapLevel */
   public static final double DEFAULT_storeSwapLevelRatio = 0.70;

   /** The default settings (as a ratio relative to the maxBytesCache) for the storeSwapBytes */
   public static final double DEFAULT_storeSwapBytesRatio = 0.25;

   /** The default settings (as a ratio relative to the maxBytesCache) for the storeSwapLevel */
   public static final double DEFAULT_reloadSwapLevelRatio = 0.30;

   /** The default settings (as a ratio relative to the maxBytesCache) for the storeSwapBytes */
   public static final double DEFAULT_reloadSwapBytesRatio = 0.25;

   /** The min span of life is one second, changeable with property e.g. "queue.expires.min=2000" milliseconds */
   public static final long DEFAULT_minExpires = 1000L;
   protected long minExpires;

   /** The max span of life of a queue is currently forever (=0), changeable with property e.g. "queue.expires.max=3600000" milliseconds */
   public static final long DEFAULT_maxExpires = 0L;
   protected long maxExpires;

   /** If not otherwise noted a queue dies after the max value, changeable with property e.g. "queue.expires=3600000" milliseconds */
   public long DEFAULT_expires;

   /** The unique protocol relating, e.g. "IOR" */
   protected String relating = Constants.RELATING_SESSION;
   /** Span of life of this queue in milliseconds */
   protected long expires = DEFAULT_expires;
   /** The max. capacity of the queue in number of entries */
   protected long maxMsg;
   /** The max. capacity of the queue in Bytes */
   protected long maxBytes;
   /** The max. capacity of the cache of the queue in number of entries */
   protected long maxMsgCache;

   /** The settings for the storeSwapLevel */
   protected long storeSwapLevel;

   /** The settings for the storeSwapBytes */
   protected long storeSwapBytes;

   /** The settings for the storeSwapLevel */
   protected long reloadSwapLevel;

   /** The settings for the storeSwapBytes */
   protected long reloadSwapBytes;

   /** The max. capacity of the queue in Bytes for the cache */
   protected long maxBytesCache;

   /** Error handling when queue is full: Constants.ONOVERFLOW_DEADMESSAGE | Constants.ONOVERFLOW_DISCARDOLDEST */
   public static final String DEFAULT_onOverflow = Constants.ONOVERFLOW_DEADMESSAGE;
   protected String onOverflow;

   /** Error handling when callback failed (after all retries etc.): Constants.ONOVERFLOW_DEADMESSAGE */
   public static final String DEFAULT_onFailure = Constants.ONOVERFLOW_DEADMESSAGE;
   protected String onFailure;

   /** The corresponding callback address */
   protected AddressBase[] addressArr = new AddressBase[0];

   /** To allow specific configuration parameters for specific cluster nodes */
   protected String nodeId = null;

   /**
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue.maxMsg and -queue.maxMsg[heron] will be searched
    */
   public QueuePropertyBase(Global glob, String nodeId) {
      if (glob == null) {
         Thread.currentThread().dumpStack();
         this.glob = new Global();
      }
      else
         this.glob = glob;
      this.log = glob.getLog("core");
      this.nodeId = nodeId;
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
    * @return The prefix for properties e.g. "history" -> "-history.queue.maxMsg"
    */
   public String getPropertyPrefix() {
      return this.propertyPrefix;
   }

   /**
    * The command line prefix to configure the queue or msgUnitStore
    * @return e.g. "msgUnitStore." or "history.queue."
    */
   public String getPrefix() {
      return (this.propertyPrefix.length() > 0) ? this.propertyPrefix+"."+getRootTagName()+"." : getRootTagName()+".";
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
    * @param propertyPrefix e.g. "history" or "cb"
    */
   protected void initialize(String propertyPrefix) {
      this.propertyPrefix = (propertyPrefix == null) ? "" : propertyPrefix;
      String prefix = getPrefix();

      // Do we need this range settings?
      setMinExpires(glob.getProperty().get("queue.expires.min", DEFAULT_minExpires));
      setMaxExpires(glob.getProperty().get("queue.expires.max", DEFAULT_maxExpires)); // Long.MAX_VALUE);
      if (nodeId != null) {
         setMinExpires(glob.getProperty().get("queue.expires.min["+nodeId+"]", getMinExpires()));
         setMaxExpires(glob.getProperty().get("queue.expires.max["+nodeId+"]", getMaxExpires())); // Long.MAX_VALUE);
      }

      try {
         PluginInfo pluginInfo = new PluginInfo(glob, null, glob.getProperty().get("queue.defaultPlugin", DEFAULT_type));
         DEFAULT_type = pluginInfo.getType();
         DEFAULT_version = pluginInfo.getVersion();
      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "initialize: could not set the default plugin to what indicated by queue.defaultPlugin");
      }
                                
      // prefix is e.g. "cb.queue." or "msgUnitStore"

      setMaxMsgUnchecked(glob.getProperty().get(prefix+"maxMsg", DEFAULT_maxMsgDefault));
      setMaxMsgCacheUnchecked(glob.getProperty().get(prefix+"maxMsgCache", DEFAULT_maxMsgCacheDefault));
      setMaxBytesUnchecked(glob.getProperty().get(prefix+"maxBytes", DEFAULT_bytesDefault));
      setMaxBytesCacheUnchecked(glob.getProperty().get(prefix+"maxBytesCache", DEFAULT_bytesCacheDefault));
      setStoreSwapLevel(glob.getProperty().get(prefix+"storeSwapLevel", (long)(DEFAULT_storeSwapLevelRatio*this.maxBytesCache)));
      setStoreSwapBytes(glob.getProperty().get(prefix+"storeSwapBytes", (long)(DEFAULT_storeSwapBytesRatio*this.maxBytesCache)));
      setReloadSwapLevel(glob.getProperty().get(prefix+"reloadSwapLevel", (long)(DEFAULT_reloadSwapLevelRatio*this.maxBytesCache)));
      setReloadSwapBytes(glob.getProperty().get(prefix+"reloadSwapBytes", (long)(DEFAULT_reloadSwapBytesRatio*this.maxBytesCache)));
      setExpires(glob.getProperty().get(prefix+"expires", DEFAULT_maxExpires));
      setOnOverflow(glob.getProperty().get(prefix+"onOverflow", DEFAULT_onOverflow));
      setOnFailure(glob.getProperty().get(prefix+"onFailure", DEFAULT_onFailure));
      setType(glob.getProperty().get(prefix+"type", DEFAULT_type));
      setVersion(glob.getProperty().get(prefix+"version", DEFAULT_version));
      if (nodeId != null) {
         setMaxMsgUnchecked(glob.getProperty().get(prefix+"maxMsg["+nodeId+"]", getMaxMsg()));
         setMaxMsgCacheUnchecked(glob.getProperty().get(prefix+"maxMsgCache["+nodeId+"]", getMaxMsgCache()));
         setMaxBytesUnchecked(glob.getProperty().get(prefix+"maxBytes["+nodeId+"]", getMaxBytes()));
         setMaxBytesCacheUnchecked(glob.getProperty().get(prefix+"maxBytesCache["+nodeId+"]", getMaxBytesCache()));
         setStoreSwapLevel(glob.getProperty().get(prefix+"storeSwapLevel["+nodeId+"]", getStoreSwapLevel()));
         setStoreSwapBytes(glob.getProperty().get(prefix+"storeSwapBytes["+nodeId+"]", getStoreSwapBytes()));
         setReloadSwapLevel(glob.getProperty().get(prefix+"reloadSwapLevel["+nodeId+"]", getReloadSwapLevel()));
         setReloadSwapBytes(glob.getProperty().get(prefix+"reloadSwapBytes["+nodeId+"]", getReloadSwapBytes()));
         setExpires(glob.getProperty().get(prefix+"expires["+nodeId+"]", getExpires()));
         setOnOverflow(glob.getProperty().get(prefix+"onOverflow["+nodeId+"]", getOnOverflow()));
         setOnFailure(glob.getProperty().get(prefix+"onFailure["+nodeId+"]", getOnFailure()));
         setType(glob.getProperty().get(prefix+"type["+nodeId+"]", getType()));
         setVersion(glob.getProperty().get(prefix+"version["+nodeId+"]", getVersion()));
      }
      
      checkConsistency();
   }

   protected void setMaxExpires(long maxExpires) { this.maxExpires = maxExpires; }
   protected long getMaxExpires() { return this.maxExpires; }

   protected void setMinExpires(long minExpires) { this.minExpires = minExpires; }
   protected long getMinExpires() { return this.minExpires; }

   /**
    * @param relating    To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT | Constants.RELATING_CLIENT
    */
   public void setRelating(String relating) {
      if (Constants.RELATING_SESSION.equalsIgnoreCase(relating))
         this.relating = Constants.RELATING_SESSION;
      else if (Constants.RELATING_SUBJECT.equalsIgnoreCase(relating))
         this.relating = Constants.RELATING_SUBJECT;
      else if (Constants.RELATING_CLIENT.equalsIgnoreCase(relating))
         this.relating = Constants.RELATING_CLIENT;
      else if (Constants.RELATING_HISTORY.equalsIgnoreCase(relating))
         this.relating = Constants.RELATING_HISTORY;
      else if (Constants.RELATING_TOPICCACHE.equalsIgnoreCase(relating))
         this.relating = Constants.RELATING_TOPICCACHE;
      else {
         log.warn(ME, "Ignoring relating=" + relating);
         Thread.currentThread().dumpStack();
      }
   }

   /**
    * Returns the queue type.
    * @return relating    To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT
    */
   public final String getRelating() {
      return this.relating;
   }

   /**
    * Span of life of this queue.
    * @return Expiry time in milliseconds or 0L if forever
    */
   public final long getExpires() {
      return expires;
   }

   /**
    * Span of life of this queue.
    * @param Expiry time in milliseconds
    */
   public final void setExpires(long expires) {
      if (maxExpires <= 0L)
         this.expires = expires;
      else if (expires > 0L && maxExpires > 0L && expires > maxExpires)
         this.expires = maxExpires;
      else if (expires <= 0L && maxExpires > 0L)
         this.expires = maxExpires;

      if (expires > 0L && expires < minExpires)
         this.expires = minExpires;
   }

   /**
    * Max number of messages for this queue.
    * <br />
    * @return number of messages
    */
   public final long getMaxMsg() {
      return this.maxMsg;
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
      this.maxMsg = maxMsg;
   }

   /**
    * The plugin type. 
    * <br />
    * @return e.g. "CACHE"
    */
   public final String getType() {
      return this.type;
   }

   /**
    * The plugin type
    * <br />
    * @param type
    */
   public final void setType(String type) {
      this.type = type;
   }

   /**
    * The plugin version. 
    * <br />
    * @return e.g. "1.0"
    */
   public final String getVersion() {
      return this.version;
   }

   /**
    * The plugin version
    * <br />
    * @param version
    */
   public final void setVersion(String version) {
      this.version = version;
   }

   /**
    * Max number of messages for the cache of this queue.
    * <br />
    * @return number of messages
    */
   public final long getMaxMsgCache() {
      return this.maxMsgCache;
   }

   /**
    * Max number of messages for the cache of this queue.
    * <br />
    * @param maxMsg
    */
   public final void setMaxMsgCache(long maxMsgCache) {
      this.maxMsgCache = maxMsgCache;
      checkConsistency();
   }
   private final void setMaxMsgCacheUnchecked(long maxMsgCache) {
      this.maxMsgCache = maxMsgCache;
   }

   /**
    * Max message queue size.
    * <br />
    * @return Get max. message queue size in Bytes
    */
   public final long getMaxBytes() {
      return this.maxBytes;
   }

   /**
    * Max message queue size.
    * <br />
    * @return Set max. message queue size in Bytes
    */
   public final void setMaxBytes(long maxBytes) {
      this.maxBytes = maxBytes;
      checkConsistency();
   }
   private final void setMaxBytesUnchecked(long maxBytes) {
      this.maxBytes = maxBytes;
   }


   /**
    * Max message queue size for the cache of this queue.
    * <br />
    * @return Get max. message queue size in Bytes
    */
   public final long getMaxBytesCache() {
      return this.maxBytesCache;
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
      this.maxBytesCache = maxBytesCache;
      checkConsistency();
   }
   private final void setMaxBytesCacheUnchecked(long maxBytesCache) {
      this.maxBytesCache = maxBytesCache;
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
         this.onOverflow = Constants.ONOVERFLOW_DEADMESSAGE;
      }
      else if (Constants.ONOVERFLOW_DISCARDOLDEST.equalsIgnoreCase(onOverflow)) {
         this.onOverflow = Constants.ONOVERFLOW_DISCARDOLDEST;

         this.onOverflow = Constants.ONOVERFLOW_DEADMESSAGE; // TODO !!!
         log.error(ME, getRootTagName() + " onOverflow='" + Constants.ONOVERFLOW_DISCARDOLDEST + "' is not implemented, switching to " + this.onOverflow + " mode");
      }
      else {
         this.onOverflow = Constants.ONOVERFLOW_DEADMESSAGE;
         log.warn(ME, "The " + getRootTagName() + " onOverflow attribute is invalid '" + onOverflow + "', setting to '" + this.onOverflow + "'");
      }
   }

   /**
    * Returns the onOverflow.
    * @return e.g. "IOR:00001100022...." or "et@universe.com"
    */
   public final String getOnOverflow() {
      return onOverflow;
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
         this.onFailure = Constants.ONOVERFLOW_DEADMESSAGE;
      else {
         log.warn(ME, "The " + getRootTagName() + " onFailure attribute is invalid '" + onFailure + "', setting to 'deadMessage'");
         this.onFailure = Constants.ONOVERFLOW_DEADMESSAGE;
      }
   }

   /**
    * Returns the onFailure.
    * @return e.g. "IOR:00001100022...." or "et@universe.com"
    */
   public final String getOnFailure() {
      return onFailure;
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
      return addressArr;
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
            else if (attrs.getQName(ii).equalsIgnoreCase("expires")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setExpires(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <" + getRootTagName() + " expires='" + tmp + "'>, expected a long in milliseconds, using default.");
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
      return usage("Control the "+this.propertyPrefix+" queue properties:");
   }

   /**
    * Defaults to queue for &lt;queue ...>
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
      text += "   -"+prefix+"maxMsg       The maximum allowed number of messages [" + DEFAULT_maxMsgDefault + "].\n";
      text += "   -"+prefix+"maxMsgCache  The maximum allowed number of messages in the cache [" + DEFAULT_maxMsgDefault + "].\n";
      text += "   -"+prefix+"maxBytes      The maximum size in bytes of the storage [" + DEFAULT_bytesDefault + "].\n";
      text += "   -"+prefix+"maxBytesCache The maximum size in bytes in the cache [" + DEFAULT_bytesCacheDefault + "].\n";
    //text += "   -"+prefix+"expires  If not otherwise noted a queue dies after these milliseconds [" + DEFAULT_expiresDefault + "].\n";
    //text += "   -"+prefix+"onOverflow What happens if queue is full. " + Constants.ONOVERFLOW_BLOCK + " | " + Constants.ONOVERFLOW_DEADMESSAGE + " [" + DEFAULT_onOverflow + "]\n";
      text += "   -"+prefix+"onOverflow What happens if storage is full [" + DEFAULT_onOverflow + "]\n";
      text += "   -"+prefix+"onFailure  Error handling when storage failed [" + DEFAULT_onFailure + "]\n";
      text += "   -"+prefix+"type       The plugin type [" + DEFAULT_type + "]\n";
      text += "   -"+prefix+"version    The plugin version [" + DEFAULT_version + "]\n";
      return text;
   }

   /**
    * Should be called after parsing
    */
   public final void checkConsistency() { // throws XmlBlasterException {
      //if (getType() == null) {
      //   throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Type may not be null in " + toXml());
      //}
      /*
      if (DEFAULT_maxMsgCacheDefault > DEFAULT_maxMsgDefault) {
         log.warn(ME, "DEFAULT_maxMsgCacheDefault=" + DEFAULT_maxMsgCacheDefault + " is bigger than DEFAULT_maxMsgDefault=" + DEFAULT_maxMsgDefault + ", we reduce DEFAULT_maxMsgCacheDefault to DEFAULT_maxMsgDefault and continue.");
      }
      if (DEFAULT_bytesCacheDefault > DEFAULT_bytesDefault) {
         log.warn(ME, "DEFAULT_bytesCacheDefault=" + DEFAULT_bytesCacheDefault + " is bigger than DEFAULT_bytesDefault=" + DEFAULT_bytesDefault + ", we reduce DEFAULT_bytesCacheDefault to DEFAULT_bytesDefault and continue.");
      }
      */
      if (getMaxMsgCache() > getMaxMsg()) {
         log.warn(ME, "maxMsgCache=" + getMaxMsgCache() + " is bigger than maxMsg=" + getMaxMsg() + ", we reduce maxMsgCache to maxMsg and continue.");
         this.maxMsgCache = getMaxMsg();
      }
      if (getMaxBytesCache() > getMaxBytes()) {
         log.warn(ME, "maxBytesCache=" + getMaxBytesCache() + " is bigger than maxBytes=" + getMaxBytes() + ", we reduce maxBytesCache to maxBytes and continue.");
         this.maxBytesCache = getMaxBytes();
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
      StringBuffer buf = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      // open <queue ...
      buf.append(offset).append("<").append(getRootTagName()).append(" relating='").append(getRelating());
      if (DEFAULT_type != getType())
         buf.append("' type='").append(getType());
      if (DEFAULT_version != getVersion())
         buf.append("' version='").append(getVersion());
      if (DEFAULT_maxMsgDefault != getMaxMsg())
         buf.append("' maxMsg='").append(getMaxMsg());
      if (DEFAULT_maxMsgCacheDefault != getMaxMsgCache())
         buf.append("' maxMsgCache='").append(getMaxMsgCache());
      if (DEFAULT_bytesDefault != getMaxBytes())
         buf.append("' maxBytes='").append(getMaxBytes());
      if (DEFAULT_bytesCacheDefault != getMaxBytesCache())
         buf.append("' maxBytesCache='").append(getMaxBytesCache());
      buf.append("' storeSwapLevel='").append(getStoreSwapLevel());
      buf.append("' storeSwapBytes='").append(getStoreSwapBytes());
      buf.append("' reloadSwapLevel='").append(getReloadSwapLevel());
      buf.append("' reloadSwapBytes='").append(getReloadSwapBytes());
      if (DEFAULT_expires != getExpires())
         buf.append("' expires='").append(getExpires());
      if (DEFAULT_onOverflow != getOnOverflow())
         buf.append("' onOverflow='").append(getOnOverflow());
      if (DEFAULT_onFailure != getOnFailure())
         buf.append("' onFailure='").append(getOnFailure());

      if (addressArr.length > 0 && addressArr[0] != null) {
         buf.append("'>");
         for (int ii=0; ii<addressArr.length; ii++) {
            AddressBase ad = addressArr[ii];
            buf.append(ad.toXml(extraOffset+Constants.INDENT));
         }
         buf.append(offset).append("</").append(getRootTagName()).append(">");  // closing </queue>
      }
      else
         buf.append("'/>");

      return buf.toString();
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
      try {
         return super.clone();
      }
      catch (CloneNotSupportedException e) {
         return null;
      }
   }
}


