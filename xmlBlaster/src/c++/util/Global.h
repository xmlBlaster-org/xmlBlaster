/*------------------------------------------------------------------------------
Name:      Global.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The global object (a stack for all pseudo static stuff).
Version:   $Id$
------------------------------------------------------------------------------*/

#ifndef ORG_XMLBLASTER_UTIL_GLOBAL_H
#define ORG_XMLBLASTER_UTIL_GLOBAL_H

#include <util/xmlBlasterDef.h>
#include <util/I_Log.h>
#include <util/LogManager.h>
#include <util/XmlBlasterException.h>
#include <util/Property.h>
#include <util/SessionName.h>

#include <client/protocol/CbServerPluginManager.h>
#include <util/dispatch/DispatchManager.h>
#include <util/Timeout.h>

#include <string>
#include <map>

// for managed objects
#include <util/objman.h>

namespace org { namespace xmlBlaster { namespace util {

/**
 * Blocks until <enter> on the keyboard is hit. 
 * Consumes multiple hits (for Windows DOS box)
 * @param str If not null it will be printed on console with cout
 * @return The last entered line which contains something, previous lines and following empty lines are ignored
 */
Dll_Export std::string waitOnKeyboardHit(const std::string &str);

/**
 * Data holder of Properties dumped to argc/argv command line arguments
 * as passed to a main(). 
 * Example:
 * <pre>
 * argv[0] = myProg (the executable name) 
 * argv[1] = -name
 * argv[2] = joe
 * </pre>
 */
typedef struct ArgsStruct {
   int argc;
   char **argv;
} ArgsStruct_T;


/**
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
class Dll_Export Global {

friend Global& getInstance(const std::string &instanceName);

// required for managed objects
template <class TYPE> friend class ManagedObject;
friend class Object_Lifetime_Manager;

private:
   const std::string      ME;
   Property*              property_;
   int                    args_;
   const char * const*    argv_;
   bool                   isInitialized_;
   org::xmlBlaster::util::LogManager logManager_;
   org::xmlBlaster::client::protocol::CbServerPluginManager* cbServerPluginManager_;
   org::xmlBlaster::util::dispatch::DispatchManager* dispatchManager_;
   Timeout*               pingTimer_;
   std::string            id_;
   std::string            immutableId_;
   thread::Mutex          pingerMutex_;
   // added for managed objects.
   org::xmlBlaster::util::SessionNameRef sessionName_;
   static Global*         global_; // becomes pointer

   void copy();

public:
   /**
    * The default constructor is made private to implement the singleton
    * pattern.
    */
   Global();
   // Global(const Global &global);
   Global& operator =(const Global &);
   ~Global();


   /**
    * Returns the length of getArgs()
    */
   int getArgs();

   /**
    * Returns the original argv from main(). 
    * NOTE: This contains NOT all other properties,
    * you should in such a case use fillArgs(ArgsStruct_T)
    */
   const char * const* getArgc();

   /**
    * Fill all properties into a argc/argv representation similar
    * to those delivered by main(argc, argv). 
    * Example:
    * <pre>
    *  ArgsStruct_T args;
    *  glob.fillArgs(args);
    *  // Do something ...
    *  glob.freeArgs(args);
    * </pre>
    * <pre>
    *  argv[0] = myProg (the executable name) 
    *  argv[1] = -name
    *  argv[2] = joe
    * </pre>
    * NOTE: You have to take care on the memory allocated into args and free
    * it with a call to freeArgs()
    * @param args A struct ArgsStruct instance which is filled with all properties
    */
   void fillArgs(ArgsStruct_T &args);

   /**
    * Free the allocated memory
    * @see #fillArgs(ArgsStruct_T &args)
    */
   void freeArgs(ArgsStruct_T &args);

   /**
    * The method to call to get the singleton org::xmlBlaster::util::Timestamp object.
    */
   static Global& getInstance(const std::string &instanceName="default");

   /**
    * Allows you to query if user wants help.
    * @return true If '-help' or '-?' was passed to us
    */
   bool wantsHelp();

   /**
    * The version field is automatically set by ant on compilation (see filter token in build.xml)
    * @return The version std::string e.g. "0.842"
    *         or "@version@" if not set
    */
   static std::string& getVersion();

