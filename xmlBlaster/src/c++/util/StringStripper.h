/*-----------------------------------------------------------------------------
Name:      StringStripper.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to strip a string containing separators into a vector
Version:   $Id: StringStripper.h,v 1.2 2000/07/06 23:42:27 laghi Exp $
Author:    <Michele Laghi> michele.laghi@attglobal.net
-----------------------------------------------------------------------------*/

#ifndef _UTIL_STRINGSTRIPPER_H
#define _UTIL_STRINGSTRIPPER_H

#include <string>
#include <vector>

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
   class StringStripper {
      
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
	    line.assign(line, pos+sepSize_);
	    ret.insert(ret.end(), sub);
  	 }
	 ret.insert(ret.end(), line);
	 return ret;
      }
      
   };
};

#endif

