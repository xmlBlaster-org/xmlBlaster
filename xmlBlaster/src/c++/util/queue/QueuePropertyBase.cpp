/*------------------------------------------------------------------------------
Name:      QueuePropertyBase.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Version:   $Id: QueuePropertyBase.cpp,v 1.6 2002/12/16 14:26:56 laghi Exp $
------------------------------------------------------------------------------*/


/**
 * Helper class holding callback queue properties.
 * <p />
 * See ConnectQos for XML syntax.
 * @see org.xmlBlaster.util.ConnectQos
 */

#include <util/queue/QueuePropertyBase.h>
#include <boost/lexical_cast.hpp>
#include <util/Global.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cfg;
using boost::lexical_cast;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

Dll_Export const long DEFAULT_maxMsgDefault = 1000l;
Dll_Export const long DEFAULT_maxMsgCacheDefault = 1000l;
Dll_Export const long DEFAULT_sizeDefault = 10485760l; // 10 MB
Dll_Export const long DEFAULT_sizeCacheDefault = 2097152l; // 2 MB
/** The default settings (as a ratio relative to the maxSizeCache) for the storeSwapLevel */
Dll_Export const double DEFAULT_storeSwapLevelRatio = 0.70;
/** The default settings (as a ratio relative to the maxSizeCache) for the storeSwapSize */
Dll_Export const double DEFAULT_storeSwapSizeRatio = 0.25;
/** The default settings (as a ratio relative to the maxSizeCache) for the storeSwapLevel */
Dll_Export const double DEFAULT_reloadSwapLevelRatio = 0.30;
/** The default settings (as a ratio relative to the maxSizeCache) for the storeSwapSize */
Dll_Export const double DEFAULT_reloadSwapSizeRatio = 0.25;
Dll_Export const Timestamp DEFAULT_minExpires = 1000;
Dll_Export const Timestamp DEFAULT_maxExpires = 0;
Dll_Export const string DEFAULT_onOverflow = Constants::ONOVERFLOW_DEADMESSAGE;
Dll_Export const string DEFAULT_onFailure = Constants::ONOVERFLOW_DEADMESSAGE;

