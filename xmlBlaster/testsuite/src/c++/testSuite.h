/*-----------------------------------------------------------------------------
Name:      testSuite.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper functions for the testsuite
-----------------------------------------------------------------------------*/

#ifndef _TESTSUITE_H
#define _TESTSUITE_H

#include <util/Log.h>
#include <boost/lexical_cast.hpp>
#include <string>

using boost::lexical_cast;

using namespace std;
using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster {


template <class T> 
void assertEquals(Log& log, const string& who, const T& should, const T& is, const string& txt)
{
   if (should != is) {
      log.error(who, txt + " FAILED: value is " + lexical_cast<string>(is) + "' but should be '" + lexical_cast<string>(should) + "'");
      assert(0);
   }
   else {
      log.info(who, txt + " OK");
   }
}

template <class T> 
void assertDifferes(Log& log, const string& who, const T& should, const T& is, const string& txt)
{
   if (should == is) {
      log.error(who, txt + " FAILED: value is " + lexical_cast<string>(is) + "' in both cases but they should be different");
      assert(0);
   }
   else {
      log.info(who, txt + " OK");
   }
}


// specific implementation for the string since the lexical_cast from string to string causes problems.

void assertEquals(Log& log, const string& who, const string& should, const string& is, const string& txt)
{
   if (should != is) {
      log.error(who, txt + " FAILED: value is " + is + "' but should be '" + should + "'");
      assert(0);
   }
   else {
      log.info(who, txt + " OK");
   }
}

void assertDifferes(Log& log, const string& who, const string& should, const string& is, const string& txt)
{
   if (should == is) {
      log.error(who, txt + " FAILED: value is " + is + "' for both cases but they should be different");
      assert(0);
   }
   else {
      log.info(who, txt + " OK");
   }
}


}} // namespace


#endif


