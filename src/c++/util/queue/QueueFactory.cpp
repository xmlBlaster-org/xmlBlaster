/*-----------------------------------------------------------------------------
Name:      QueueFactory.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory to create different queue implementations
-----------------------------------------------------------------------------*/

#ifndef _UTIL_PARSER_PARSERFACTORY_C
#define _UTIL_PARSER_PARSERFACTORY_C

#if defined(_WIN32)
  #pragma warning(disable:4786)
#endif

#include <util/ErrorCode.h>
#include <util/XmlBlasterException.h>
#include <util/Global.h>
#include <util/queue/QueueFactory.h>
#include <util/queue/RamQueuePlugin.h>
#include <util/queue/CacheQueuePlugin.h>
#ifdef XMLBLASTER_PERSISTENT_QUEUE
#  include <util/queue/SQLiteQueuePlugin.h>
#endif
#include <string>

 
namespace org { namespace xmlBlaster { namespace util { namespace queue {

using namespace std;
using namespace org::xmlBlaster::util;
    
QueueFactory* QueueFactory::factory_ = NULL;

QueueFactory& QueueFactory::getFactory()
{
   if (factory_ == NULL) {
      factory_ = new QueueFactory();
      org::xmlBlaster::util::Object_Lifetime_Manager::instance()->manage_object("XB_QueueFactory", factory_);  // if not pre-allocated.
   }
   return *factory_;
}

QueueFactory::QueueFactory() :
     ME("QueueFactory")
{
}

QueueFactory::QueueFactory(const QueueFactory& factory) :
               ME(factory.ME)
{
   throw util::XmlBlasterException(INTERNAL_NOTIMPLEMENTED, ME, "private copy constructor");
}

QueueFactory& QueueFactory::operator =(const QueueFactory&)
{
   throw util::XmlBlasterException(INTERNAL_NOTIMPLEMENTED, ME, "private assignement operator");
}

QueueFactory::~QueueFactory()
{
}

I_Queue& QueueFactory::getPlugin(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::qos::storage::QueuePropertyBase& property, const string& type, const string& /*version*/)
{
   org::xmlBlaster::util::I_Log& log = global.getLog("org.xmlBlaster.queue");
   if (log.call()) log.call(ME, string("getPlugin: type: '") + property.getType() + string("', version: '") + property.getVersion() + "' ...");
   string typ = type.empty() ? property.getType() : type;

   if (typ == Constants::CACHE) {
      return *(new CacheQueuePlugin(global, property));
   }
   else if (typ == Constants::RAM) {
      return *(new RamQueuePlugin(global, property));
   }
   else if (typ == Constants::SQLITE) {
#     ifdef XMLBLASTER_PERSISTENT_QUEUE
         return *(new SQLiteQueuePlugin(global, property));  //#ifdef XMLBLASTER_PERSISTENT_QUEUE
#     else
         log.error(ME, "Please compile with -DXMLBLASTER_PERSISTENT_QUEUE=1 defined to have SQLite persistent queue support");
#     endif
   }
   string embeddedMsg = string("Plugin: '") + property.getType() +
                        string("' and version: '") + property.getVersion() +
                        string("' is not supported");
   throw XmlBlasterException(RESOURCE_CONFIGURATION_PLUGINFAILED,
                    "client-c++",
                    ME + string("::getPlugin"),
                    "en",
                    global.getVersion() + " " + global.getBuildTimestamp(),
                    "",
                    "",
                    embeddedMsg);
}

void QueueFactory::releasePlugin(I_Queue *queueP)
{
   if (queueP) {
      delete queueP;
   }
}


}}}} // namespace

#endif
