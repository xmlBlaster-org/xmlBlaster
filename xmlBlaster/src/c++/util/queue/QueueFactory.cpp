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
#include <util/PlatformUtils.hpp>
#include <util/queue/QueueFactory.h>
#include <util/queue/RamQueuePlugin.h>
#include <util/queue/SQLiteQueuePlugin.h>
#include <string>

 
namespace org { namespace xmlBlaster { namespace util { namespace queue {

using namespace std;
using namespace org::xmlBlaster::util;
    
QueueFactory* QueueFactory::factory_ = NULL;

QueueFactory& QueueFactory::getFactory(Global& global)
{
   if (factory_ == NULL) {
      factory_ = new QueueFactory(global);
      org::xmlBlaster::util::Object_Lifetime_Manager::instance()->manage_object("XB_QueueFactory", factory_);  // if not pre-allocated.
   }
   return *factory_;
}

QueueFactory::QueueFactory(Global& global) :
     ME("QueueFactory"), 
     global_(global), 
     log_(global_.getLog("org.xmlBlaster.util.queue"))
{
}

QueueFactory::QueueFactory(const QueueFactory& factory) :
               ME(factory.ME),
               global_(factory.global_),
               log_(factory.log_)
{
   throw util::XmlBlasterException(INTERNAL_NOTIMPLEMENTED, ME, "private copy constructor");
}

QueueFactory& QueueFactory::operator =(const QueueFactory&)
{
   throw util::XmlBlasterException(INTERNAL_NOTIMPLEMENTED, ME, "private assignement operator");
}

QueueFactory::~QueueFactory()
{
   if (log_.call()) log_.call(ME, "~QueueFactory()");
}

I_Queue& QueueFactory::getPlugin(const org::xmlBlaster::util::qos::storage::QueuePropertyBase& property) //, const string& type, const string& version)
{
   if (log_.trace()) log_.trace(ME, string("getPlugin: type: '") + property.getType() + string("', version: '") + property.getVersion() + "' ...");

   if (property.getType() == Constants::RAM ||
       property.getType() == Constants::CACHE) { // HACK! The default in QueuePropertyBase is CACHE, so we use our RAM in this case, see ClientQueueProperty.cpp for another hack
      return *(new RamQueuePlugin(global_, property));
   }
   else if (property.getType() == Constants::SQLITE) {
#     ifdef XMLBLASTER_PERSISTENT_QUEUE
         return *(new SQLiteQueuePlugin(global_, property));  //#ifdef XMLBLASTER_PERSISTENT_QUEUE
#     else
         log_.error(ME, "Please compile with -DXMLBLASTER_PERSISTENT_QUEUE=1 defined to have SQLite persistent queue support");
#     endif
   }
   string embeddedMsg = string("Plugin: '") + property.getType() +
                        string("' and version: '") + property.getVersion() +
                        string("' is not supported");
   throw XmlBlasterException(RESOURCE_CONFIGURATION_PLUGINFAILED,
                    "client-c++",
                    ME + string("::getPlugin"),
                    "en",
                    global_.getVersion() + " " + global_.getBuildTimestamp(),
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
