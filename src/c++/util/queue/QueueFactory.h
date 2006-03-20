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
   static QueueFactory* factory_;
   
   QueueFactory();
   QueueFactory(const QueueFactory& factory);
   QueueFactory& operator =(const QueueFactory& factory);

   public:
   ~QueueFactory();

   /**
    * Static access to the factory. 
    * @exception XmlBlasterException
    */
   static QueueFactory& getFactory();

   /**
    * Creates a queue implementation. It is the responsibility of the user to delete the I_Queue
    * object once it is not needed anymore by calling <code>releasePlugin()</code>. 
    * @param property The configuration settings
    * @param type The queue type, for example "RAM", "SQLite", if empty the setting from argument 'property' is used
    * @param version The queue version, defaults to "1.0", if empty the setting from argument 'property' is used
    * @throws XmlBlasterException: "resource.configuration.pluginFailed" if plugin is not known or other errorCodes if it can't be initialized. 
    */
   I_Queue& getPlugin(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::qos::storage::QueuePropertyBase& property,
                      const std::string& type="", const std::string& version="");

   /**
    * After calling this the <code>queue</code> argument in not usable anymore. 
    */
   void releasePlugin(I_Queue *queueP); /*const std::string& type="RAM", const std::string& version="1.0");*/
};

}}}} // namespace

#endif
