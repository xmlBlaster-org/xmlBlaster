/*------------------------------------------------------------------------------
Name:      QueueProperty.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
------------------------------------------------------------------------------*/

#include <util/queue/QueueProperty.h>
#include <boost/lexical_cast.hpp>

using boost::lexical_cast;

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cfg;

namespace org { namespace xmlBlaster { namespace util { namespace queue {

   QueueProperty::QueueProperty(Global& global, const string& nodeId) :
      QueuePropertyBase(global, nodeId), ME("QueueProperty")
   {
      relating_ = Constants::RELATING_CLIENT;
      initialize();
   }

   /**
    * Show some important settings for logging
    */
   string QueueProperty::getSettings()
   {
      string ret;
      ret += string("type=") + getType() + string(" onOverflow=") +
             getOnOverflow() + string(" onFailure=") + getOnFailure() +
             string(" maxMsg=") + lexical_cast<string>(getMaxMsg());
      if (getCurrentAddress() != NULL)
         ret += string(" ") + getCurrentAddress()->getSettings();
      return ret;
   }

   /**
    */
   void QueueProperty::setAddress(const Address& address)
   {
      // this differes from the current java code (2002-12-07) since it allows
      // multiple addresses
      AddressBase* el = new Address(address);
      addressArr_.insert(addressArr_.begin(), el);
   }

   void QueueProperty::cleanUpAddresses()
   {
      AddressVector::iterator iter = addressArr_.begin();
      while (iter != addressArr_.end()) {
        AddressBase* el = *iter;
        addressArr_.erase(iter);
        delete el;
        iter = addressArr_.begin();
      }
   }

   /**
    * clears up all addresses and allocates new ones.
    */
   void QueueProperty::setAddresses(const vector<Address>& addresses)
   {
      // clean up the old addresses vector ...
      cleanUpAddresses();
      addressArr_ = AddressVector();
      vector<Address>::const_iterator iter = addresses.begin();
      while(iter != addresses.end()) {
          setAddress(*iter);
        iter++;
      }
   }


   /**
    * @return null if none available
   public Address[] getAddresses() {
      return (Address[])this.addressArr;
   }
    */

   /**
    * @return null if none available
    */
   Address* QueueProperty::getCurrentAddress()
   {
      if (addressArr_.empty()) {
         Address* ptr = new Address(global_);
         addressArr_.insert(addressArr_.begin(), ptr);
         return ptr;
      }
      // otherwise get the last one added
      AddressBase* ptr  = *addressArr_.begin();
      Address*     ptr1 = dynamic_cast<Address*>(ptr);
      return ptr1;
   }

   /**
    * Get a usage string for the connection parameters
    */
   string QueueProperty::usage()
   {
      string text = "";
      text += string("Control client side fail save queue properties (message recorder):\n");
      text += string("   -queue.maxMsg       The maximum allowed number of messages in this queue [") + lexical_cast<string>(DEFAULT_maxMsgDefault) + string("].\n");
      text += string("                       0 switches recording of invocations off.\n");
      text += string("                       -1 sets it to unlimited.\n");
      text += string("   -queue.type         The queue plugin type [") + DEFAULT_type + string("].\n");
      text += string("   -queue.version      The queue plugin type [") + DEFAULT_version + string("].\n");
      text += string("   -recorder.type      The plugin type to use for tail back messages in fail save mode [FileRecorder]\n");
      text += string("   -recorder.version   The version of the plugin [1.0]\n");
      text += string("   -recorder.path      The path (without file name) for the file for FileRecorder [<is generated>]\n");
      text += string("   -recorder.fn        The file name (without path) for the file for FileRecorder [<is generated unique>]\n");
      text += string("   -recorder.rate      The playback rate in msg/sec on reconnect e.g. 200 is 200 msg/sec, -1 is as fast as possible [-1]\n");
      text += string("   -recorder.mode      The on-overflow mode: ") + string(Constants::ONOVERFLOW_EXCEPTION) + string(" | ") + string(Constants::ONOVERFLOW_DISCARD) + string(" | )" + string(Constants::ONOVERFLOW_DISCARDOLDEST) + string(" [") + string(Constants::ONOVERFLOW_EXCEPTION) + string("]\n"));
    //text += string("   -queue.maxSize      The maximum size in kBytes of this queue [" + DEFAULT_sizeDefault + "].\n";
    //text += string("   -queue.expires      If not otherwise noted a queue dies after these milliseconds [" + DEFAULT_expiresDefault + "].\n";
    //text += string("   -queue.onOverflow   What happens if queue is full. " + Constants.ONOVERFLOW_BLOCK + " | " + Constants.ONOVERFLOW_DEADMESSAGE + " [" + DEFAULT_onOverflow + "]\n";
    //text += string("   -queue.onFailure    What happens if the data sink connection has a failure [" + DEFAULT_onFailure + "]\n";
      return text;
   }

}}}} // namespace

#ifdef _XMLBLASTER_CLASSTEST

using namespace std;
using org::xmlBlaster::util::queue::QueueProperty;

/** For testing: java org.xmlBlaster.authentication.plugins.simple.SecurityQos */
int main(int args, char* argv[])
{
   Global& glob = Global::getInstance();
   glob.initialize(args, argv);
   QueueProperty prop(glob, "");
   cout << prop.toXml() << endl;
   Address adr(glob, "EMAIL");
   adr.setAddress("et@mars.sun");
   prop.setAddress(adr);
   cout << prop.toXml() << endl;
}

#endif
