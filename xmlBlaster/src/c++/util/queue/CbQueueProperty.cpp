/*------------------------------------------------------------------------------
Name:      CbQueueProperty.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
Version:   $Id: CbQueueProperty.cpp,v 1.2 2002/12/09 13:00:35 laghi Exp $
------------------------------------------------------------------------------*/

/**
 * Helper class holding callback queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.util.ConnectQos
 */

#include <util/queue/CbQueueProperty.h>
#include <boost/lexical_cast.hpp>
#include <ctype.h> // for toUpper

using boost::lexical_cast;

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cfg;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

   CbQueueProperty::CbQueueProperty(Global& global,
                                    const string& relating,
                                    const string& nodeId)
                                  : QueuePropertyBase(global, nodeId)
   {
      ME = "CbQueueProperty";
      log_  = global_.getLog("dispatch");
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
      if (getCurrentCallbackAddress() != NULL)
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
    * @param relating    To what is this queue related: Constants.RELATING_SESSION | Constants.RELATING_SUBJECT
    */
   void CbQueueProperty::setRelating(const string& relating)
   {
      if (relating == "") {
         relating_ = Constants::RELATING_SESSION;
         return;
      }
      string help = relating;
      help = toLowerCase(help);

      if (Constants::RELATING_SESSION == help)
         relating_ = Constants::RELATING_SESSION;
      else if (Constants::RELATING_SUBJECT == help)
         relating_ = Constants::RELATING_SUBJECT;
      else {
         log_.warn(ME, string("The queue relating attribute is invalid '") + relating + string("', setting to session scope"));
         relating_ = Constants::RELATING_SESSION;
      }
   }

   bool CbQueueProperty::isSubjectRelated()
   {
      return Constants::RELATING_SUBJECT == getRelating();
   }

   bool CbQueueProperty::isSessionRelated()
   {
      return Constants::RELATING_SESSION == getRelating();
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
   void CbQueueProperty::setCallbackAddress(const CallbackAddress& address)
   {
      AddressBase* ptr = new CallbackAddress(address);
      addressArr_.insert(addressArr_.begin(), ptr);
   }

   /**
    */
   void CbQueueProperty::setCallbackAddresses(const vector<CallbackAddress>& addresses)
   {
      // clean up the old addresses vector ...
      QueuePropertyBase::cleanupAddresses();
      addressArr_ = AddressVector();
      vector<CallbackAddress>::const_iterator iter = addresses.begin();
      while(iter != addresses.end()) {
          setCallbackAddress(*iter);
        iter++;
      }
   }

   /**
    * @return array with size 0 if none available
    */
   vector<CallbackAddress> CbQueueProperty::getCallbackAddresses()
   {
      vector<CallbackAddress> ret;

      AddressVector::iterator iter = addressArr_.begin();
      while (iter != addressArr_.end()) {
         AddressBase* ptr = *iter;
         CallbackAddress* ptr1 = dynamic_cast<CallbackAddress*>(ptr);
         ret.insert(ret.begin(), *ptr1);
         iter++;
      }
      return ret;
   }

   /**
    * @return null if none available
    */
   CallbackAddress* CbQueueProperty::getCurrentCallbackAddress()
   {
      if (addressArr_.empty()) return NULL;
      AddressBase* ptr = *(addressArr_.begin());
      CallbackAddress* ret = dynamic_cast<CallbackAddress*>(ptr);
      return ret;
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
      text += string("   -cb.queue.maxSize      The maximum size in kBytes of this queue [") + lexical_cast<string>(DEFAULT_sizeDefault) + string("].\n");
      text += string("   -cb.queue.maxSizeCache The maximum size in kBytes in the cache of this queue [") + lexical_cast<string>(DEFAULT_sizeDefault) + string("].\n");
    //text += "   -cb.queue.expires  If not otherwise noted a queue dies after these milliseconds [" + DEFAULT_expiresDefault + "].\n";
    //text += "   -cb.queue.onOverflow What happens if queue is full. " + Constants.ONOVERFLOW_BLOCK + " | " + Constants.ONOVERFLOW_DEADMESSAGE + " [" + DEFAULT_onOverflow + "]\n";
      text += string("   -cb.queue.onOverflow What happens if queue is full [") + DEFAULT_onOverflow + string("]\n");
      text += string("   -cb.queue.onFailure  Error handling when callback failed [") + DEFAULT_onFailure + string("]\n");
      text += string("   -cb.queue.type       The plugin type [") + DEFAULT_type + string("]\n");
      text += string("   -cb.queue.version    The plugin version [") + DEFAULT_version + string("]\n");
      return text;
   }

}}}} // namespace


#ifdef _XMLBLASTER_CLASSTEST

using namespace std;
using org::xmlBlaster::util::queue::CbQueueProperty;

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


