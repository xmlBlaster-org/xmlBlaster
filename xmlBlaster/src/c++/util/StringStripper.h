/*-----------------------------------------------------------------------------
Name:      StringStripper.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to strip a string containing separators into a vector
Author:    <Michele Laghi> laghi@swissinfo.org
-----------------------------------------------------------------------------*/

#ifndef _UTIL_STRINGSTRIPPER_H
#define _UTIL_STRINGSTRIPPER_H

#include <string>
#include <vector>
#include <util/XmlBCfg.h>

using namespace std;

namespace org { namespace xmlBlaster {
namespace util {
   
/**
 * Class StringStripper is used to strip a string with separators into a vector
 * of (separated) strings. It is mostly used to strip a name to be used in a
 * name server. An example could be to strip the following name:
 *
 * <pre>
 * string name = "motor.electric.stepper.motor1";
 * StringStripper stripper = new StringStripper(".");
 * vector<String> vec = stripper->strip(name);
 * </pre>
 */
   class Dll_Export StringStripper {
      
   private:
      string separator_;
      int    sepSize_;
   public:
      
      StringStripper(const string &separator) {
         separator_ = separator;
         sepSize_   = separator_.length();
      }
      
      /** 
       * strip strips the string into a vector of strings. If the input 
       * string terminates with a separator, then the last string in the
       * vector will be empty. No separator appears in the return strings.
       */
      vector<string> strip(string line) {
         vector<string> ret;
         string         sub;
         int            pos;
         while ((pos=line.find(separator_)) >= 0) {
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

