/*------------------------------------------------------------------------------
Name:      CbQueueProperty.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Version:   $Id: CbQueueProperty.cpp,v 1.5 2003/01/17 13:07:21 ruff Exp $
------------------------------------------------------------------------------*/

/**
 * Helper class holding callback queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */

#include <util/qos/storage/CbQueueProperty.h>
#include <boost/lexical_cast.hpp>
#include <ctype.h> // for toUpper
#include <util/Global.h>

using namespace boost;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos::address;

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

   inline void CbQueueProperty::initialize()
   {
      QueuePropertyBase::initialize("callback");

      // Set the queue properties
/*
      setMaxMsg(global_.getProperty().getLongProperty("cb.queue.maxMsg", DEFAULT_maxMsgDefault));
      setMaxBytes(global_.getProperty().getLongProperty("cb.queue.maxBytes", DEFAULT_bytesDefault));
      setMaxMsgCache(global_.getProperty().getLongProperty("cb.queue.maxMsgCache", DEFAULT_maxMsgCacheDefault));
      setMaxBytesCache(global_.getProperty().getLongProperty("cb.queue.maxBytesCache", DEFAULT_bytesCacheDefault));
      setStoreSwapLevel(global_.getProperty().getLongProperty("cb.queue.storeSwapLevel", (long)(DEFAULT_storeSwapLevelRatio * maxBytesCache_)));
      setStoreSwapBytes(global_.getProperty().getLongProperty("cb.queue.storeSwapBytes", (long)(DEFAULT_storeSwapBytesRatio * maxBytesCache_)));
      setReloadSwapLevel(global_.getProperty().getLongProperty("cb.queue.reloadSwapLevel", (long)(DEFAULT_reloadSwapLevelRatio * maxBytesCache_)));
      setReloadSwapBytes(global_.getProperty().getLongProperty("cb.queue.reloadSwapBytes", (long)(DEFAULT_reloadSwapBytesRatio * maxBytesCache_)));
      setExpires(global_.getProperty().getTimestampProperty("cb.queue.expires", DEFAULT_maxExpires));
      setOnOverflow(global_.getProperty().getStringProperty("cb.queue.onOverflow", DEFAULT_onOverflow));
      setOnFailure(global_.getProperty().getStringProperty("cb.queue.onFailure", DEFAULT_onFailure));
      setType(global_.getProperty().getStringProperty("cb.queue.type", DEFAULT_type));
      setVersion(global_.getProperty().getStringProperty("cb.queue.version", DEFAULT_version));
      if (nodeId_ != "") {
         setMaxMsg(global_.getProperty().getLongProperty(string("cb.queue.maxMsg[")+nodeId_+string("]"), getMaxMsg()));
         setMaxBytes(global_.getProperty().getLongProperty(string("cb.queue.maxBytes[")+nodeId_+string("]"), getMaxBytes()));
         setExpires(global_.getProperty().getTimestampProperty(string("cb.queue.expires[")+nodeId_+string("]"), getExpires()));
         setOnOverflow(global_.getProperty().getStringProperty(string("cb.queue.onOverflow[")+nodeId_+string("]"), getOnOverflow()));
         setOnFailure(global_.getProperty().getStringProperty(string("cb.queue.onFailure[")+nodeId_+string("]"), getOnFailure()));
         setType(global_.getProperty().getStringProperty(string("cb.queue.type[")+nodeId_+string("]"), getType()));
         setVersion(global_.getProperty().getStringProperty(string("cb.queue.version[")+nodeId_+string("]"), getVersion()));
      }
*/
   }

   CbQueueProperty::CbQueueProperty(Global& global,
                                    const string& relating,
                                    const string& nodeId)
                                  : QueuePropertyBase(global, global.getLog("dispatch"), nodeId)
   {
      ME = "CbQueueProperty";
      initialize();
      setRelating(relating);
   }

   CbQueueProperty::CbQueueProperty(const QueuePropertyBase& prop)
      : QueuePropertyBase(prop)
   {
   }

   CbQueueProperty&
   CbQueueProperty::operator =(const QueuePropertyBase& prop)
   {
      copy(prop);
      return *this;
   }

   /**
    * Show some important settings for logging
    */
   string CbQueueProperty::getSettings()
   {
      string ret;
      ret += string("type=") + getType() + string(" onOverflow=") +
             getOnOverflow() + string(" onFailure=") + getOnFailure() +
             string(" maxMsg=") + lexical_cast<string>(getMaxMsg());
      if (!addressArr_.empty())
         ret += string(" ") + getCurrentCallbackAddress().getSettings();
      return ret;
   }


   /**
    * This method converts a string to lowercase. Note that the input string is
    * modified and a reference to it is returned.
    */
   string& CbQueueProperty::toLowerCase(string& ref)
   {
      string::iterator iter = ref.begin();
      while (iter != ref.end()) {
         *iter = tolower(*iter);
         iter++;
      }
      return ref;
   }

   /**
    * @param relating    To what is this queue related: Constants.RELATING_CALLBACK | Constants.RELATING_SUBJECT
    */
   void CbQueueProperty::setRelating(const string& relating)
   {
      if (relating == "") {
         relating_ = Constants::RELATING_CALLBACK;
         return;
      }
      string help = relating;
      help = toLowerCase(help);

      if (Constants::RELATING_CALLBACK == help)
         relating_ = Constants::RELATING_CALLBACK;
      else if (Constants::RELATING_SUBJECT == help)
         relating_ = Constants::RELATING_SUBJECT;
      else {
         log_.warn(ME, string("The queue relating attribute is invalid '") + relating + string("', setting to session scope"));
         relating_ = Constants::RELATING_CALLBACK;
      }
   }

   bool CbQueueProperty::isSubjectRelated()
   {
      return Constants::RELATING_SUBJECT == getRelating();
   }

   bool CbQueueProperty::isSessionRelated()
   {
      return Constants::RELATING_CALLBACK == getRelating();
   }

   bool CbQueueProperty::onOverflowDeadMessage()
   {
      if (Constants::ONOVERFLOW_DEADMESSAGE == getOnOverflow())
         return true;
      return false;
   }


   /**
    * Currently only one address is allowed, failover addresses will be implemented in a future version
    */
   void CbQueueProperty::setCallbackAddress(const AddressBase& address)
   {
//      addressArr_.insert(addressArr_.begin(), address);
      if (!addressArr_.empty()) addressArr_.erase(addressArr_.begin());
      addressArr_.insert(addressArr_.begin(), address);
   }

   /**
    */
   void CbQueueProperty::setCallbackAddresses(const AddressVector& addresses)
   {
      addressArr_ = AddressVector(addresses);
   }

   /**
    * @return array with size 0 if none available
    */
   AddressVector CbQueueProperty::getCallbackAddresses()
   {
      return addressArr_;
   }

   /**
    * @return null if none available
    */
   AddressBase CbQueueProperty::getCurrentCallbackAddress()
   {
      if (addressArr_.empty()) return CallbackAddress(global_);
      return *addressArr_.begin();
   }

   /**
    * Get a usage string for the connection parameters
    */
   string CbQueueProperty::usage()
   {
      string text;
      text += string("Control the callback queue properties:\n");
      text += string("   -cb.queue.maxMsg       The maximum allowed number of messages in this queue [") + lexical_cast<string>(DEFAULT_maxMsgDefault) + string("].\n");
      text += string("   -cb.queue.maxMsgCache  The maximum allowed number of messages in the cache of this queue [") + lexical_cast<string>(DEFAULT_maxMsgDefault) + string("].\n");
      text += string("   -cb.queue.maxBytes      The maximum size in kBytes of this queue [") + lexical_cast<string>(DEFAULT_bytesDefault) + string("].\n");
      text += string("   -cb.queue.maxBytesCache The maximum size in kBytes in the cache of this queue [") + lexical_cast<string>(DEFAULT_bytesDefault) + string("].\n");
    //text += "   -cb.queue.expires  If not otherwise noted a queue dies after these milliseconds [" + DEFAULT_expiresDefault + "].\n";
    //text += "   -cb.queue.onOverflow What happens if queue is full. " + Constants.ONOVERFLOW_BLOCK + " | " + Constants.ONOVERFLOW_DEADMESSAGE + " [" + DEFAULT_onOverflow + "]\n";
      text += string("   -cb.queue.onOverflow What happens if queue is full [") + DEFAULT_onOverflow + string("]\n");
      text += string("   -cb.queue.onFailure  Error handling when callback failed [") + DEFAULT_onFailure + string("]\n");
      text += string("   -cb.queue.type       The plugin type [") + DEFAULT_type + string("]\n");
      text += string("   -cb.queue.version    The plugin version [") + DEFAULT_version + string("]\n");
      return text;
   }

}}}}} // namespace


#ifdef _XMLBLASTER_CLASSTEST

using namespace std;
using namespace org::xmlBlaster::util::qos::storage;

/** For testing: java org.xmlBlaster.authentication.plugins.simple.SecurityQos */
int main(int args, char* argv[])
{
   Global& glob = Global::getInstance();
   glob.initialize(args, argv);

   CbQueueProperty prop(glob, "", "");

   cout << prop.toXml() << endl;
   CallbackAddress adr(glob, "EMAIL");
   adr.setAddress("et@mars.sun");
   prop.setCallbackAddress(adr);
   cout << prop.toXml() << endl;
}

#endif


