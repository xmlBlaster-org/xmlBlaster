/*------------------------------------------------------------------------------
Name:      Global.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The global object (a stack for all pseudo static stuff).
Version:   $Id: Global.h,v 1.2 2002/12/06 13:55:58 laghi Exp $
------------------------------------------------------------------------------*/

#ifndef _UTIL_GLOBAL_H
#define _UTIL_GLOBAL_H

#include <util/xmlBlasterDef.h>
#include <util/Log.h>
#include <util/XmlBlasterException.h>
#include <util/Property.h>

#include <string>
#include <map>

using namespace std;

namespace org { namespace xmlBlaster { namespace util {


class Dll_Export HappyCompilerFriend
{
    /**
     * This class is a friend of Global to make the warnings of some
     * compilers disappear (since Global has private constructors and
     * destructors).
     */
};

/**
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
   class Dll_Export Global {

   typedef map<char*, Log> LogMap;

    friend class HappyCompilerFriend;

   private:
      const string        ME;
      LogMap              logMap_;
      Property*           property_;
      int                 args_;
      const char * const* argc_;

      /**
       * The default constructor is made private to implement the singleton
       * pattern.
       */
      Global();
      Global(const Global &global);
      Global& operator =(const Global &global);
      ~Global();

      void copy()
      {
         args_     = 0 ;
         argc_     = NULL;
         property_ = NULL;
      }

   public:

      int getArgs();

      const char * const* getArgc();


      /**
       * The method to call to get the singleton Timestamp object.
       */
      static Global& getInstance(const char* instanceName="default");
    
      /**
       * Constructs a current timestamp which is guaranteed to be unique in time for this JVM
       * @param delay the time in milliseconds from now the return value has to point to.
       * @exception RuntimeException on overflow (never happens :-=)
       */
      void initialize(int args=0, const char * const argc[]=0);

      /**
       * If no log is found with that name, one is created and added to the
       * log map.
       */
      Log& getLog(char* logName="default");

      /**
       * Returns the property object associated to this global
       */
      Property& getProperty();

      string getLocalIP() const;

      /**
       * Returns the bootstrap host name
       */
       string getBootstrapHostname();

   };

}}}; // namespace

#endif
