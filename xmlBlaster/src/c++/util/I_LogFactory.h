/*----------------------------------------------------------------------------
Name:      I_LogFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
-----------------------------------------------------------------------------*/

#ifndef _ORG_XMLBLASTER_UTIL_I_LOGFACTORY_H
#define _ORG_XMLBLASTER_UTIL_I_LOGFACTORY_H

#include <util/xmlBlasterDef.h>
#include <string>
#include <map>
#include <util/I_Log.h>

/**
 * Interface for a factory of I_Log (logging) instances. 
 * <p />
 * This allows to customize which logging library you want to use.
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
namespace org { namespace xmlBlaster { namespace util {
   
   class Dll_Export I_LogFactory {
      
   public:
      typedef std::map<std::string, std::string, std::less<std::string> > PropMap;

   protected:
      PropMap propMap_;

   public:
      //I_LogFactory() {}
      
      virtual ~I_LogFactory() {}

      /**
       * Will be called on registration of your factory. 
       * @param properties A map containing key/values of properties (command line args etc),
       *                   we keep a clone of it.
       */
      virtual void initialize(const PropMap& propMap) { propMap_ = propMap; };

      /**
       * Construct (if necessary) and return a Log instance, using the factory's current implementation. 
       * @param name Logical name of the Log instance to be returned
       *        (the meaning of this name is only known to the underlying logging implementation that is being wrapped) 
       */
      virtual I_Log& getLog(const std::string& name="") = 0;
      
      /**
       * Free resources for the given logger. 
       * @param name
       */
      virtual void releaseLog(const std::string& name="") = 0;

   }; // end of class I_LogFactory



}}} // end of namespace util

#endif // _ORG_XMLBLASTER_UTIL_I_LOGFACTORY_H
