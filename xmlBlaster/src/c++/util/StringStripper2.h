/*-----------------------------------------------------------------------------
Name:      StringStripper2.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to strip a string containing two kinds of separators into a 
           vector of pairs of strings.
Author:    <Michele Laghi> laghi@swissinfo.org
-----------------------------------------------------------------------------*/

#ifndef _UTIL_STRINGSTRIPPER2_H
#define _UTIL_STRINGSTRIPPER2_H

// #ifdef _WIN32
// #  include <_pair.h>
// #endif
#include <util/StringStripper.h>


/**
 * StringStripper2 is a class used to strip a string into a vector of pairs
 * of strings. It needs two separators, a major one and a minor one.
 *
 * Lets explain it with an example:
 * * if you have set the (default) separators: major sep: "/" and a minor
 * sep: "." and a string to strip: "ti.che/ta.tacat/i.tac/tacum.i.tac"
 * then the external strip would divide the string into a vector containing
 * the following four strings: "ti.che", "ta.tacat", "i.tac", "tacumi.tac".
 * The internal strip (the minor strip) would further divide all these strings
 * into pairs of strings. In the last string there are two separators. The 
 * string will be divided so that the part of the string before the last 
 * separator will be the first element in the pair. The result will be:
 * <"ti","che">,<"ta","tacat">,<"i","tac">,<"tacumi","tac">
 */
using namespace std;

namespace org { namespace xmlBlaster {
namespace util {
   
   class Dll_Export StringStripper2 {
      
   private:
      StringStripper mainStrip_, minorStrip_;
      
   public:

      StringStripper2(const string &mainSeparator="/", 
                      const string &minorSeparator=".") 
         : mainStrip_(mainSeparator), minorStrip_(minorSeparator) {
      }


      vector<pair<string,string> > strip(const string &line) {

         vector<string>               mainVector = mainStrip_.strip(line);
         string::size_type            vectorSize;
         pair<string,string>          namePair;
         vector<pair<string,string> > ret;
         
         for (string::size_type i=0; i < mainVector.size(); i++) {
            vector<string> minorVector = minorStrip_.strip(mainVector[i]);

            if ( (vectorSize = minorVector.size()) > 1) {
               string name = "";
               for (string::size_type j=0; j<(vectorSize-1); j++) name += minorVector[j];
               namePair = pair<string,string>(name,minorVector[vectorSize-1]);
            }

            else {
               if (vectorSize == 1) 
                  namePair = pair<string,string>(minorVector[0],"");
               else 
                  namePair = pair<string,string>("","");
            }

            ret.insert(ret.end(), namePair);

         }

         return ret; 

      }

   };
}}} // namespace

#endif
