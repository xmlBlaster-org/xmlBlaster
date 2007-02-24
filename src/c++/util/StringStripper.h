/*-----------------------------------------------------------------------------
Name:      StringStripper.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to strip a std::string containing separators into a std::vector
Author:    <Michele Laghi> laghi@swissinfo.org
-----------------------------------------------------------------------------*/

#ifndef _UTIL_STRINGSTRIPPER_H
#define _UTIL_STRINGSTRIPPER_H

#include <string>
#include <vector>
#include <util/XmlBCfg.h>



namespace org { namespace xmlBlaster {
namespace util {
   
/**
 * Class StringStripper is used to strip a std::string with separators into a std::vector
 * of (separated) std::strings. It is mostly used to strip a name to be used in a
 * name server. An example could be to strip the following name:
 *
 * <pre>
 * std::string name = "motor.electric.stepper.motor1";
 * std::vector<std::string> vec = StringStripper(".").strip(name);
 * </pre>
 */
   class Dll_Export StringStripper {
      
   private:
      std::string separator_;
      int    sepSize_;
   public:
      
      StringStripper(const std::string &separator) {
         separator_ = separator;
         sepSize_   = (int)separator_.length();
      }
      
      /** 
       * strip strips the std::string into a std::vector of std::strings. If the input 
       * std::string terminates with a separator, then the last std::string in the
       * std::vector will be empty. No separator appears in the return std::strings.
       */
      std::vector<std::string> strip(std::string line) {
         std::vector<std::string> ret;
         std::string         sub;
         int            pos;
         while ((pos=(int)line.find(separator_)) >= 0) {
            sub.assign(line,0, pos);
            line = line.substr(pos+sepSize_);
            ret.insert(ret.end(), sub);
         }
         ret.insert(ret.end(), line);
         return ret;
      }
      
   };
}}} // namespace

#endif

