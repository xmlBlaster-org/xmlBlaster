/*------------------------------------------------------------------------------
Name:      HistoryQueueProperty.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Helper class holding history queue properties.
 * <p />
 * See ConnectQos for XML sysntax.
 * @see org.xmlBlaster.util.ConnectQos
 */

/*
#ifndef _UTIL_TOPICCACHEPROPERTY_H
#define _UTIL_TOPICCACHEPROPERTY_H
*/

#include <util/qos/storage/HistoryQueueProperty.h>
#include <boost/lexical_cast.hpp>
#include <util/Constants.h>
#include <util/Global.h>

using boost::lexical_cast;

using namespace org::xmlBlaster::util;
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace storage {

HistoryQueueProperty::HistoryQueueProperty(Global& global, const string& nodeId)
   : QueuePropertyBase(global, nodeId)
{
   ME = "HistoryQueueProperty";
   setRelating(Constants::RELATING_HISTORY);
   initialize();
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
   ret += "type=" + getType() + " onOverflow=" + getOnOverflow() + " onFailure=" + getOnFailure() + " maxMsg=" + lexical_cast<string>(getMaxMsg());
   return ret;
}

void HistoryQueueProperty::initialize()
{
   QueuePropertyBase::initialize("history");
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




