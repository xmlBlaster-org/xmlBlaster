/*------------------------------------------------------------------------------
Name:      I_Plugin.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#ifndef _UTIL_PLUGIN_I_PLUGIN_H
#define _UTIL_PLUGIN_I_PLUGIN_H

#include <string>

namespace org { namespace xmlBlaster { namespace util { namespace plugin {

/**
 * Interface for all plugins. 
 * Note: Currently this interface marks plugins only, it is not
 * yet used to dynamically load shared libraries or dlls.
 * @author <a href='mailto:xmlblast@marcelruff.info'>Marcel Ruff</a>
 */
class Dll_Export I_Plugin
{
public:
   virtual ~I_Plugin() {};
    
   /**
    * Get the name of the plugin. 
    * @return For example "SOCKET", "IOR", "SQLite", "RAM", "XERCES", ...
    */
   virtual std::string getType() = 0;

   /**
    * Get the version of the plugin. 
    * @return For example "1.0"
    */
   virtual std::string getVersion() = 0;
};

}}}} // namespace

#endif

