/*------------------------------------------------------------------------------
Name:      QueuePropertyBase.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Version:   $Id$
------------------------------------------------------------------------------*/


/**
 * Helper class holding callback queue properties.
 * <p />
 * See ConnectQos for XML syntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */

#include <util/qos/storage/QueuePropertyBase.h>
#include <util/lexical_cast.h>
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos::address;

const long DEFAULT_maxEntriesDefault = 1000L;
const long DEFAULT_maxEntriesCacheDefault = 1000L;
const long DEFAULT_bytesDefault = 10485760L; // 10 MB
const long DEFAULT_bytesCacheDefault = 2097152L; // 2 MB
/** The default settings (as a ratio relative to the maxBytesCache) for the storeSwapLevel */
const double DEFAULT_storeSwapLevelRatio = 0.70;
/** The default settings (as a ratio relative to the maxBytesCache) for the storeSwapBytes */
const double DEFAULT_storeSwapBytesRatio = 0.25;
/** The default settings (as a ratio relative to the maxBytesCache) for the storeSwapLevel */
const double DEFAULT_reloadSwapLevelRatio = 0.30;
/** The default settings (as a ratio relative to the maxBytesCache) for the storeSwapBytes */
const double DEFAULT_reloadSwapBytesRatio = 0.25;
const Timestamp DEFAULT_minExpires = 1000;
const Timestamp DEFAULT_maxExpires = 0;
const string DEFAULT_onOverflow = Constants::ONOVERFLOW_DEADMESSAGE;
const string DEFAULT_onFailure = Constants::ONOVERFLOW_DEADMESSAGE;

// static variables
string DEFAULT_type = "CACHE";
string DEFAULT_version = "1.0";
/** If not otherwise noted a queue dies after the max value, changeable with property e.g. "queue.expires=3600000" milliseconds */
long DEFAULT_expires;


/**
 * Configure property settings, add your own defaults in the derived class
 * @param propertyPrefix e.g. "history" or "callback" or "connection" or ""
 */
