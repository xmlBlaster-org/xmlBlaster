/*------------------------------------------------------------------------------
Name:      MsgUnitStoreProperty.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Helper class holding properties of the MsgUnit storage. 
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */

#include <util/qos/storage/MsgUnitStoreProperty.h>
#include <util/Constants.h>
#include <boost/lexical_cast.hpp>
#include <util/Global.h>

using boost::lexical_cast;
using namespace org::xmlBlaster::util;
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

MsgUnitStoreProperty::MsgUnitStoreProperty(Global& global, const string& nodeId)
   : QueuePropertyBase(global, nodeId)
{
   ME = string("MsgUnitStoreProperty");
   relating_ = Constants::RELATING_TOPICCACHE;
   initialize();
}

MsgUnitStoreProperty::MsgUnitStoreProperty(const QueuePropertyBase& prop)
   : QueuePropertyBase(prop)
{
}

MsgUnitStoreProperty& MsgUnitStoreProperty::operator =(const QueuePropertyBase& prop)
{
   copy(prop);
   return *this;
}



/**
 * Configure property settings
 */
void MsgUnitStoreProperty::initialize()
{
   QueuePropertyBase::initialize(""); // "msgUnitStore");
}

bool MsgUnitStoreProperty::onOverflowDeadMessage()
{
   if (Constants::ONOVERFLOW_DEADMESSAGE == getOnOverflow())
      return true;
   return false;
}

/**
 * Get a usage string for the connection parameters
 */
string MsgUnitStoreProperty::usage()
{
   string text;
   text += "Control the MsgUnit storage properties:\n";
   text += "   -msgUnitStore.maxMsg       The maximum allowed number of messages in this storage [" + lexical_cast<string>(DEFAULT_maxMsgDefault) + "].\n";
   text += "   -msgUnitStore.maxMsgCache  The maximum allowed number of messages in the cache of this storage [" + lexical_cast<string>(DEFAULT_maxMsgDefault) + "].\n";
   text += "   -msgUnitStore.maxBytes      The maximum size in bytes of this storage [" + lexical_cast<string>(DEFAULT_bytesDefault) + "].\n";
   text += "   -msgUnitStore.maxBytesCache The maximum size in bytes in the cache of this storage [" + lexical_cast<string>(DEFAULT_bytesCacheDefault) + "].\n";
 //text += "   -msgUnitStore.expires  If not otherwise noted a storage dies after these milliseconds [" + DEFAULT_expiresDefault + "].\n";
 //text += "   -msgUnitStore.onOverflow What happens if storage is full. " + Constants::ONOVERFLOW_BLOCK + " | " + Constants::ONOVERFLOW_DEADMESSAGE + " [" + DEFAULT_onOverflow + "]\n";
   text += "   -msgUnitStore.onOverflow What happens if storage is full [" + DEFAULT_onOverflow + "]\n";
   text += "   -msgUnitStore.onFailure  Error handling when history failed [" + DEFAULT_onFailure + "]\n";
   text += "   -msgUnitStore.type       The plugin type [" + DEFAULT_type + "]\n";
   text += "   -msgUnitStore.version    The plugin version [" + DEFAULT_version + "]\n";
   return text;
}

/**
 * The tag name for configuration, here it is &lt;msgUnitStore ...>
 */
string MsgUnitStoreProperty::getRootTagName() const
{
   return "msgUnitStore";
}

}}}}}

#ifdef _XMLBLASTER_CLASSTEST


using namespace std;
using namespace org::xmlBlaster::util::qos::storage;

int main(int args, char* argv[])
{
   try {

      Global& glob = Global::getInstance();
      glob.initialize(args, argv);
      MsgUnitStoreProperty prop(glob, "");

      cout << prop.toXml() << endl;
   }
   catch (...) {
      cerr << "an exception occured in the main thread" << endl;
   }
}

#endif


