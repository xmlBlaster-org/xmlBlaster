/*------------------------------------------------------------------------------
Name:      HistoryQueueProperty.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Helper class holding history queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.client.qos.ConnectQos
 */

#include <util/qos/storage/HistoryQueueProperty.h>
#include <util/lexical_cast.h>
#include <util/Constants.h>
#include <util/Global.h>



using namespace org::xmlBlaster::util;
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

HistoryQueueProperty::HistoryQueueProperty(Global& global, const string& nodeId)
   : QueuePropertyBase(global, nodeId)
{
   ME = "HistoryQueueProperty";
   setRelating(Constants::RELATING_HISTORY);
   QueuePropertyBase::initialize(Constants::RELATING_HISTORY);
}

HistoryQueueProperty::HistoryQueueProperty(const QueuePropertyBase& prop)
   : QueuePropertyBase(prop)
{
}

HistoryQueueProperty& HistoryQueueProperty::operator =(const QueuePropertyBase& prop)
{
   copy(prop);
   return *this;
}

string HistoryQueueProperty::getSettings()
{
   string ret;
   ret += "type=" + getType() + " onOverflow=" + getOnOverflow() + " onFailure=" + getOnFailure() + " maxEntries=" + lexical_cast<std::string>(getMaxEntries());
   return ret;
}

bool HistoryQueueProperty::onOverflowDeadMessage()
{
   if (Constants::ONOVERFLOW_DEADMESSAGE == getOnOverflow())
      return true;
   return false;
}

}}}}} // namespace

#ifdef _XMLBLASTER_CLASSTEST


using namespace std;
using namespace org::xmlBlaster::util::qos::storage;

int main(int args, char* argv[])
{
   try {

      Global& glob = Global::getInstance();
      glob.initialize(args, argv);
      HistoryQueueProperty prop(glob, "");

      cout << prop.toXml() << endl;
   }
   catch (...) {
      cerr << "an exception occured in the main thread" << endl;
   }
}

#endif




