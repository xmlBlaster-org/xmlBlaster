/*------------------------------------------------------------------------------
Name:      QueueProperty.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Version:   $Id: QueuePropertyBase.java,v 1.5 2002/11/26 12:38:45 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xml.sax.Attributes;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * Helper class holding callback queue properties.
 * <p />
 * See ConnectQos for XML syntax.
 * @see org.xmlBlaster.util.ConnectQos
 */
public abstract class QueuePropertyBase
{
   private static final String ME = "QueuePropertyBase";
   protected final Global glob;
   protected final LogChannel log;

   /** The queue plugin type "CACHE" "RAM" "JDBC" */
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

   /** The max setting allowed for queue maxSize in Bytes is adjustable with property "queue.maxSize=4194304" (4 MBytes is default) */
   public static final long DEFAULT_sizeDefault = 10485760L; // 10 MB
   protected long maxSizeDefault;

   /** The max setting allowed for queue maxSizeCache in Bytes is adjustable with property "queue.maxSizeCache=4000" (4 MBytes is default) */
   public static final long DEFAULT_sizeCacheDefault = 2097152L; // 2 MB
   protected long maxSizeCacheDefault;

   /** The default settings (as a ratio relative to the maxSizeCache) for the storeSwapLevel */
   public static final double DEFAULT_storeSwapLevelRatio = 0.70;

   /** The default settings (as a ratio relative to the maxSizeCache) for the storeSwapSize */
   public static final double DEFAULT_storeSwapSizeRatio = 0.25;

   /** The default settings (as a ratio relative to the maxSizeCache) for the storeSwapLevel */
   public static final double DEFAULT_reloadSwapLevelRatio = 0.30;

   /** The default settings (as a ratio relative to the maxSizeCache) for the storeSwapSize */
   public static final double DEFAULT_reloadSwapSizeRatio = 0.25;

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
   protected long maxSize;
   /** The max. capacity of the cache of the queue in number of entries */
   protected long maxMsgCache;

   /** The settings for the storeSwapLevel */
   protected long storeSwapLevel;

   /** The settings for the storeSwapSize */
   protected long storeSwapSize;

   /** The settings for the storeSwapLevel */
   protected long reloadSwapLevel;

   /** The settings for the storeSwapSize */
   protected long reloadSwapSize;