void QueuePropertyBase::initialize(const string& propertyPrefix)
{
   //if (log_.call()) log_.call(ME, string("::initialize with property prefix '") + propertyPrefix + "'");
   propertyPrefix_ = propertyPrefix;
   string prefix = getPrefix();
   //if (log_.trace()) log_.trace(ME, string("::initialize: got the prefix '") + prefix + "'");

   // Do we need this range settings?
   setMinExpires(global_.getProperty().getTimestampProperty("queue/expires.min", DEFAULT_minExpires));
   setMaxExpires(global_.getProperty().getTimestampProperty("queue/expires.max", DEFAULT_maxExpires)); // Long.MAX_VALUE);
   //if (log_.trace()) log_.trace(ME, "::initialize: expires set");
   if (nodeId_ != "") {
      setMinExpires(global_.getProperty().getTimestampProperty("queue/expires.min["+nodeId_+"]", getMinExpires()));
      setMaxExpires(global_.getProperty().getTimestampProperty("queue/expires.max["+nodeId_+"]", getMaxExpires())); // Long.MAX_VALUE);
   }
   //if (log_.trace()) log_.trace(ME, "::initialize: expires for the specific node set");

   // prefix is e.g. "queue/history/" or "persistence/topicStore/"
   setMaxEntries(global_.getProperty().getLongProperty(prefix+"maxEntries", DEFAULT_maxEntriesDefault));
   //if (log_.trace()) log_.trace(ME, "::initialize: setMaxEntries -> " + lexical_cast<string>(getMaxEntries()));
   setMaxEntriesCache(global_.getProperty().getLongProperty(prefix+"maxEntriesCache", DEFAULT_maxEntriesCacheDefault));
   //if (log_.trace()) log_.trace(ME, "::initialize: setMaxEntriesCache -> " + lexical_cast<string>(getMaxEntriesCache()));
   setMaxBytes(global_.getProperty().getLongProperty(prefix+"maxBytes", DEFAULT_bytesDefault));
   //if (log_.trace()) log_.trace(ME, "::initialize: setMaxBytes -> " + lexical_cast<string>(getMaxBytes()));
   setMaxBytesCache(global_.getProperty().getLongProperty(prefix+"maxBytesCache", DEFAULT_bytesCacheDefault));
   //if (log_.trace()) log_.trace(ME, "::initialize: setMaxBytesCache -> " + lexical_cast<string>(getMaxBytesCache()));

   setStoreSwapLevel(global_.getProperty().getLongProperty(prefix+"storeSwapLevel", (long)(DEFAULT_storeSwapLevelRatio*maxBytesCache_)));
   setStoreSwapBytes(global_.getProperty().getLongProperty(prefix+"storeSwapBytes", (long)(DEFAULT_storeSwapBytesRatio*maxBytesCache_)));
   setReloadSwapLevel(global_.getProperty().getLongProperty(prefix+"reloadSwapLevel", (long)(DEFAULT_reloadSwapLevelRatio*maxBytesCache_)));
   setReloadSwapBytes(global_.getProperty().getLongProperty(prefix+"reloadSwapBytes", (long)(DEFAULT_reloadSwapBytesRatio*maxBytesCache_)));

   //if (log_.trace()) log_.trace(ME, "::initialize: values for the swap control set");

   setExpires(global_.getProperty().getTimestampProperty(prefix+"expires", DEFAULT_maxExpires));
   setOnOverflow(global_.getProperty().getStringProperty(prefix+"onOverflow", DEFAULT_onOverflow));
   setOnFailure(global_.getProperty().getStringProperty(prefix+"onFailure", DEFAULT_onFailure));
   setType(global_.getProperty().getStringProperty(prefix+"type", DEFAULT_type));
   setVersion(global_.getProperty().getStringProperty(prefix+"version", DEFAULT_version));

   //if (log_.trace()) log_.trace(ME, "::initialize: going to set specific node properties");

   if (nodeId_ != "") {
      setMaxEntries(global_.getProperty().getLongProperty(prefix+"maxEntries["+nodeId_+"]", getMaxEntries()));
      setMaxEntriesCache(global_.getProperty().getLongProperty(prefix+"maxEntriesCache["+nodeId_+"]", getMaxEntriesCache()));
      setMaxBytes(global_.getProperty().getLongProperty(prefix+"maxBytes["+nodeId_+"]", getMaxBytes()));
      setMaxBytesCache(global_.getProperty().getLongProperty(prefix+"maxBytesCache["+nodeId_+"]", getMaxBytesCache()));
      setStoreSwapLevel(global_.getProperty().getLongProperty(prefix+"storeSwapLevel["+nodeId_+"]", getStoreSwapLevel()));
      setStoreSwapBytes(global_.getProperty().getLongProperty(prefix+"storeSwapBytes["+nodeId_+"]", getStoreSwapBytes()));
      setReloadSwapLevel(global_.getProperty().getLongProperty(prefix+"reloadSwapLevel["+nodeId_+"]", getReloadSwapLevel()));
      setReloadSwapBytes(global_.getProperty().getLongProperty(prefix+"reloadSwapBytes["+nodeId_+"]", getReloadSwapBytes()));
      setExpires(global_.getProperty().getTimestampProperty(prefix+"expires["+nodeId_+"]", getExpires()));
      setOnOverflow(global_.getProperty().getStringProperty(prefix+"onOverflow["+nodeId_+"]", getOnOverflow()));
      setOnFailure(global_.getProperty().getStringProperty(prefix+"onFailure["+nodeId_+"]", getOnFailure()));
      setType(global_.getProperty().getStringProperty(prefix+"type["+nodeId_+"]", getType()));
      setVersion(global_.getProperty().getStringProperty(prefix+"version["+nodeId_+"]", getVersion()));
   }
   if (log_.trace()) log_.trace(ME, string("::initialized to: ") + toXml());
}

