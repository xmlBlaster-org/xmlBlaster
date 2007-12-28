/*------------------------------------------------------------------------------
Name:      CbQueueProperty.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Version:   $Id$
------------------------------------------------------------------------------*/

/**
 * Helper class holding callback queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */

#include <util/qos/storage/CbQueueProperty.h>
#include <util/lexical_cast.h>
#include <ctype.h> // for toUpper
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos::address;

   CbQueueProperty::CbQueueProperty(Global& global,
                                    const string& relating,
                                    const string& nodeId)
                                  : QueuePropertyBase(global, nodeId)
   {
      ME = "CbQueueProperty";
      setRelating(relating);
      QueuePropertyBase::initialize(Constants::RELATING_CALLBACK);
      getCurrentCallbackAddress(); // Force creation, to read environment settings
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

   CbQueueProperty::~CbQueueProperty()
   {
   }

   /**
    * Show some important settings for logging
    */
   string CbQueueProperty::getSettings()
   {
      string ret;
      ret += string("type=") + getType() + string(" onOverflow=") +
             getOnOverflow() + string(" onFailure=") + getOnFailure() +
             string(" maxEntries=") + lexical_cast<std::string>(getMaxEntries());
      if (!addressArr_.empty())
         ret += string(" ") + getCurrentCallbackAddress()->getSettings();
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
   void CbQueueProperty::setCallbackAddress(const AddressBaseRef& address)
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
    * @return a default if none available
    */
   AddressBaseRef CbQueueProperty::getCurrentCallbackAddress()
   {
      if (addressArr_.empty()) {
         setCallbackAddress(new CallbackAddress(global_));
      }
      return *addressArr_.begin();
   }

   /**
    * Get a usage string for the connection parameters
    */
   string CbQueueProperty::usage()
   {
      string text;
      text += string("Control the callback queue properties (on server side):\n");
      text += string("   -queue/callback/maxEntries [") + lexical_cast<std::string>(DEFAULT_maxEntriesDefault) + string("]\n");
      text += string("                       The maximum allowed number of messages in this queue.\n");
      text += string("   -queue/callback/maxEntriesCache [") + lexical_cast<std::string>(DEFAULT_maxEntriesDefault) + string("]\n");
      text += string("                       The maximum allowed number of messages in the cache of this queue.\n");
      text += string("   -queue/callback/maxBytes [") + lexical_cast<std::string>(DEFAULT_bytesDefault) + string("]\n");
      text += string("                       The maximum size in kBytes of this queue.\n");
      text += string("   -queue/callback/maxBytesCache [") + lexical_cast<std::string>(DEFAULT_bytesDefault) + string("]\n");
      text += string("                       The maximum size in kBytes in the cache of this queue.\n");
    //text += "   -queue/callback/expires  If not otherwise noted a queue dies after these milliseconds [" + DEFAULT_expiresDefault + "].\n";
    //text += "   -queue/callback/onOverflow What happens if queue is full. " + Constants.ONOVERFLOW_BLOCK + " | " + Constants.ONOVERFLOW_DEADMESSAGE + " [" + DEFAULT_onOverflow + "]\n";
      //text += string("   -queue/callback/onOverflow What happens if queue is full [") + DEFAULT_onOverflow + string("]\n");
      //text += string("   -queue/callback/onFailure  Error handling when callback failed [") + DEFAULT_onFailure + string("]\n");
      text += string("   -queue/callback/type [") + DEFAULT_type + string("]\n");
      text += string("                       The callback queue plugin type on server side\n");
      //text += string("   -queue/callback/version    The plugin version [") + DEFAULT_version + string("]\n");
      return text;
   }

}}}}} // namespace


#ifdef _XMLBLASTER_CLASSTEST

using namespace std;
using namespace org::xmlBlaster::util::qos::storage;

/** For testing */
int main(int args, char* argv[])
{
   Global& glob = Global::getInstance();
   glob.initialize(args, argv);

   CbQueueProperty prop(glob, "", "");

   cout << prop.toXml() << endl;
   CallbackAddress *adr = new CallbackAddress(glob, "EMAIL");
   adr.setAddress("et@mars.sun");
   prop.setCallbackAddress(adr);
   cout << prop.toXml() << endl;
}

#endif


