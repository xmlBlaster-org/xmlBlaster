/*-----------------------------------------------------------------------------
Name:      StringStripper2.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to strip a std::string containing two kinds of separators into a 
           std::vector of pairs of std::strings.
Author:    <Michele Laghi> laghi@swissinfo.org
-----------------------------------------------------------------------------*/

#ifndef _UTIL_STRINGSTRIPPER2_H
#define _UTIL_STRINGSTRIPPER2_H

// #ifdef _WIN32
// #  include <_pair.h>
// #endif
#include <util/StringStripper.h>


/**
 * StringStripper2 is a class used to strip a std::string into a std::vector of pairs
 * of std::strings. It needs two separators, a major one and a minor one.
 *
 * Lets explain it with an example:
 * * if you have set the (default) separators: major sep: "/" and a minor
 * sep: "." and a std::string to strip: "ti.che/ta.tacat/i.tac/tacum.i.tac"
 * then the external strip would divide the std::string into a std::vector containing
 * the following four std::strings: "ti.che", "ta.tacat", "i.tac", "tacumi.tac".
 * The internal strip (the minor strip) would further divide all these std::strings
 * into pairs of std::strings. In the last std::string there are two separators. The 
 * std::string will be divided so that the part of the std::string before the last 
 * separator will be the first element in the pair. The result will be:
 * <"ti","che">,<"ta","tacat">,<"i","tac">,<"tacumi","tac">
 */


namespace org { namespace xmlBlaster {
namespace util {
   
   class Dll_Export StringStripper2 {
      
   private:
      StringStripper mainStrip_, minorStrip_;
      
   public:

      StringStripper2(const std::string &mainSeparator="/", 
                      const std::string &minorSeparator=".") 
         : mainStrip_(mainSeparator), minorStrip_(minorSeparator) {
      }


      std::vector<std::pair<std::string,std::string> > strip(const std::string &line) {

         std::vector<std::string>               mainVector = mainStrip_.strip(line);
         std::string::size_type            vectorSize;
         std::pair<std::string,std::string>          namePair;
         std::vector<std::pair<std::string,std::string> > ret;
         
         for (std::string::size_type i=0; i < mainVector.size(); i++) {
            std::vector<std::string> minorVector = minorStrip_.strip(mainVector[i]);

            if ( (vectorSize = minorVector.size()) > 1) {
               std::string name = "";
               for (std::string::size_type j=0; j<(vectorSize-1); j++) name += minorVector[j];
               namePair = std::pair<std::string,std::string>(name,minorVector[vectorSize-1]);
            }

            else {
               if (vectorSize == 1) 
                  namePair = std::pair<std::string,std::string>(minorVector[0],"");
               else 
                  namePair = std::pair<std::string,std::string>("","");
            }

            ret.insert(ret.end(), namePair);

         }

         return ret; 

      }

   };
}}} // namespace

#endif