// static variables
Dll_Export string DEFAULT_type = "CACHE";
Dll_Export string DEFAULT_version = "1.0";
/** If not otherwise noted a queue dies after the max value, changeable with property e.g. "queue.expires=3600000" milliseconds */
Dll_Export long DEFAULT_expires;


   void QueuePropertyBase::initialize()
   {
      // Do we need this range settings?
      setMinExpires(global_.getProperty().getTimestampProperty("queue.expires.min", DEFAULT_minExpires));
      setMaxExpires(global_.getProperty().getTimestampProperty("queue.expires.max", DEFAULT_maxExpires)); // Long.MAX_VALUE);
      if (nodeId_ != "") {
         setMinExpires(global_.getProperty().getTimestampProperty(string("queue.expires.min[")+nodeId_+string("]"), getMinExpires()));
         setMaxExpires(global_.getProperty().getTimestampProperty(string("queue.expires.max[")+nodeId_+string("]"), getMaxExpires())); // Long.MAX_VALUE);
      }

//         PluginInfo pluginInfo = new PluginInfo(glob, null, global_.getProperty().get("queue.defaultPlugin", DEFAULT_type));
//         DEFAULT_type = pluginInfo.getType();
//         DEFAULT_version = pluginInfo.getVersion();
   }



   QueuePropertyBase::QueuePropertyBase(Global& global, const string& nodeId)
      : ME("QueuePropertyBase"), global_(global), log_(global.getLog("core")),
        addressArr_()
   {
      nodeId_ = nodeId;
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
      // delete all entries of the address vector since they are pointers
      // owned by this object.
//      cleanupAddresses();
   }

   /**
    * @param relating    To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT | Constants.RELATING_CLIENT
    */
   void QueuePropertyBase::setRelating(const string& relating)
   {
      if (Constants::RELATING_SESSION == relating)
         relating_ = Constants::RELATING_SESSION;
      else if (Constants::RELATING_SUBJECT == relating)
         relating_ = Constants::RELATING_SUBJECT;
      else if (Constants::RELATING_CLIENT ==relating)
         relating_ = Constants::RELATING_CLIENT;
      else {
         log_.warn(ME, string("Ignoring relating=") + relating);
      }
   }

   /**
    * Returns the queue type.
    * @return relating    To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT
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
   long QueuePropertyBase::getMaxMsg() const
   {
      return maxMsg_;
   }

   /**
    * Max number of messages for this queue.
    * <br />
    * @param maxMsg
    */
   void QueuePropertyBase::setMaxMsg(long maxMsg)
   {
      maxMsg_ = maxMsg;
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
   long QueuePropertyBase::getMaxMsgCache() const
   {
      return maxMsgCache_;
   }

   /**
    * Max number of messages for the cache of this queue.
    * <br />
    * @param maxMsg
    */
   void QueuePropertyBase::setMaxMsgCache(long maxMsgCache)
   {
      maxMsgCache_ = maxMsgCache;
   }


   /**
    * Max message queue size.
    * <br />
    * @return Get max. message queue size in Bytes
    */
   long QueuePropertyBase::getMaxSize() const
   {
      return maxSize_;
   }

   /**
    * Max message queue size.
    * <br />
    * @return Set max. message queue size in Bytes
    */
   void QueuePropertyBase::setMaxSize(long maxSize)
   {
      maxSize_ = maxSize;
   }


   /**
    * Max message queue size for the cache of this queue.
    * <br />
    * @return Get max. message queue size in Bytes
    */
   long QueuePropertyBase::getMaxSizeCache() const
   {
      return maxSizeCache_;
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
    * Gets the storeSwapSize for the queue (only used on cache queues).
    * <br />
    * @return Get storeSwapSize in bytes.
    */
   long QueuePropertyBase::getStoreSwapSize() const
   {
      return storeSwapSize_;
   }

   /**
    * Sets the storeSwapSize for the queue (only used on cache queues).
    * <br />
    * @param Set storeSwapSize in bytes.
    */
   void QueuePropertyBase::setStoreSwapSize(long storeSwapSize)
   {
      storeSwapSize_ = storeSwapSize;
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
    * Gets the reloadSwapSize for the queue (only used on cache queues).
    * <br />
    * @return Get reloadSwapSize in bytes.
    */
   long QueuePropertyBase::getReloadSwapSize() const
   {
      return reloadSwapSize_;
   }

   /**
    * Sets the reloadSwapSize for the queue (only used on cache queues).
    * <br />
    * @param Set reloadSwapSize in bytes.
    */
   void QueuePropertyBase::setReloadSwapSize(long reloadSwapSize)
   {
      reloadSwapSize_ = reloadSwapSize;
   }

   /**
    * Max message queue size for the cache of this queue.
    * <br />
    * @return Set max. message queue size in Bytes
    */
   void QueuePropertyBase::setMaxSizeCache(long maxSizeCache)
   {
      maxSizeCache_ = maxSizeCache;
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
   string QueuePropertyBase::toXml(const string& extraOffset)
   {
      string offset = "\n   ";
      string ret;
      ret += offset + string("<!-- QueuePropertyBase -->");

      ret += offset + string("<queue relating='") + getRelating();
      if (DEFAULT_type != getType())
         ret += string("' type='") + getType();
      if (DEFAULT_version != getVersion())
         ret += string("' version='") + getVersion();
      if (DEFAULT_maxMsgDefault != getMaxMsg())
         ret += string("' maxMsg='") + lexical_cast<string>(getMaxMsg());
      if (DEFAULT_maxMsgCacheDefault != getMaxMsgCache())
         ret += string("' maxMsgCache='") + lexical_cast<string>(getMaxMsgCache());
      if (DEFAULT_sizeDefault != getMaxSize())
         ret += string("' maxSize='") + lexical_cast<string>(getMaxSize());
      if (DEFAULT_sizeCacheDefault != getMaxSizeCache())
         ret += string("' maxSizeCache='") + lexical_cast<string>(getMaxSizeCache());
      ret += string("' storeSwapLevel='") + lexical_cast<string>(getStoreSwapLevel());
      ret += string("' storeSwapSize='") + lexical_cast<string>(getStoreSwapSize());
      ret += string("' reloadSwapLevel='") + lexical_cast<string>(getReloadSwapLevel());
      ret += string("' reloadSwapSize='") + lexical_cast<string>(getReloadSwapSize());
      if (DEFAULT_expires != getExpires())
         ret += string("' expires='") + lexical_cast<string>(getExpires());
      if (DEFAULT_onOverflow != getOnOverflow())
         ret += string("' onOverflow='") + getOnOverflow();
      if (DEFAULT_onFailure != getOnFailure())
         ret += string("' onFailure='") + getOnFailure();

      if (!addressArr_.empty()) {
         ret += string("'>");
         AddressVector::iterator iter = addressArr_.begin();
         while (iter != addressArr_.end()) {
            ret += (*iter).toXml(extraOffset + "   ");
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

}}}} // namespaces


