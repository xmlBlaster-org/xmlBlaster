/*------------------------------------------------------------------------------
Name:      QueuePropertyBase.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Version:   $Id$
------------------------------------------------------------------------------*/

/**
 * Helper class holding callback queue properties.
 * <p />
 * See org::xmlBlaster::util::qos::ConnectQos for XML syntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */

#ifndef _UTIL_QUEUE_QUEUEPROPERTYBASE_H
#define _UTIL_QUEUE_QUEUEPROPERTYBASE_H

#include <util/xmlBlasterDef.h>
#include <util/Constants.h>
#include <util/qos/address/AddressBase.h>

#include <string>
#include <vector>

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

extern Dll_Export  const long DEFAULT_maxEntriesDefault;
extern Dll_Export  const long DEFAULT_maxEntriesCacheDefault;
extern Dll_Export  const long DEFAULT_bytesDefault;
extern Dll_Export  const long DEFAULT_bytesCacheDefault;
/** The default settings (as a ratio relative to the maxBytesCache) for the storeSwapLevel */
extern Dll_Export  const double DEFAULT_storeSwapLevelRatio;
/** The default settings (as a ratio relative to the maxBytesCache) for the storeSwapBytes */
extern Dll_Export  const double DEFAULT_storeSwapBytesRatio;
/** The default settings (as a ratio relative to the maxBytesCache) for the storeSwapLevel */
extern Dll_Export  const double DEFAULT_reloadSwapLevelRatio;
/** The default settings (as a ratio relative to the maxBytesCache) for the storeSwapBytes */
extern Dll_Export  const double DEFAULT_reloadSwapBytesRatio;
extern Dll_Export  const Timestamp DEFAULT_minExpires;
extern Dll_Export  const Timestamp DEFAULT_maxExpires;
extern Dll_Export  const std::string DEFAULT_onOverflow;
extern Dll_Export  const std::string DEFAULT_onFailure;

// static variables
extern Dll_Export  std::string DEFAULT_type;
extern Dll_Export  std::string DEFAULT_version;
/** If not otherwise noted a queue dies after the max value, changeable with property e.g. "queue/expires=3600000" milliseconds */
extern Dll_Export  long DEFAULT_expires;

typedef std::vector<org::xmlBlaster::util::qos::address::AddressBaseRef> AddressVector;

class Dll_Export QueuePropertyBase
{
   friend class QueuePropertyFactory;
protected:
   std::string  ME; //  = "QueuePropertyBase";
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log&    log_;

   /** The queue plugin type "CACHE" "RAM" "JDBC" */
   std::string type_;

   /** The queue plugin version "1.0" or similar */
   std::string version_;

   /** The max setting allowed for queue maxEntries is adjustable with property "queue/maxEntries=1000" (1000 messages is default) */
   long maxEntriesDefault_;

   /** The max setting allowed for queue maxEntriesCache is adjustable with property "queue/maxEntriesCache=1000" (1000 messages is default) */
   long maxEntriesCacheDefault_;

   /** The max setting allowed for queue maxBytes in Bytes is adjustable with property "queue/maxBytes=4194304" (4 MBytes is default) */
   long maxBytesDefault_;

   /** The max setting allowed for queue maxBytesCache in Bytes is adjustable with property "queue/maxBytesCache=4000" (4 MBytes is default) */
   long maxBytesCacheDefault_;

   /** The min span of life is one second, changeable with property e.g. "queue/expires.min=2000" milliseconds */
   Timestamp minExpires_;

   /** The max span of life of a queue is currently forever (=0), changeable with property e.g. "queue/expires.max=3600000" milliseconds */
   Timestamp maxExpires_;


   /** The unique protocol relating, e.g. "IOR" */
   std::string relating_; //  = Constants.RELATING_CALLBACK;
   /** Span of life of this queue in milliseconds */
   Timestamp expires_; // = DEFAULT_expires;
   /** The max. capacity of the queue in number of entries */
   long maxEntries_;
   /** The max. capacity of the queue in Bytes */
   long maxBytes_;
   /** The max. capacity of the cache of the queue in number of entries */
   long maxEntriesCache_;

   /** The settings for the storeSwapLevel */
   long storeSwapLevel_;

   /** The settings for the storeSwapBytes */
   long storeSwapBytes_;

   /** The settings for the storeSwapLevel */
   long reloadSwapLevel_;