   /** The max. capacity of the queue in Bytes for the cache */
   protected long maxSizeCache;

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
   public QueuePropertyBase(Global glob, String nodeId)
   {
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
    * Configure property settings, add your own defaults in the derived class
    */
   protected void initialize() {
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
   public final long getExpires()
   {
      return expires;
   }


   /**
    * Span of life of this queue.
    * @param Expiry time in milliseconds
    */
   public final void setExpires(long expires)
   {
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
   public final long getMaxMsg()
   {
      return this.maxMsg;
   }

   /**
    * Max number of messages for this queue.
    * <br />
    * @param maxMsg
    */
   public final void setMaxMsg(long maxMsg)
   {
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
   public final long getMaxMsgCache()
   {
      return this.maxMsgCache;
   }

   /**
    * Max number of messages for the cache of this queue.
    * <br />
    * @param maxMsg
    */
   public final void setMaxMsgCache(long maxMsgCache)
   {
      this.maxMsgCache = maxMsgCache;
   }


   /**
    * Max message queue size.
    * <br />
    * @return Get max. message queue size in Bytes
    */
   public final long getMaxSize()
   {
      return this.maxSize;
   }

   /**
    * Max message queue size.
    * <br />
    * @return Set max. message queue size in Bytes
    */
   public final void setMaxSize(long maxSize)
   {
      this.maxSize = maxSize;
   }


   /**
    * Max message queue size for the cache of this queue.
    * <br />
    * @return Get max. message queue size in Bytes
    */
   public final long getMaxSizeCache()
   {
      return this.maxSizeCache;
   }


   /**
    * Gets the storeSwapLevel for the queue (only used on cache queues).
    * <br />
    * @return Get storeSwapLevel in bytes.
    */
   public final long getStoreSwapLevel()
   {
      return this.storeSwapLevel;
   }

   /**
    * Sets the storeSwapLevel for the queue (only used on cache queues).
    * <br />
    * @param Set storeSwapLevel in bytes.
    */
   public final void setStoreSwapLevel(long storeSwapLevel)
   {
      this.storeSwapLevel = storeSwapLevel;
   }

   /**
    * Gets the storeSwapSize for the queue (only used on cache queues).
    * <br />
    * @return Get storeSwapSize in bytes.
    */
   public final long getStoreSwapSize()
   {
      return this.storeSwapSize;
   }

   /**
    * Sets the storeSwapSize for the queue (only used on cache queues).
    * <br />
    * @param Set storeSwapSize in bytes.
    */
   public final void setStoreSwapSize(long storeSwapSize)
   {
      this.storeSwapSize = storeSwapSize;
   }

   /**
    * Gets the reloadSwapLevel for the queue (only used on cache queues).
    * <br />
    * @return Get reloadSwapLevel in bytes.
    */
   public final long getReloadSwapLevel()
   {
      return this.reloadSwapLevel;
   }

   /**
    * Sets the reloadSwapLevel for the queue (only used on cache queues).
    * <br />
    * @param Set reloadSwapLevel in bytes.
    */
   public final void setReloadSwapLevel(long reloadSwapLevel)
   {
      this.reloadSwapLevel = reloadSwapLevel;
   }

   /**
    * Gets the reloadSwapSize for the queue (only used on cache queues).
    * <br />
    * @return Get reloadSwapSize in bytes.
    */
   public final long getReloadSwapSize()
   {
      return this.reloadSwapSize;
   }

   /**
    * Sets the reloadSwapSize for the queue (only used on cache queues).
    * <br />
    * @param Set reloadSwapSize in bytes.
    */
   public final void setReloadSwapSize(long reloadSwapSize)
   {
      this.reloadSwapSize = reloadSwapSize;
   }

   /**
    * Max message queue size for the cache of this queue.
    * <br />
    * @return Set max. message queue size in Bytes
    */
   public final void setMaxSizeCache(long maxSizeCache)
   {
      this.maxSizeCache = maxSizeCache;
   }


   /**
    * Set the callback onOverflow, it should fit to the protocol-relating.
    *
    * @param onOverflow The callback onOverflow, e.g. "et@mars.univers"
    */
   public final void setOnOverflow(String onOverflow)
   {
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
         log.error(ME, "queue onOverflow='" + Constants.ONOVERFLOW_DISCARDOLDEST + "' is not implemented, switching to " + this.onOverflow + " mode");
      }
      else {
         this.onOverflow = Constants.ONOVERFLOW_DEADMESSAGE;
         log.warn(ME, "The queue onOverflow attribute is invalid '" + onOverflow + "', setting to '" + this.onOverflow + "'");
      }
   }

   /**
    * Returns the onOverflow.
    * @return e.g. "IOR:00001100022...." or "et@universe.com"
    */
   public final String getOnOverflow()
   {
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
         log.warn(ME, "The queue onFailure attribute is invalid '" + onFailure + "', setting to 'deadMessage'");
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
                  setMaxMsg(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <queue maxMsg='" + tmp + "'>, expected a long, using default.");
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
                  setMaxMsgCache(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <queue maxMsgCache='" + tmp + "'>, expected an long, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("maxSize")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setMaxSize(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <queue maxSize='" + tmp + "'>, expected a long in bytes, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("maxSizeCache")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setMaxSizeCache(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <queue maxSizeCache='" + tmp + "'>, expected a long in bytes, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("storeSwapLevel")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setStoreSwapLevel(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <queue storeSwapLevel='" + tmp + "'>, expected a long in bytes, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("storeSwapSize")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setStoreSwapSize(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <queue storeSwapSize='" + tmp + "'>, expected a long in bytes, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("reloadSwapLevel")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setReloadSwapLevel(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <queue reloadSwapLevel='" + tmp + "'>, expected a long in bytes, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("reloadSwapSize")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setReloadSwapSize(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <queue reloadSwapSize='" + tmp + "'>, expected a long in bytes, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("expires")) {
               String tmp = attrs.getValue(ii).trim();
               try {
                  setExpires(new Long(tmp).longValue());
               } catch (NumberFormatException e) {
                  log.error(ME, "Wrong format of <queue expires='" + tmp + "'>, expected a long in milliseconds, using default.");
               }
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("onOverflow")) {
               setOnOverflow(attrs.getValue(ii).trim());
            }
            else if (attrs.getQName(ii).equalsIgnoreCase("onFailure")) {
               setOnFailure(attrs.getValue(ii).trim());
            }
            else
               log.warn(ME, "Ignoring unknown attribute '" + attrs.getQName(ii) + "' in connect QoS <queue>");
         }
      }
      else {
         log.warn(ME, "Missing 'relating' attribute in connect QoS <queue>");
      }
   }


   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml()
   {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   public final String toXml(String extraOffset)
   {
      StringBuffer buf = new StringBuffer(256);
      String offset = "\n   ";
      if (extraOffset != null) offset += extraOffset;
      else extraOffset = "";

      buf.append(offset).append("<!-- QueuePropertyBase -->");

      buf.append(offset).append("<queue relating='").append(getRelating());
      if (DEFAULT_type != getType())
         buf.append("' type='").append(getType());
      if (DEFAULT_version != getVersion())
         buf.append("' version='").append(getVersion());
      if (DEFAULT_maxMsgDefault != getMaxMsg())
         buf.append("' maxMsg='").append(getMaxMsg());
      if (DEFAULT_maxMsgCacheDefault != getMaxMsgCache())
         buf.append("' maxMsgCache='").append(getMaxMsgCache());
      if (DEFAULT_sizeDefault != getMaxSize())
         buf.append("' maxSize='").append(getMaxSize());
      if (DEFAULT_sizeCacheDefault != getMaxSizeCache())
         buf.append("' maxSizeCache='").append(getMaxSizeCache());
      buf.append("' storeSwapLevel='").append(getStoreSwapLevel());
      buf.append("' storeSwapSize='").append(getStoreSwapSize());
      buf.append("' reloadSwapLevel='").append(getReloadSwapLevel());
      buf.append("' reloadSwapSize='").append(getReloadSwapSize());
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
            buf.append(ad.toXml(extraOffset+"   "));
         }
         buf.append(offset).append("</queue>");
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
}


