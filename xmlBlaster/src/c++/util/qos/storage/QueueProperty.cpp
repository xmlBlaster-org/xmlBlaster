/*------------------------------------------------------------------------------
Name:      QueueProperty.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback queue properties
------------------------------------------------------------------------------*/

#include <util/qos/storage/QueueProperty.h>
#include <boost/lexical_cast.hpp>
#include <util/Global.h>

using boost::lexical_cast;

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos::address;

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

   inline void QueueProperty::initialize()
   {
      QueuePropertyBase::initialize("");
/*
      setMaxMsg(global_.getProperty().getLongProperty("queue.maxMsg", DEFAULT_maxMsgDefault));
      setMaxBytes(global_.getProperty().getLongProperty("queue.maxBytes", DEFAULT_bytesDefault));
      setExpires(global_.getProperty().getTimestampProperty("queue.expires", DEFAULT_maxExpires));
      setOnOverflow(global_.getProperty().getStringProperty("queue.onOverflow", DEFAULT_onOverflow));
      setOnFailure(global_.getProperty().getStringProperty("queue.onFailure", DEFAULT_onFailure));
      setType(global_.getProperty().getStringProperty("queue.type", DEFAULT_type));
      setVersion(global_.getProperty().getStringProperty("queue.version", DEFAULT_version));
      if (nodeId_ != "") {
         setMaxMsg(global_.getProperty().getLongProperty(string("queue.maxMsg[")+nodeId_+string("]"), getMaxMsg()));
         setMaxBytes(global_.getProperty().getLongProperty(string("queue.maxBytes[")+nodeId_+string("]"), getMaxBytes()));
         setExpires(global_.getProperty().getTimestampProperty(string("queue.expires[")+nodeId_+string("]"), getExpires()));
         setOnOverflow(global_.getProperty().getStringProperty(string("queue.onOverflow[")+nodeId_+string("]"), getOnOverflow()));
         setOnFailure(global_.getProperty().getStringProperty(string("queue.onFailure[")+nodeId_+string("]"), getOnFailure()));
         setType(global_.getProperty().getStringProperty(string("queue.type[")+nodeId_+string("]"), getType()));
         setVersion(global_.getProperty().getStringProperty(string("queue.version[")+nodeId_+string("]"), getVersion()));
      }
*/
   }

   QueueProperty::QueueProperty(Global& global, const string& nodeId) :
      QueuePropertyBase(global, nodeId)
   {
      ME = "QueueProperty";
      relating_ = Constants::RELATING_CLIENT;
      initialize();
   }

   QueueProperty::QueueProperty(const QueuePropertyBase& prop)
      : QueuePropertyBase(prop)
   {
   }

   QueueProperty& QueueProperty::operator =(const QueuePropertyBase& prop)
   {
      copy(prop);
      return *this;
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
      if (!addressArr_.empty())
         ret += string(" ") + getCurrentAddress().getSettings();
      return ret;
   }

   /**
    */
   void QueueProperty::setAddress(const AddressBase& address)
   {
      // this differes from the current java code (2002-12-07) since it allows
      // multiple addresses
      addressArr_.insert(addressArr_.begin(), address);
   }

   /**
    * clears up all addresses and allocates new ones.
    */
   void QueueProperty::setAddresses(const AddressVector& addresses)
   {
      addressArr_ = AddressVector(addresses);
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
   AddressBase QueueProperty::getCurrentAddress()
   {
      if (addressArr_.empty()) return Address(global_);
      // otherwise get the last one added
      return *addressArr_.begin();
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
    //text += string("   -queue.maxBytes      The maximum size in kBytes of this queue [" + DEFAULT_bytesDefault + "].\n";
    //text += string("   -queue.expires      If not otherwise noted a queue dies after these milliseconds [" + DEFAULT_expiresDefault + "].\n";
    //text += string("   -queue.onOverflow   What happens if queue is full. " + Constants.ONOVERFLOW_BLOCK + " | " + Constants.ONOVERFLOW_DEADMESSAGE + " [" + DEFAULT_onOverflow + "]\n";
    //text += string("   -queue.onFailure    What happens if the data sink connection has a failure [" + DEFAULT_onFailure + "]\n";
      return text;
   }

}}}}} // namespace

#ifdef _XMLBLASTER_CLASSTEST

using namespace std;
using org::xmlBlaster::util::qos::storage::QueueProperty;

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
