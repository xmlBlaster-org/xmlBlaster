/*------------------------------------------------------------------------------
Name:      Global.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Create unique timestamp
Version:   $Id: Global.cpp,v 1.26 2003/02/25 19:02:06 ruff Exp $
------------------------------------------------------------------------------*/
#include <client/protocol/CbServerPluginManager.h>
#include <util/dispatch/DeliveryManager.h>
#include <util/Timeout.h>
#include <algorithm>
#include <util/lexical_cast.h>
#include <util/Global.h>

#ifdef _WINDOWS
   // how to do it?
#else
   // To support query state with 'ident libxmlBlaster.so' or 'what libxmlBlaster.so'
   static char *rcsid_GlobalCpp  __attribute__ ((unused)) =  "@(#) $Id: Global.cpp,v 1.26 2003/02/25 19:02:06 ruff Exp $";
#endif

using namespace std;

using namespace org::xmlBlaster::client::protocol;

namespace org { namespace xmlBlaster { namespace util {

Global::Global() : ME("Global"), logMap_()
{
   cbServerPluginManager_ = NULL;
   copy();
   isInitialized_ = false;
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
      logMap_.erase(logMap_.begin(), logMap_.end());
      delete property_;
      delete cbServerPluginManager_;
      delete pingTimer_;
   }
   catch (...) {
   }
}

Global& Global::getInstance(const char*)
{
   static Global global;
   return global;
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
   static string version = "@version@";
   if ( version[0] == '@' ) {
      version = "0.842";
   }
   return version;
}

string &Global::getBuildTimestamp()
{
   static string timestamp = "@build.timestamp@";
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


Log& Global::getLog(char* logName)
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
   string hostname = getProperty().getStringProperty(string("hostname"), getLocalIP());
   int port     = getProperty().getIntProperty(string("port"), Constants::XMLBLASTER_PORT);
   return "http://" + hostname + ":" + lexical_cast<string>(port);
}

string Global::getCbHostname() const
{
//   std::cout << "Global::getCbHostname implementation is not finished" << std::endl;
   return getProperty().getStringProperty(string("hostname"), getLocalIP());
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
   pingTimer_ = new Timeout(*this, string("ping timer"));
   pingTimer_->start();
   return *pingTimer_;
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

}}}; // namespace

