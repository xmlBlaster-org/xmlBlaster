/*------------------------------------------------------------------------------
Name:      Global.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Create unique timestamp
Version:   $Id: Global.cpp,v 1.42 2003/07/17 17:38:25 ruff Exp $
------------------------------------------------------------------------------*/
#include <client/protocol/CbServerPluginManager.h>
#include <util/dispatch/DeliveryManager.h>
#include <util/Timeout.h>
#include <algorithm>
#include <util/lexical_cast.h>
#include <util/Global.h>

#if defined(__GNUC__) || defined(__ICC)
   // To support query state with 'ident libxmlBlaster.so' or 'what libxmlBlaster.so'
   static const char *rcsid_GlobalCpp  __attribute__ ((unused)) =  "@(#) $Id: Global.cpp,v 1.42 2003/07/17 17:38:25 ruff Exp $";
#elif defined(__SUNPRO_CC)
   static const char *rcsid_GlobalCpp  =  "@(#) $Id: Global.cpp,v 1.42 2003/07/17 17:38:25 ruff Exp $";
#endif

namespace org { namespace xmlBlaster { namespace util {

using namespace std;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::client::protocol;

Global::Global() : ME("Global"), logMap_(), pingerMutex_() 
{
   cbServerPluginManager_ = 0;
   pingTimer_             = 0;
   deliveryManager_       = 0;
   property_              = 0;
   copy();
   isInitialized_ = false;

   if(global_ == NULL)
     global_ = this;
}

/*
Global::Global(const Global& global) : ME("Global"), logMap_(global.logMap_)
{
   args_ = global.args_;
   argc_ = global.argc_;
}
*/
Global& Global::operator =(const Global &)
{
   copy();
   return *this;
}


Global::~Global()
{
   try {
      delete property_;
      delete cbServerPluginManager_;
      delete pingTimer_;
      logMap_.erase(logMap_.begin(), logMap_.end());
   }
   catch (...) {
   }
}

/*
Global& Global::getInstance(const string&)
{
   static Global global;
   return global;
}
*/

Global *Global::global_ = NULL;

//-----------------
// Global.cpp modification
Global& Global::getInstance(const string&)
{
   if(global_ == NULL) {
     global_ = new Global();
     Object_Lifetime_Manager::instance()->manage_object(global_);  // if not pre-allocated.
   }
   return *global_;
}

Global& Global::initialize(int args, const char * const argc[])
{
   if (isInitialized_) {
      getLog("core").warn(ME, "::initialize: the global is already initialized. Ignoring this initialization");
      return *this;
   }
   args_     = args;
   argc_     = argc;
   if (property_ != NULL) delete property_;
   property_ = NULL;
   property_ = new Property(args, argc);
   isInitialized_ = true;
   return *this;
}

string &Global::getVersion()
{
   static string version = "@version@";  // is replaced by ant / build.xml to e.g. "0.849"
   return version;
}

string &Global::getBuildTimestamp()
{
   static string timestamp = "@build.timestamp@"; // is replaced by ant / build.xml to e.g. "03/20/2003 10:22 PM";
   return timestamp;
}

Property& Global::getProperty() const
{
   if (property_ == NULL)
     throw XmlBlasterException(USER_CONFIGURATION,
                               "UNKNOWN NODE",
                               ME + string("::getProperty"));
   return *property_;
}


Log& Global::getLog(const string &logName)
{
   LogMap::iterator pos = logMap_.find(logName);
   if (pos != logMap_.end()) return (*pos).second;

   Log help(getProperty(), args_, argc_, logName);
   help.initialize();
   logMap_.insert(LogMap::value_type(logName, help));
   pos = logMap_.find(logName);
   if (pos != logMap_.end()) {
      Log* log = &(*pos).second;
      return *log;
   }

  // if it reaches this point, then a serious error occured
  throw XmlBlasterException(INTERNAL_UNKNOWN, "UNKNOWN NODE", ME + string("::getLog"));
}

int Global::getArgs()
{
   return args_;
}

const char * const* Global::getArgc()
{
   return argc_;
}

string Global::getLocalIP() const
{
   // change this to a better way later ...
   return string("127.0.0.1");
}

string Global::getBootstrapHostname() const
{
   string bootstrapHostname = getProperty().getStringProperty(string("bootstrapHostname"), getLocalIP());
   int bootstrapPort = getProperty().getIntProperty(string("bootstrapPort"), Constants::XMLBLASTER_PORT);
   return "http://" + bootstrapHostname + ":" + lexical_cast<std::string>(bootstrapPort);
}

string Global::getCbHostname() const
{
//   std::cout << "Global::getCbHostname implementation is not finished" << std::endl;
   return getProperty().getStringProperty(string("bootstrapHostname"), getLocalIP());
}

CbServerPluginManager& Global::getCbServerPluginManager()
{
   if (cbServerPluginManager_ == NULL) {
      cbServerPluginManager_ = new CbServerPluginManager(*this);
   }
   return *cbServerPluginManager_;
}

DeliveryManager& Global::getDeliveryManager()
{
   if (deliveryManager_ == NULL) {
      deliveryManager_ = new DeliveryManager(*this);
   }
   return *deliveryManager_;
}

Timeout& Global::getPingTimer()
{
   if (pingTimer_) return *pingTimer_;
   thread::Lock lock(pingerMutex_);
   { // this is synchronized. Test again if meanwhile it has been set ...
      getLog("core").info(ME, "::getPingTimer: creating the singleton 'ping timer'");
      if (pingTimer_) return *pingTimer_;
      pingTimer_ = new Timeout(*this, string("ping timer"));
      return *pingTimer_;
   }
}

const string& Global::getBoolAsString(bool val)
{
   static const string _TRUE = "true";
   static const string _FALSE = "false";
   if (val) return _TRUE;
   else return _FALSE;
}


/**
 * Access the id (as a String) currently used on server side.
 * @return ""
 */
string Global::getId() const
{
   return id_;
}

/**
 * Same as getId() but all 'special characters' are stripped
 * so you can use it for file names.
 * @return ""
 */
string Global::getStrippedId() const
{
   return getStrippedString(id_);
}

/**
 * Utility method to strip any string, all characters which prevent
 * to be used for e.g. file names are replaced. 
 * @param text e.g. "http://www.xmlBlaster.org:/home\\x"
 * @return e.g. "http_www_xmlBlaster_org_homex"
 */
string Global::getStrippedString(const string& text) const
{
   string ret = text;
   string::iterator
     ref = remove(ret.begin(), ret.end(), '/'); // StringHelper.replaceAll(text, "/", "");
   replace(ret.begin(), ref, '.', '_');         // StringHelper.replaceAll(strippedId, ".", "_");
   replace(ret.begin(), ref, ':', '_');         // StringHelper.replaceAll(strippedId, ":", "_");
   ref = remove(ret.begin(), ref, '\\');        // StringHelper.replaceAll(strippedId, "\\", "");
   ret.erase(ref, ret.end());
   return ret;
}       

/**
 * Currently set by engine.Global, used server side only.
 * @param a unique id
 */
void Global::setId(const string& id) 
{
   id_ = id;
}

}}} // namespace

