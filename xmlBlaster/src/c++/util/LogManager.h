/*----------------------------------------------------------------------------
Name:      LogManager.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
-----------------------------------------------------------------------------*/

#ifndef _ORG_XMLBLASTER_UTIL_LOGMANAGER_H
#define _ORG_XMLBLASTER_UTIL_LOGMANAGER_H

#include <util/xmlBlasterDef.h>
#include <string>
#include <map>
#include <util/I_LogFactory.h>
#include <util/I_Log.h>

namespace org { namespace xmlBlaster { namespace util {
   
class DefaultLogFactory; // forward declaration


/**
 * Manages the logging framework. 
 * <p />
 * The logManager uses xmlBlasters own logging functionality
 * as a default setting.
 * You can change this and add your own logging library by providing your
 * implementation of I_LogFactory.
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
class Dll_Export LogManager {
   
   public:
      typedef org::xmlBlaster::util::I_LogFactory::PropMap PropMap;
      typedef std::map<std::string, org::xmlBlaster::util::I_LogFactory *> LogFactoryMap;

   private:
      I_LogFactory *logFactory_;

      /**
       * Copy constructor
       */
      LogManager(const LogManager& rhs);

      /**
       * Assignment constructor
       */
      LogManager& operator=(const LogManager& rhs);

   public:
      LogManager();

      virtual ~LogManager();

      /**
       * Should be called directly after the constructor. 
       */
      void initialize(const PropMap& propMap);

      /**
       * Return the current LogFactory implementation. 
       * @param name Log implementation name (currently not supported)
       */
      I_LogFactory& getLogFactory(const std::string& name="DEFAULT") const;

      /**
       * Add your own LogFactory implementation. 
       * <p />
       * We will delete it when the LogManager is destroyed or when
       * setLogFactory() is called again.
       * @param name Log implementation name (currently not supported, pass "")
       * @param logFactory Your factory for your logging framework
       */
      void setLogFactory(const std::string& name, I_LogFactory* logFactory);

}; // end of class LogManager



/**
 * Default implementation of our log factory. 
 * It returns the xmlBlaster native console logger with colored output on UNIX
 */
class Dll_Export DefaultLogFactory : public I_LogFactory {
   public:
      typedef std::map<std::string, org::xmlBlaster::util::I_Log *> LogMap;
   private:
      LogMap logMap_;
   public:
      //void initialize(const PropMap& propMap);
      //DefaultLogFactory(){};
      virtual ~DefaultLogFactory();
      I_Log& getLog(const std::string& name="");
      void releaseLog(const std::string& name="");
}; // end of class DefaultLogFactory


}}} // end of namespace util

#endif // _ORG_XMLBLASTER_UTIL_LOGMANAGER_H
