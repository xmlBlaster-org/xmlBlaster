/*------------------------------------------------------------------------------
Name:      CbQueueProperty.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Version:   $Id: CbQueueProperty.h,v 1.2 2002/12/09 13:00:35 laghi Exp $
------------------------------------------------------------------------------*/

/**
 * Helper class holding callback queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.util.ConnectQos
 */

#ifndef _UTIL_QUEUE_CBQUEUEPROPERTY_H
#define _UTIL_QUEUE_CBQUEUEPROPERTY_H

#include <util/xmlBlasterDef.h>
#include <util/Global.h>
#include <util/Log.h>
#include <util/Constants.h>
#include <util/queue/QueuePropertyBase.h>
#include <util/cfg/CallbackAddress.h>

#include <string>
#include <vector>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cfg;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

class CbQueueProperty : public QueuePropertyBase
{
protected:

   /**
    * Configure property settings
    */
   void initialize()
   {
      QueuePropertyBase::initialize();

      // Set the queue properties
      setMaxMsg(global_.getProperty().getLongProperty("cb.queue.maxMsg", DEFAULT_maxMsgDefault));
      setMaxSize(global_.getProperty().getLongProperty("cb.queue.maxSize", DEFAULT_sizeDefault));
      setMaxMsgCache(global_.getProperty().getLongProperty("cb.queue.maxMsgCache", DEFAULT_maxMsgCacheDefault));
      setMaxSizeCache(global_.getProperty().getLongProperty("cb.queue.maxSizeCache", DEFAULT_sizeCacheDefault));
      setStoreSwapLevel(global_.getProperty().getLongProperty("cb.queue.storeSwapLevel", (long)(DEFAULT_storeSwapLevelRatio * maxSizeCache_)));
      setStoreSwapSize(global_.getProperty().getLongProperty("cb.queue.storeSwapSize", (long)(DEFAULT_storeSwapSizeRatio * maxSizeCache_)));
      setReloadSwapLevel(global_.getProperty().getLongProperty("cb.queue.reloadSwapLevel", (long)(DEFAULT_reloadSwapLevelRatio * maxSizeCache_)));
      setReloadSwapSize(global_.getProperty().getLongProperty("cb.queue.reloadSwapSize", (long)(DEFAULT_reloadSwapSizeRatio * maxSizeCache_)));
      setExpires(global_.getProperty().getTimestampProperty("cb.queue.expires", DEFAULT_maxExpires));
      setOnOverflow(global_.getProperty().getStringProperty("cb.queue.onOverflow", DEFAULT_onOverflow));
      setOnFailure(global_.getProperty().getStringProperty("cb.queue.onFailure", DEFAULT_onFailure));
      setType(global_.getProperty().getStringProperty("cb.queue.type", DEFAULT_type));
      setVersion(global_.getProperty().getStringProperty("cb.queue.version", DEFAULT_version));
      if (nodeId_ != "") {
         setMaxMsg(global_.getProperty().getLongProperty(string("cb.queue.maxMsg[")+nodeId_+string("]"), getMaxMsg()));
         setMaxSize(global_.getProperty().getLongProperty(string("cb.queue.maxSize[")+nodeId_+string("]"), getMaxSize()));
         setExpires(global_.getProperty().getTimestampProperty(string("cb.queue.expires[")+nodeId_+string("]"), getExpires()));
         setOnOverflow(global_.getProperty().getStringProperty(string("cb.queue.onOverflow[")+nodeId_+string("]"), getOnOverflow()));
         setOnFailure(global_.getProperty().getStringProperty(string("cb.queue.onFailure[")+nodeId_+string("]"), getOnFailure()));
         setType(global_.getProperty().getStringProperty(string("cb.queue.type[")+nodeId_+string("]"), getType()));
         setVersion(global_.getProperty().getStringProperty(string("cb.queue.version[")+nodeId_+string("]"), getVersion()));
      }
   }

   /**
    * @param relating  To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT
    * @param nodeId    If not null, the command line properties will look for prop[nodeId] as well,
    * e.g. -queue.maxMsg and -queue.maxMsg[heron] will be searched
    */
public:
   CbQueueProperty(Global& global, const string& relating, const string& nodeId);

   CbQueueProperty(const QueuePropertyBase& prop);

   CbQueueProperty& operator =(const QueuePropertyBase& prop);

   /**
    * Show some important settings for logging
    */
   string getSettings();

   /**
    * This method converts a string to lowercase. Note that the input string is
    * modified and a reference to it is returned.
    */
   static string& toLowerCase(string& ref);

   /**
    * @param relating    To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT
    */
   void setRelating(const string& relating);

   bool isSubjectRelated();

   bool isSessionRelated();

   bool onOverflowDeadMessage();

   /**
    * Currently only one address is allowed, failover addresses will be implemented in a future version
    */
   void setCallbackAddress(const CallbackAddress& address);

   /**
    */
   void setCallbackAddresses(const vector<CallbackAddress>& addresses);

   /**
    * @return array with size 0 if none available
    */
   vector<CallbackAddress> getCallbackAddresses();

   /**
    * @return null if none available
    */
   CallbackAddress* getCurrentCallbackAddress();

   /**
    * Get a usage string for the connection parameters
    */
   string usage();
};

}}}} // namespace

#endif
