/*------------------------------------------------------------------------------
Name:      TopicCacheProperty.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Helper class holding properties of the MsgUnit storage. 
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.util.ConnectQos
 */

#include <util/qos/storage/TopicCacheProperty.h>
#include <util/Constants.h>
#include <boost/lexical_cast.hpp>
#include <util/Global.h>

using boost::lexical_cast;
using namespace org::xmlBlaster::util;
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

TopicCacheProperty::TopicCacheProperty(Global& global, const string& nodeId)
   : QueuePropertyBase(global, nodeId)
{
   ME = string("TopicCacheProperty");
   relating_ = Constants::RELATING_TOPICCACHE;
   initialize();
}

TopicCacheProperty::TopicCacheProperty(const QueuePropertyBase& prop)
   : QueuePropertyBase(prop)
{
}

TopicCacheProperty& TopicCacheProperty::operator =(const QueuePropertyBase& prop)
{
   copy(prop);
   return *this;
}



/**
 * Configure property settings
 */
void TopicCacheProperty::initialize()
{
   QueuePropertyBase::initialize("topic.cache");
}

bool TopicCacheProperty::onOverflowDeadMessage()
{
   if (Constants::ONOVERFLOW_DEADMESSAGE == getOnOverflow())
      return true;
   return false;
}

/**
 * Get a usage string for the connection parameters
 */
string TopicCacheProperty::usage()
{
   string text;
   text += "Control the MsgUnit storage properties:\n";
   text += "   -topic.cache.maxMsg       The maximum allowed number of messages in this storage [" + lexical_cast<string>(DEFAULT_maxMsgDefault) + "].\n";
   text += "   -topic.cache.maxMsgCache  The maximum allowed number of messages in the cache of this storage [" + lexical_cast<string>(DEFAULT_maxMsgDefault) + "].\n";
   text += "   -topic.cache.maxBytes      The maximum size in bytes of this storage [" + lexical_cast<string>(DEFAULT_bytesDefault) + "].\n";
   text += "   -topic.cache.maxBytesCache The maximum size in bytes in the cache of this storage [" + lexical_cast<string>(DEFAULT_bytesCacheDefault) + "].\n";
 //text += "   -topic.cache.expires  If not otherwise noted a storage dies after these milliseconds [" + DEFAULT_expiresDefault + "].\n";
 //text += "   -topic.cache.onOverflow What happens if storage is full. " + Constants::ONOVERFLOW_BLOCK + " | " + Constants::ONOVERFLOW_DEADMESSAGE + " [" + DEFAULT_onOverflow + "]\n";
   text += "   -topic.cache.onOverflow What happens if storage is full [" + DEFAULT_onOverflow + "]\n";
   text += "   -topic.cache.onFailure  Error handling when history failed [" + DEFAULT_onFailure + "]\n";
   text += "   -topic.cache.type       The plugin type [" + DEFAULT_type + "]\n";
   text += "   -topic.cache.version    The plugin version [" + DEFAULT_version + "]\n";
   return text;
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
      TopicCacheProperty prop(glob, "");

      cout << prop.toXml() << endl;
   }
   catch (...) {
      cerr << "an exception occured in the main thread" << endl;
   }
}

#endif


