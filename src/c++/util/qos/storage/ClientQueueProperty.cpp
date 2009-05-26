/*------------------------------------------------------------------------------
Name:      ClientQueueProperty.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
------------------------------------------------------------------------------*/

#include <util/qos/storage/ClientQueueProperty.h>
#include <util/lexical_cast.h>
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos::address;

   ClientQueueProperty::ClientQueueProperty(Global& global, const string& nodeId) :
      QueuePropertyBase(global, nodeId)
   {
      ME = "ClientQueueProperty";
      relating_ = Constants::RELATING_CLIENT;  // == "connection"
      QueuePropertyBase::initialize(Constants::RELATING_CLIENT); // == "connection"

      /*
#     if !defined (XMLBLASTER_PERSISTENT_QUEUE) || !defined (XMLBLASTER_PERSISTENT_QUEUE_SQLITE3)
      // TODO !!!: Hack: We need to force default to RAM instead of CACHE
      // as we have no C++ CACHE implementation  (see QueueFactory.cpp for the other workaround)
      string envType = global_.getProperty().getStringProperty("queue/connection/type", "");
      if (envType == "") {
         setType("RAM");
      }
#    endif
*/
   }

   ClientQueueProperty::ClientQueueProperty(const QueuePropertyBase& prop)
      : QueuePropertyBase(prop)
   {
   }

   ClientQueueProperty& ClientQueueProperty::operator =(const QueuePropertyBase& prop)
   {
      copy(prop);
      return *this;
   }

   /**
    * Show some important settings for logging
    */
   string ClientQueueProperty::getSettings()
   {
      string ret;
      ret += string("type=") + getType() + string(" onOverflow=") +
             getOnOverflow() + string(" onFailure=") + getOnFailure() +
             string(" maxEntries=") + lexical_cast<std::string>(getMaxEntries());
      if (!addressArr_.empty())
         ret += string(" ") + getCurrentAddress()->getSettings();
      return ret;
   }

   /**
    */
   void ClientQueueProperty::setAddress(const AddressBaseRef& address)
   {
      // this differes from the current java code (2002-12-07) since it allows
      // multiple addresses
      addressArr_.insert(addressArr_.begin(), address);
   }

   /**
    * clears up all addresses and allocates new ones.
    */
   void ClientQueueProperty::setAddresses(const AddressVector& addresses)
   {
      addressArr_ = AddressVector(addresses);
   }


   /**
    * @return If none is available a default is created
    */
   AddressBaseRef ClientQueueProperty::getCurrentAddress()
   {
      if (addressArr_.empty()) {
         addressArr_.push_back(new Address(global_));
      }
      // otherwise get the last one added
      return *addressArr_.begin();
   }

   /**
    * Get a usage string for the connection parameters
    */
   string ClientQueueProperty::usage()
   {
      string text = "";
      text += string("Control client side failsafe queue properties (message recorder):\n");
      text += string("   -queue/connection/maxEntries [") + lexical_cast<std::string>(DEFAULT_maxEntriesDefault) + string("]\n");
      text += string("                       The maximum allowed number of messages in this queue.\n");
      text += string("                       0 switches recording of invocations off, -1 sets it to unlimited.\n");
      text += string("   -queue/connection/type [CACHE].\n");
      text += string("                       The C++ client side queue plugin type, choose 'RAM' for a pure memory based queue.\n");
#     if defined(XMLBLASTER_PERSISTENT_QUEUE) || defined(XMLBLASTER_PERSISTENT_QUEUE_SQLITE3)
      text += string("                       Choose 'SQLite' for a pure persistent client side queue.\n");
#     else
      text += string("                       Please recompile with -DXMLBLASTER_PERSISTENT_QUEUE_SQLITE3=1 defined\n");
      text += string("                       to have a persistent client side queue 'SQLite'.\n");
#     endif
      text += string("   -queue/connection/version ") + DEFAULT_version + string("].\n");
      text += string("                       The queue plugin type.\n");
      text += string("   -queue/connection/maxBytes [" + lexical_cast<std::string>(DEFAULT_bytesDefault) + "].\n");
      text += string("                       The maximum size in bytes of this queue.\n");
#     if defined(XMLBLASTER_PERSISTENT_QUEUE) || defined (XMLBLASTER_PERSISTENT_QUEUE_SQLITE3)
      text += string("SQLite specific setting:\n");
      text += string("   -queue/connection/url [xmlBlasterClientCpp.db].\n");
      text += string("                       The location and file name of the database.\n");
#     endif
    //text += string("   -queue/connection/expires      If not otherwise noted a queue dies after these milliseconds [" + DEFAULT_expiresDefault + "].\n";
    //text += string("   -queue/connection/onOverflow   What happens if queue is full. " + Constants.ONOVERFLOW_BLOCK + " | " + Constants.ONOVERFLOW_DEADMESSAGE + " [" + DEFAULT_onOverflow + "]\n";
    //text += string("   -queue/connection/onFailure    What happens if the data sink connection has a failure [" + DEFAULT_onFailure + "]\n";
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
   ClientQueueProperty prop(glob, "");
   cout << prop.toXml() << endl;
   Address adr(glob, "EMAIL");
   adr.setAddress("et@mars.sun");
   prop.setAddress(adr);
   cout << prop.toXml() << endl;
}

#endif
