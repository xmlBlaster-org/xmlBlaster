/*-----------------------------------------------------------------------------
Name:      QueueFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Factory to create different queue implementations
-----------------------------------------------------------------------------*/

#ifndef _UTIL_PARSER_PARSERFACTORY_H
#define _UTIL_PARSER_PARSERFACTORY_H

#include <util/xmlBlasterDef.h>
#include <util/queue/I_Queue.h>
#include <util/qos/storage/QueuePropertyBase.h>
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util { namespace queue {
    
/**
 * Abstraction for the queue implementations. 
 * <p />
 * You may use this as the interface to implement your own persistent, RAM or cache based queues.
 * <p />
 * It is a singleton class and has for
 * that reason private constructors, destructor and assignment operator. 
 * To get a reference to the singleton instance you must invoke getFactory(...).
 */
class Dll_Export QueueFactory {
   friend class Global; // g++ 2.95.3 warning: `class org::xmlBlaster::util::queue::QueueFactory' only defines private constructors and has no friends

   private:
   const std::string ME;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log& log_;

   static QueueFactory* factory_;
   
   QueueFactory(org::xmlBlaster::util::Global& global);
   QueueFactory(const QueueFactory& factory);
   QueueFactory& operator =(const QueueFactory& factory);

   public:
   ~QueueFactory();

   /**
    * Static access to the factory. 
    * @exception XmlBlasterException
    */
   static QueueFactory& getFactory(org::xmlBlaster::util::Global& global);

   /**
    * Creates a queue implementation. 
    * <p />
    * It is the responsibility of the user to delete the I_Queue
    * object once it is not needed anymore.
    * @exception XmlBlasterException
    */
   I_Queue* createQueue(const org::xmlBlaster::util::qos::storage::QueuePropertyBase& property);
};

}}}} // namespace

#endif
