/*------------------------------------------------------------------------------
Name:      Global.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The global object (a stack for all pseudo static stuff).
Version:   $Id: Global.h,v 1.15 2003/02/21 11:25:43 ruff Exp $
------------------------------------------------------------------------------*/

#ifndef _UTIL_GLOBAL_H
#define _UTIL_GLOBAL_H

#include <util/xmlBlasterDef.h>
#include <util/Log.h>
#include <util/XmlBlasterException.h>
#include <util/Property.h>

#include <client/protocol/CbServerPluginManager.h>
#include <util/dispatch/DeliveryManager.h>
#include <util/Timeout.h>

#include <string>
#include <map>

using namespace std;
using namespace org::xmlBlaster::client::protocol;
using namespace org::xmlBlaster::util::dispatch;

namespace org { namespace xmlBlaster { namespace util {

/**
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
class Dll_Export Global {

typedef map<char*, Log> LogMap;

friend Global& getInstance(const char* instanceName);
private:
   const string           ME;
   LogMap                 logMap_;
   Property*              property_;
   int                    args_;
   const char * const*    argc_;
   bool                   isInitialized_;
   CbServerPluginManager* cbServerPluginManager_;
   DeliveryManager*       deliveryManager_;
   Timeout*               pingTimer_;
   string                 id_;

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
    * The method to call to get the singleton Timestamp object.
    */
   static Global& getInstance(const char* instanceName="default");

   /**
    * The version field is automatically set by ant on compilation (see filter token in build.xml)
    * @return The version string e.g. "0.842"
    *         or "@version@" if not set
    */
   static string& getVersion();

   /**
    * The timestamp field is automatically set by ant on compilation (see filter token in build.xml)
    * @return The compilation timestamp of format "MM/dd/yyyy hh:mm aa"
    *         or "@build.timestamp@" if not set
    */
   static string& getBuildTimestamp();
 
   /**
    * Constructs a current timestamp which is guaranteed to be unique in time for this JVM
    * @param delay the time in milliseconds from now the return value has to point to.
    * @exception RuntimeException on overflow (never happens :-=)
    */
   Global& initialize(int args=0, const char * const argc[]=0);

   /**
    * If no log is found with that name, one is created and added to the
    * log map.
    */
   Log& getLog(char* logName="default");

   /**
    * Returns the property object associated to this global
    */
   Property& getProperty() const;

   string getLocalIP() const;

   /**
    * Returns the bootstrap host name
    */
    string getBootstrapHostname() const;

    string getCbHostname() const;

    CbServerPluginManager& getCbServerPluginManager();

    DeliveryManager& getDeliveryManager();

    Timeout& getPingTimer();

    /**
     * returns the specified value as a string.
     */
    static const string& getBoolAsString(bool val);

    /**

     * Access the id (as a String) currently used on server side.
     * @return ""
     */
    string getId() const;

    /**
     * Same as getId() but all 'special characters' are stripped
     * so you can use it for file names.
     * @return ""
     */
    string getStrippedId() const;

    /**
     * Utility method to strip any string, all characters which prevent
     * to be used for e.g. file names are replaced. 
     * @param text e.g. "http://www.xmlBlaster.org:/home\\x"
     * @return e.g. "http_www_xmlBlaster_org_homex"
     */
    string getStrippedString(const string& text) const;

    /**
     * Currently set by engine.Global, used server side only.
     * @param a unique id
     */
    void setId(const string& id);
};

}}}; // namespace

#endif