   /**
    * The subversion revision number field is automatically set by ant on compilation (see filter token in build.xml)
    * @return The revision number std::string e.g. "12708M"
    *         or getVersion() if not set
    */
   static std::string& getRevisionNumber();

	/**
	 * String containing getVersion() and getRevisionNumber()
	 * @return For example "0.91 #12107M"
	 */
	static std::string& getReleaseId();

   /**
    * The timestamp field is automatically set by ant on compilation (see filter token in build.xml)
    * @return The compilation timestamp of format "MM/dd/yyyy hh:mm aa"
    *         or "@build.timestamp@" if not set
    */
   static std::string& getBuildTimestamp();

   /**
    * The C++ compiler used. 
    * @return For example something like "Intel icc" for Intels C++ compiler
    */
   static std::string& getCompiler();

   /**
    * @return The protocol to use if not otherwise configured, currently "IOR" for CORBA
    *         and "SOCKET" for our native socket protocol are supported
    */
   static std::string& getDefaultProtocol();
 
    /**
    * Command line usage.
    */
   static std::string usage();

   /**
    * Intitialize with the given environment settings. 
    * @param argv The command line arguments, for example "-protocol SOCKET"
    */
   Global& initialize(int args=0, const char * const argv[]=0);

   /**
    * Intitialize with the given environment settings. 
    * @param propertyMap A std::map which contains key and values pairs,
    *                    for example key="protocol" and value="SOCKET"
    */
   Global& initialize(const org::xmlBlaster::util::Property::MapType &propertyMap);

   /**
    * Access the used LogManager. 
    */
   org::xmlBlaster::util::LogManager& getLogManager();

   /**
    * If no log is found with that name, one is created. 
	 * You should have initialized Global with your properties to
	 * before your first call to getLog(), for example:
	 * <pre>
	 * std::map<std::string, std::string, std::less<std::string> > propertyMap;
	 * ... // insert configuration key / values
    * Global& myGlobal = Global::getInstance().initialize(propertyMap);
	 * I_Log& log = myGlobal.getLog("BlasterClient"));
	 * ...
    * </pre>
    */
   org::xmlBlaster::util::I_Log& getLog(const std::string &logName="org.xmlBlaster");

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
     * Returns the specified value as a std::string. 
     * @deprecated Please use 'lexical_cast<string>(bool)' instead
     */
    static const std::string& getBoolAsString(bool val);

	 /**
	  * Set the clients session name, changes after login
	  */
	 void setSessionName(org::xmlBlaster::util::SessionNameRef sessionName);

	 /**
	  * Get the clients session name, changes after login
	  */
	 org::xmlBlaster::util::SessionNameRef getSessionName() const;

    /**
     * Access the unique local id (as a String), the absolute session name. 
     * Before connection it is temporay the relative session name
     * and changed to the absolute session name after connection (when we know the cluster id)
     * @return For example "/node/heron/client/joe/2"
     */
    std::string getId() const;

    /**
     * Same as getId() but all 'special characters' are stripped
     * so you can use it for file names.
     * @return ""
     */
    std::string getStrippedId() const;

    /**
     * The relative session name. 
     * We can't use the absolute session name (with cluster node informations)
     * as this is known after connect(), but we need to name queues
     * before connect() already -> The Id must be immutable!
     * @return For example "client/joe/2"
     */
    std::string getImmutableId() const;

    /**
     * Same as getImmutableId() but all 'special characters' are stripped
     * so you can use it for queue names.
     * @return ""
     */
    std::string getStrippedImmutableId() const;

    /**
     * Utility method to strip any std::string, all characters which prevent
     * to be used for e.g. file names are replaced. 
     * @param text e.g. "http://www.xmlBlaster.org:/home\\x"
     * @return e.g. "http_www_xmlBlaster_org_homex"
     */
    std::string getStrippedString(const std::string& text) const;

    /**
     * Set after successful login. 
     * Set by XmlBlasterAccess/ConnectionHandler to absolute session name
     * after successful connection (before it is temporary the relative name)
     * @param a unique id, for example "/node/heron/client/joe/2"
     */
    void setId(const std::string& id);
    
	 /**
	  * Set by XmlBlasterAccess/ConnectionHandler to relative session name
	  * @param a unique id, for example "client/joe/2"
	  */
	 void setImmutableId(const std::string& id);
};

}}}; // namespace

# endif // ORG_XMLBLASTER_UTIL_GLOBAL_H