   /** The settings for the storeSwapBytes */
   long reloadSwapBytes_;

   /** The max. capacity of the queue in Bytes for the cache */
   long maxBytesCache_;

   /** Error handling when queue is full: Constants.ONOVERFLOW_DEADMESSAGE | Constants.ONOVERFLOW_DISCARDOLDEST */
   std::string onOverflow_;

   /** Error handling when callback failed (after all retries etc.): Constants.ONOVERFLOW_DEADMESSAGE */
   std::string onFailure_;

   /** The corresponding callback address */
   AddressVector addressArr_; // = new org::xmlBlaster::util::qos::address::AddressBase[0];

   /** To allow specific configuration parameters for specific cluster nodes */
   std::string nodeId_; // = null;

   /** Relating "connection", "history", "callback" etc. */
   std::string propertyPrefix_;

   std::string rootTagName_;

   void copy(const QueuePropertyBase& prop)
   {
      type_                 = prop.type_;
      version_              = prop.version_;
      maxEntriesDefault_    = prop.maxEntriesDefault_;
      maxEntriesCacheDefault_= prop.maxEntriesCacheDefault_;
      maxBytesDefault_      = prop.maxBytesDefault_;
      maxBytesCacheDefault_ = prop.maxBytesCacheDefault_;
      minExpires_           = prop.maxExpires_;
      relating_             = prop.relating_;
      expires_              = prop.expires_;
      maxEntries_           = prop.maxEntries_;
      maxBytes_             = prop.maxBytes_;
      maxEntriesCache_      = prop.maxEntriesCache_;
      storeSwapLevel_       = prop.storeSwapLevel_;
      storeSwapBytes_       = prop.storeSwapBytes_;
      reloadSwapLevel_      = prop.reloadSwapLevel_;
      reloadSwapBytes_      = prop.reloadSwapBytes_;
      maxBytesCache_        = prop.maxBytesCache_;
      onOverflow_           = prop.onOverflow_;
      onFailure_            = prop.onFailure_;
      addressArr_           = prop.addressArr_;
      nodeId_               = prop.nodeId_;
      propertyPrefix_       = prop.propertyPrefix_;
      rootTagName_          = prop.rootTagName_;
   }

   /**
    * Configure property settings, add your own defaults in the derived class
    */
   /*inline*/ void initialize(const std::string& propertyPrefix);

   void setMaxExpires(Timestamp maxExpires)
   {
      maxExpires_ = maxExpires;
   }

   Timestamp getMaxExpires() const
   {
      return maxExpires_;
   }

   void setMinExpires(Timestamp minExpires)
   {
      minExpires_ = minExpires;
   }

   Timestamp getMinExpires()
   {
      return minExpires_;
   }

public:

   /**
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue/maxEntries and -queue/maxEntries[heron] will be searched
    */
   QueuePropertyBase(org::xmlBlaster::util::Global& global, const std::string& nodeId);

   QueuePropertyBase(const QueuePropertyBase& prop);

   QueuePropertyBase& operator =(const QueuePropertyBase& prop);

   virtual ~QueuePropertyBase();

   /**
    * @param relating    To what is this queue related: Constants.RELATING_CALLBACK | Constants.RELATING_SUBJECT | Constants.RELATING_CLIENT
    */
   void setRelating(const std::string& relating);

   /**
    * Returns the queue type.
    * @return relating    To what is this queue related: Constants.RELATING_CALLBACK | Constants.RELATING_SUBJECT
    */
   std::string getRelating() const;

   /**
    * Span of life of this queue.
    * @return Expiry time in milliseconds or 0L if forever
    */
   Timestamp getExpires() const;

   /**
    * Span of life of this queue.
    * @param Expiry time in milliseconds
    */
   void setExpires(Timestamp expires);

   /**
    * Max number of messages for this queue.
    * <br />
    * @return number of messages
    */
   long getMaxEntries() const;

   /**
    * Max number of messages for this queue.
    * <br />
    * @param maxEntries
    */
   void setMaxEntries(long maxEntries);

   /**
    * The plugin type. 
    * <br />
    * @return e.g. "CACHE"
    */
   std::string getType() const;

   /**
    * The plugin type
    * <br />
    * @param type
    */
   void setType(const std::string& type);

   /**
    * The plugin version. 
    * <br />
    * @return e.g. "1.0"
    */
   std::string getVersion() const;

