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


 
namespace org { namespace xmlBlaster { namespace util { namespace queue {

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

/**
 * Creates a queue implementation. It is the responsibility of the user to delete the I_Queue
 * object once it is not needed anymore.
 */
I_Queue* QueueFactory::createQueue(const org::xmlBlaster::util::qos::storage::QueuePropertyBase& property)
{
   return new RamQueuePlugin(global_, property);
}


}}}} // namespace

#endif