/*
   void QueuePropertyBase::initialize()
   {
      // Do we need this range settings?
      setMinExpires(global_.getProperty().getTimestampProperty("queue/expires.min", DEFAULT_minExpires));
      setMaxExpires(global_.getProperty().getTimestampProperty("queue/expires.max", DEFAULT_maxExpires)); // Long.MAX_VALUE);
      if (nodeId_ != "") {
         setMinExpires(global_.getProperty().getTimestampProperty(string("queue/expires.min[")+nodeId_+string("]"), getMinExpires()));
         setMaxExpires(global_.getProperty().getTimestampProperty(string("queue/expires.max[")+nodeId_+string("]"), getMaxExpires())); // Long.MAX_VALUE);
      }

//         PluginInfo pluginInfo = new PluginInfo(glob, null, global_.getProperty().get("queue/defaultPlugin", DEFAULT_type));
//         DEFAULT_type = pluginInfo.getType();
//         DEFAULT_version = pluginInfo.getVersion();
   }
*/


   QueuePropertyBase::QueuePropertyBase(Global& global, const string& nodeId)
      : ME("QueuePropertyBase"),
        global_(global),
        log_(global.getLog("org.xmlBlaster.util.qos")),
        type_(DEFAULT_type),
        version_(DEFAULT_version),
        minExpires_(DEFAULT_minExpires),
        maxExpires_(DEFAULT_minExpires),
        relating_(Constants::RELATING_CALLBACK),
        expires_(DEFAULT_expires),
        maxEntries_(DEFAULT_maxEntriesDefault),
        maxBytes_(DEFAULT_bytesDefault),
        maxEntriesCache_(DEFAULT_maxEntriesCacheDefault),
        storeSwapLevel_(0),
        storeSwapBytes_(0),
        reloadSwapLevel_(0),
        reloadSwapBytes_(0),
        maxBytesCache_(DEFAULT_bytesCacheDefault),
        onOverflow_(Constants::ONOVERFLOW_DEADMESSAGE),
        onFailure_(Constants::ONOVERFLOW_DEADMESSAGE),
        addressArr_(),
        nodeId_(nodeId),
        propertyPrefix_(""),
        rootTagName_("queue")
   {
   }

   QueuePropertyBase::QueuePropertyBase(const QueuePropertyBase& prop)
      : ME("QueuePropertyBase"), global_(prop.global_), log_(prop.log_)
   {
      copy(prop);
   }

   QueuePropertyBase&
   QueuePropertyBase::operator =(const QueuePropertyBase& prop)
   {
      copy(prop);
      return *this;
   }


   QueuePropertyBase::~QueuePropertyBase()
   {
      addressArr_.clear();
      // delete all entries of the address vector since they are pointers
      // owned by this object.
//      cleanupAddresses();
   }

   /**
    * @param relating    To what is this queue related: Constants.RELATING_CALLBACK | Constants.RELATING_SUBJECT | Constants.RELATING_CLIENT
    */
   void QueuePropertyBase::setRelating(const string& relating)
   {
      if (Constants::RELATING_CALLBACK == relating)
         relating_ = Constants::RELATING_CALLBACK;
      else if (Constants::RELATING_SUBJECT == relating)
         relating_ = Constants::RELATING_SUBJECT;
      else if (Constants::RELATING_CLIENT == relating)
         relating_ = Constants::RELATING_CLIENT;
      else if (Constants::RELATING_HISTORY == relating)
         relating_ = Constants::RELATING_HISTORY;
      else if (Constants::RELATING_MSGUNITSTORE == relating)
         relating_ = Constants::RELATING_MSGUNITSTORE;
      else if (Constants::RELATING_TOPICSTORE == relating)
         relating_ = Constants::RELATING_TOPICSTORE;
      else {
         log_.warn(ME, string("Ignoring relating=") + relating);
      }
   }

   /**
    * Returns the queue type.
    * @return relating    To what is this queue related: Constants.RELATING_CALLBACK | Constants.RELATING_SUBJECT
    */
   string QueuePropertyBase::getRelating() const
   {
      return relating_;
   }

   /**
    * Span of life of this queue.
    * @return Expiry time in milliseconds or 0L if forever
    */
   Timestamp QueuePropertyBase::getExpires() const
   {
      return expires_;
   }

   /**
    * Span of life of this queue.
    * @param Expiry time in milliseconds
    */
   void QueuePropertyBase::setExpires(Timestamp expires)
   {
      if (maxExpires_ <= 0) expires_ = expires;
      else if ( (expires>0) && (maxExpires_>0) && (expires>maxExpires_) )
         expires_ = maxExpires_;
      else if ( (expires<=0) && (maxExpires_>0) )
         expires_ = maxExpires_;

      if ( (expires>0) && (expires<minExpires_) )
         expires_ = minExpires_;
   }


   /**
    * Max number of messages for this queue.
    * <br />
    * @return number of messages
    */
   long QueuePropertyBase::getMaxEntries() const
   {
      return maxEntries_;
   }

   /**
    * Max number of messages for this queue.
    * <br />
    * @param maxEntries
    */
   void QueuePropertyBase::setMaxEntries(long maxEntries)
   {
      maxEntries_ = maxEntries;
   }


   /**
    * The plugin type. 
    * <br />
    * @return e.g. "CACHE"
    */
   string QueuePropertyBase::getType() const
   {
      return type_;
   }

   /**
    * The plugin type
    * <br />
    * @param type
    */
   void QueuePropertyBase::setType(const string& type)
   {
      type_ = type;
   }

   /**
    * The plugin version. 
    * <br />
    * @return e.g. "1.0"
    */
   string QueuePropertyBase::getVersion() const
   {
      return version_;
   }

   /**
    * The plugin version
    * <br />
    * @param version
    */
   void QueuePropertyBase::setVersion(const string& version)
   {
      version_ = version;
   }

   /**
    * Max number of messages for the cache of this queue.
    * <br />
    * @return number of messages
    */
   long QueuePropertyBase::getMaxEntriesCache() const
   {
      return maxEntriesCache_;
   }

   /**
    * Max number of messages for the cache of this queue.
    * <br />
    * @param maxEntries
    */
   void QueuePropertyBase::setMaxEntriesCache(long maxEntriesCache)
   {
      maxEntriesCache_ = maxEntriesCache;
   }


   /**
    * Max message queue size.
    * <br />
    * @return Get max. message queue size in Bytes
    */
   long QueuePropertyBase::getMaxBytes() const
   {
      return maxBytes_;
   }

   /**
    * Max message queue size.
    * <br />
    * @return Set max. message queue size in Bytes
    */
   void QueuePropertyBase::setMaxBytes(long maxBytes)
   {
      maxBytes_ = maxBytes;
   }


   /**
    * Max message queue size for the cache of this queue.
    * <br />
    * @return Get max. message queue size in Bytes
    */
   long QueuePropertyBase::getMaxBytesCache() const
   {
      return maxBytesCache_;
   }


   /**
    * Gets the storeSwapLevel for the queue (only used on cache queues).
    * <br />
    * @return Get storeSwapLevel in bytes.
    */
   long QueuePropertyBase::getStoreSwapLevel() const
   {
      return storeSwapLevel_;
   }

   /**
    * Sets the storeSwapLevel for the queue (only used on cache queues).
    * <br />
    * @param Set storeSwapLevel in bytes.
    */
   void QueuePropertyBase::setStoreSwapLevel(long storeSwapLevel)
   {
      storeSwapLevel_ = storeSwapLevel;
   }

   /**
    * Gets the storeSwapBytes for the queue (only used on cache queues).
    * <br />
    * @return Get storeSwapBytes in bytes.
    */
   long QueuePropertyBase::getStoreSwapBytes() const
   {
      return storeSwapBytes_;
   }

   /**
    * Sets the storeSwapBytes for the queue (only used on cache queues).
    * <br />
    * @param Set storeSwapBytes in bytes.
    */
   void QueuePropertyBase::setStoreSwapBytes(long storeSwapBytes)
   {
      storeSwapBytes_ = storeSwapBytes;
   }

   /**
    * Gets the reloadSwapLevel for the queue (only used on cache queues).
    * <br />
    * @return Get reloadSwapLevel in bytes.
    */
   long QueuePropertyBase::getReloadSwapLevel() const
   {
      return reloadSwapLevel_;
   }

   /**
    * Sets the reloadSwapLevel for the queue (only used on cache queues).
    * <br />
    * @param Set reloadSwapLevel in bytes.
    */
   void QueuePropertyBase::setReloadSwapLevel(long reloadSwapLevel)
   {
      reloadSwapLevel_ = reloadSwapLevel;
   }

   /**
    * Gets the reloadSwapBytes for the queue (only used on cache queues).
    * <br />
    * @return Get reloadSwapBytes in bytes.
    */
   long QueuePropertyBase::getReloadSwapBytes() const
   {
      return reloadSwapBytes_;
   }

   /**
    * Sets the reloadSwapBytes for the queue (only used on cache queues).
    * <br />
    * @param Set reloadSwapBytes in bytes.
    */
   void QueuePropertyBase::setReloadSwapBytes(long reloadSwapBytes)
   {
      reloadSwapBytes_ = reloadSwapBytes;
   }

   /**
    * Max message queue size for the cache of this queue.
    * <br />
    * @return Set max. message queue size in Bytes
    */
   void QueuePropertyBase::setMaxBytesCache(long maxBytesCache)
   {
      maxBytesCache_ = maxBytesCache;
   }


   /**
    * Set the callback onOverflow, it should fit to the protocol-relating.
    *
    * @param onOverflow The callback onOverflow, e.g. "et@mars.univers"
    */
   void QueuePropertyBase::setOnOverflow(const string& onOverflow)
   {
      /*
      if (Constants.ONOVERFLOW_BLOCK.equalsIgnoreCase(onOverflow)) {
         this.onOverflow = Constants.ONOVERFLOW_BLOCK;
      }
      */
      if (Constants::ONOVERFLOW_DEADMESSAGE == onOverflow) {
         onOverflow_ = Constants::ONOVERFLOW_DEADMESSAGE;
      }
      else if (Constants::ONOVERFLOW_DISCARDOLDEST == onOverflow) {
         onOverflow_ = Constants::ONOVERFLOW_DISCARDOLDEST;

         onOverflow_ = Constants::ONOVERFLOW_DEADMESSAGE; // TODO !!!
         log_.error(ME, string("queue onOverflow='") + string(Constants::ONOVERFLOW_DISCARDOLDEST) + string("' is not implemented, switching to ") + onOverflow_ + string(" mode"));
      }
      else {
         onOverflow_ = Constants::ONOVERFLOW_DEADMESSAGE;
         log_.warn(ME, string("The queue onOverflow attribute is invalid '") + onOverflow + string("', setting to '") + onOverflow_ + string("'"));
      }
   }

   /**
    * Returns the onOverflow.
    * @return e.g. "IOR:00001100022...." or "et@universe.com"
    */
   string QueuePropertyBase::getOnOverflow() const
   {
      return onOverflow_;
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
   void QueuePropertyBase::setOnFailure(const string& onFailure)
   {
      if (Constants::ONOVERFLOW_DEADMESSAGE == onFailure)
         onFailure_ = Constants::ONOVERFLOW_DEADMESSAGE;
      else {
         log_.warn(ME, string("The queue onFailure attribute is invalid '") + onFailure + string("', setting to 'deadMessage'"));
         onFailure_ = Constants::ONOVERFLOW_DEADMESSAGE;
      }
   }

   /**
    * Returns the onFailure.
    * @return e.g. "IOR:00001100022...." or "et@universe.com"
    */
   string QueuePropertyBase::getOnFailure() const
   {
      return onFailure_;
   }

   /**
    * The default mode is to send a dead letter if callback fails permanently
    */
   bool QueuePropertyBase::onFailureDeadMessage()
   {
      if (Constants::ONOVERFLOW_DEADMESSAGE == getOnFailure())
         return true;
      return false;
   }

   /**
    * Access the reference counted addresses. 
    * @return null if none available
    */
   AddressVector QueuePropertyBase::getAddresses() const
   {
      return addressArr_;
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   string QueuePropertyBase::toXml(const string& extraOffset) const
   {
      string offset = Constants::OFFSET + extraOffset;
      string ret;   
      ret += offset + string("<!-- QueuePropertyBase -->");

      ret += offset + string("<queue relating='") + getRelating();
      if (DEFAULT_type != getType())
         ret += string("' type='") + getType();
      if (DEFAULT_version != getVersion())
         ret += string("' version='") + getVersion();
      if (DEFAULT_maxEntriesDefault != getMaxEntries())
         ret += string("' maxEntries='") + lexical_cast<std::string>(getMaxEntries());
      if (DEFAULT_maxEntriesCacheDefault != getMaxEntriesCache())
         ret += string("' maxEntriesCache='") + lexical_cast<std::string>(getMaxEntriesCache());
      if (DEFAULT_bytesDefault != getMaxBytes())
         ret += string("' maxBytes='") + lexical_cast<std::string>(getMaxBytes());
      if (DEFAULT_bytesCacheDefault != getMaxBytesCache())
         ret += string("' maxBytesCache='") + lexical_cast<std::string>(getMaxBytesCache());
      ret += string("' storeSwapLevel='") + lexical_cast<std::string>(getStoreSwapLevel());
      ret += string("' storeSwapBytes='") + lexical_cast<std::string>(getStoreSwapBytes());
      ret += string("' reloadSwapLevel='") + lexical_cast<std::string>(getReloadSwapLevel());
      ret += string("' reloadSwapBytes='") + lexical_cast<std::string>(getReloadSwapBytes());
      if (DEFAULT_expires != getExpires())
         ret += string("' expires='") + lexical_cast<std::string>(getExpires());
      if (DEFAULT_onOverflow != getOnOverflow())
         ret += string("' onOverflow='") + getOnOverflow();
      if (DEFAULT_onFailure != getOnFailure())
         ret += string("' onFailure='") + getOnFailure();

      if (!addressArr_.empty()) {
         ret += string("'>");
         AddressVector::const_iterator iter = addressArr_.begin();
         while (iter != addressArr_.end()) {
            ret += (*iter)->toXml(extraOffset + Constants::INDENT);
            iter++;
         }
         ret += offset + string("</queue>");

      }
      else
         ret += string("'/>");
      return ret;
   }

   /**
    * returns the global object
    */
   Global& QueuePropertyBase::getGlobal()
   {
      return global_;
   }

/*
   void QueuePropertyBase::cleanupAddresses()
   {
      AddressVector::iterator iter = addressArr_.begin();
      while (iter != addressArr_.end()) {
        AddressBase* el = *iter;
        addressArr_.erase(iter);
        delete el;
        iter = addressArr_.begin();
      }
   }
*/

   string QueuePropertyBase::getPropertyPrefix() const
   {
      return propertyPrefix_;
   }

   void QueuePropertyBase::setpropertyPrefix(const string& prefix)
   {
      propertyPrefix_ = prefix;
   }

   /**
    * The command line prefix to configure the queue or msgUnitStore
    * @return e.g. "queue/history/" or "persistence/msgUnitStore/"
    */
   string QueuePropertyBase::getPrefix()
   {
      return (propertyPrefix_.length() > 0) ?
                   getRootTagName()+"/"+propertyPrefix_+"/" :
                   getRootTagName()+"/";
   }

   /**
    * Helper for logging output, creates the property key for configuration (the command line property).
    * @param prop e.g. "maxEntries"
    * @return e.g. "-queue/history/maxEntries" or "-queue/history/maxEntriesCache" or "-persistence/msgUnitStore/maxEntries"
    */
   string QueuePropertyBase::getPropName(const string& token)
   {
      return "-" + getPrefix() + token;
   }

   string QueuePropertyBase::getRootTagName() const
   {
      return rootTagName_;
   }


}}}}} // namespaces