   /**
    * The plugin version
    * <br />
    * @param version
    */
   void setVersion(const std::string& version);

   /**
    * Max number of messages for the cache of this queue.
    * <br />
    * @return number of messages
    */
   long getMaxEntriesCache() const;

   /**
    * Max number of messages for the cache of this queue.
    * <br />
    * @param maxEntries
    */
   void setMaxEntriesCache(long maxEntriesCache);

   /**
    * Max message queue size.
    * <br />
    * @return Get max. message queue size in Bytes
    */
   long getMaxBytes() const;

   /**
    * Max message queue size.
    * <br />
    * @return Set max. message queue size in Bytes
    */
   void setMaxBytes(long maxBytes);

   /**
    * Max message queue size for the cache of this queue.
    * <br />
    * @return Get max. message queue size in Bytes
    */
   long getMaxBytesCache() const;

   /**
    * Gets the storeSwapLevel for the queue (only used on cache queues).
    * <br />
    * @return Get storeSwapLevel in bytes.
    */
   long getStoreSwapLevel() const;

   /**
    * Sets the storeSwapLevel for the queue (only used on cache queues).
    * <br />
    * @param Set storeSwapLevel in bytes.
    */
   void setStoreSwapLevel(long storeSwapLevel);

   /**
    * Gets the storeSwapBytes for the queue (only used on cache queues).
    * <br />
    * @return Get storeSwapBytes in bytes.
    */
   long getStoreSwapBytes() const;

   /**
    * Sets the storeSwapBytes for the queue (only used on cache queues).
    * <br />
    * @param Set storeSwapBytes in bytes.
    */
   void setStoreSwapBytes(long storeSwapBytes);

   /**
    * Gets the reloadSwapLevel for the queue (only used on cache queues).
    * <br />
    * @return Get reloadSwapLevel in bytes.
    */
   long getReloadSwapLevel() const;

   /**
    * Sets the reloadSwapLevel for the queue (only used on cache queues).
    * <br />
    * @param Set reloadSwapLevel in bytes.
    */
   void setReloadSwapLevel(long reloadSwapLevel);

   /**
    * Gets the reloadSwapBytes for the queue (only used on cache queues).
    * <br />
    * @return Get reloadSwapBytes in bytes.
    */
   long getReloadSwapBytes() const;

   /**
    * Sets the reloadSwapBytes for the queue (only used on cache queues).
    * <br />
    * @param Set reloadSwapBytes in bytes.
    */
   void setReloadSwapBytes(long reloadSwapBytes);

   /**
    * Max message queue size for the cache of this queue.
    * <br />
    * @return Set max. message queue size in Bytes
    */
   void setMaxBytesCache(long maxBytesCache);

   /**
    * Set the callback onOverflow, it should fit to the protocol-relating.
    *
    * @param onOverflow The callback onOverflow, e.g. "et@mars.univers"
    */
   void setOnOverflow(const std::string& onOverflow);

   /**
    * Returns the onOverflow.
    * @return e.g. "IOR:00001100022...." or "et@universe.com"
    */
   std::string getOnOverflow() const;

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
   void setOnFailure(const std::string& onFailure);

   /**
    * Returns the onFailure.
    * @return e.g. "IOR:00001100022...." or "et@universe.com"
    */
   std::string getOnFailure() const;

   /**
    * The default mode is to send a dead letter if callback fails permanently
    */
   bool onFailureDeadMessage();

   /**
    * Access all addresses, they are reference counted.
    * @return null if none available
    */
   AddressVector getAddresses() const;

   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   std::string toXml(const std::string& extraOffset="") const;

   /**
    * returns the global object
    */
   org::xmlBlaster::util::Global& getGlobal();

//   void cleanupAddresses();

   /** Relating "connection", "history", "callback" etc. */
   std::string getPropertyPrefix() const;
   void setpropertyPrefix(const std::string& prefix);

   /**
    * The command line prefix to configure the queue or msgUnitStore
    * @return e.g. "persistence/msgUnitStore/" or "queue/history/"
    */
   std::string getPrefix();

   /**
    * Helper for logging output, creates the property key for configuration (the command line property).
    * @param prop e.g. "maxEntries"
    * @return e.g. "-queue/history/maxEntries" or "-queue/history/maxEntriesCache"
    */
   std::string getPropName(const std::string& token);

   std::string getRootTagName() const;

};

}}}}} // namespaces

#endif
