/*------------------------------------------------------------------------------
Name:      Global.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The global object (a stack for all pseudo static stuff).
Version:   $Id: Global.h,v 1.24 2003/10/15 13:13:07 laghi Exp $
------------------------------------------------------------------------------*/

#ifndef _UTIL_GLOBAL_H
#define _UTIL_GLOBAL_H

#include <util/xmlBlasterDef.h>
#include <util/Log.h>
#include <util/XmlBlasterException.h>
#include <util/Property.h>

#include <client/protocol/CbServerPluginManager.h>
#include <util/dispatch/DispatchManager.h>
#include <util/Timeout.h>

#include <string>
#include <map>

// for managed objects
#include <util/objman.h>

namespace org { namespace xmlBlaster { namespace util {

/**
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
class Dll_Export Global {

typedef std::map<std::string, org::xmlBlaster::util::Log> LogMap;

friend Global& getInstance(const std::string &instanceName);

// required for managed objects
template <class TYPE> friend class ManagedObject;
friend class Object_Lifetime_Manager;

private:
   const std::string      ME;
   LogMap                 logMap_;
   Property*              property_;
   int                    args_;
   const char * const*    argc_;
   bool                   isInitialized_;
   org::xmlBlaster::client::protocol::CbServerPluginManager* cbServerPluginManager_;
   org::xmlBlaster::util::dispatch::DispatchManager* dispatchManager_;
   Timeout*               pingTimer_;
   std::string            id_;
   thread::Mutex          pingerMutex_;
   // added for managed objects.
   static Global*         global_; // becomes pointer

   /**
    * The default constructor is made private to implement the singleton
    * pattern.
    */
   Global();
   Global(const Global &global);
   Global& operator =(const Global &);
   ~Global();

   void copy()
   {
      args_      = 0 ;
      argc_      = NULL;
      property_  = NULL;
      pingTimer_ = NULL;
      id_        = "";
   }

public:

   int getArgs();

   const char * const* getArgc();

   /**
    * The method to call to get the singleton org::xmlBlaster::util::Timestamp object.
    */
   static Global& getInstance(const std::string &instanceName="default");

   /**
    * The version field is automatically set by ant on compilation (see filter token in build.xml)
    * @return The version std::string e.g. "0.842"
    *         or "@version@" if not set
    */
   static std::string& getVersion();

   /**
    * The timestamp field is automatically set by ant on compilation (see filter token in build.xml)
    * @return The compilation timestamp of format "MM/dd/yyyy hh:mm aa"
    *         or "@build.timestamp@" if not set
    */
   static std::string& getBuildTimestamp();
 
   /**
    * Constructs a current timestamp which is guaranteed to be unique in time for this JVM
    * @param delay the time in milliseconds from now the return value has to point to.
    * @exception RuntimeException on overflow (never happens :-=)
    */
   Global& initialize(int args=0, const char * const argc[]=0);

   /**
    * If no log is found with that name, one is created and added to the
    * log std::map.
    */
   org::xmlBlaster::util::Log& getLog(const std::string &logName="default");

   /**
    * Returns the property object associated to this global
    */
   Property& getProperty() const;

   std::string getLocalIP() const;

   /**
    * Returns the bootstrap host name
    */
    std::string getBootstrapHostname() const;

    std::string getCbHostname() const;

    org::xmlBlaster::client::protocol::CbServerPluginManager& getCbServerPluginManager();

    org::xmlBlaster::util::dispatch::DispatchManager& getDispatchManager();

    Timeout& getPingTimer();

    /**
     * returns the specified value as a std::string.
     */
    static const std::string& getBoolAsString(bool val);

    /**

     * Access the id (as a String) currently used on server side.
     * @return ""
     */
    std::string getId() const;

    /**
     * Same as getId() but all 'special characters' are stripped
     * so you can use it for file names.
     * @return ""
     */
    std::string getStrippedId() const;

    /**
     * Utility method to strip any std::string, all characters which prevent
     * to be used for e.g. file names are replaced. 
     * @param text e.g. "http://www.xmlBlaster.org:/home\\x"
     * @return e.g. "http_www_xmlBlaster_org_homex"
     */
    std::string getStrippedString(const std::string& text) const;

    /**
     * Currently set by engine.Global, used server side only.
     * @param a unique id
     */
    void setId(const std::string& id);
};

}}}; // namespace

#endif
