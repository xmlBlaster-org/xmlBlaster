/*------------------------------------------------------------------------------
Name:      Global.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Create unique timestamp
Version:   $Id: Global.cpp,v 1.55 2004/01/21 13:12:26 ruff Exp $
------------------------------------------------------------------------------*/
#include <client/protocol/CbServerPluginManager.h>
#include <util/dispatch/DispatchManager.h>
#include <util/Timeout.h>
#include <algorithm>
#include <util/lexical_cast.h>
#include <util/Global.h>

// For usage():
#include <util/qos/address/Address.h>
#include <util/qos/address/CallbackAddress.h>
#include <util/qos/storage/MsgUnitStoreProperty.h>
#include <util/qos/storage/ClientQueueProperty.h>
#include <util/qos/storage/CbQueueProperty.h>
#ifdef COMPILE_SOCKET_PLUGIN
#  include <client/protocol/socket/SocketDriver.h>
#endif
#ifdef COMPILE_CORBA_PLUGIN
#  include <client/protocol/corba/CorbaDriver.h>
#endif

#if defined(__GNUC__) || defined(__ICC)
   // To support query state with 'ident libxmlBlasterClient.so' or 'what libxmlBlasterClient.so'
   // or 'strings libxmlBlasterClient.so  | grep Global.cpp'
   static const char *rcsid_GlobalCpp  __attribute__ ((unused)) =  "@(#) $Id: Global.cpp,v 1.55 2004/01/21 13:12:26 ruff Exp $ xmlBlaster @version@";
#elif defined(__SUNPRO_CC)
   static const char *rcsid_GlobalCpp  =  "@(#) $Id: Global.cpp,v 1.55 2004/01/21 13:12:26 ruff Exp $ xmlBlaster @version@";
#endif

namespace org { namespace xmlBlaster { namespace util {
#if __GNUC__ == 2
  // Problems with g++ 2.95.3 and template<>
#else
/** Specialization for bool to return "true" instead of "1", see lexical_cast.h */
template<> std::string lexical_cast(bool arg)
{
   static const std::string _TRUE = "true";
   static const std::string _FALSE = "false";
   return (arg) ? _TRUE : _FALSE;
}
/** Specialization for bool to return "true" instead of "1", see lexical_cast.h */
template<> const char * lexical_cast(bool arg)
{
   static const char * const _TRUE = "true";
   static const char * const _FALSE = "false";
   return (arg) ? _TRUE : _FALSE;
}
#endif
using namespace std;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::client::protocol;

Global::Global() : ME("Global"), logMap_(), pingerMutex_() 
{
   cbServerPluginManager_ = 0;
   pingTimer_             = 0;
   dispatchManager_       = 0;
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
   argv_ = global.argv_;
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
      delete dispatchManager_;
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
     Object_Lifetime_Manager::instance()->manage_object(Constants::XB_GLOBAL_KEY, global_);  // if not pre-allocated.
   }
   return *global_;
}

Global& Global::initialize(int args, const char * const argv[])
{
   if (isInitialized_) {
      getLog("core").warn(ME, "::initialize: the global is already initialized. Ignoring this initialization");
      return *this;
   }
   args_     = args;
   argv_     = argv;
   if (property_ != NULL) delete property_;
   property_ = NULL;
   property_ = new Property(args, argv);
   isInitialized_ = true;
   return *this;
}

bool Global::wantsHelp()
{
   return property_->getBoolProperty("help", false, false) ||
          property_->getBoolProperty("-help", false, false) ||
          property_->getBoolProperty("h", false, false) ||
          property_->getBoolProperty("?", false, false); 
}

string &Global::getVersion()
{
   static string version = "@version@";  // is replaced by ant / build.xml to e.g. "0.85c"
   return version;
}

string &Global::getBuildTimestamp()
{
   static string timestamp = "@build.timestamp@"; // is replaced by ant / build.xml to e.g. "03/20/2003 10:22 PM";
   return timestamp;
}

string& Global::getCompiler()
{
   static string cppCompiler = "@cpp.compiler@"; // is replaced by ant / build.xml to e.g. "g++";
   return cppCompiler;
}


string& Global::getDefaultProtocol()
{
#  if COMPILE_CORBA_PLUGIN
   static string defaultProtocol = Constants::IOR;
#  elif COMPILE_SOCKET_PLUGIN
   static string defaultProtocol = Constants::SOCKET;
#  else
   log_.error(ME, "Missing protocol in getDefaultProtocol(), please set COMPILE_CORBA_PLUGIN or COMPILE_SOCKET_PLUGIN on compilation");
#  endif
   return defaultProtocol;
}

Property& Global::getProperty() const
{
   if (property_ == NULL)
     throw XmlBlasterException(USER_CONFIGURATION,
                               "UNKNOWN NODE",
                               ME + string("::getProperty"));
   return *property_;
}

string Global::usage()
{
   string sb;
   sb += "\n";
   sb += "\nXmlBlaster C++ client " + Global::getVersion() + " compiled at " + Global::getBuildTimestamp() + " with " + Global::getCompiler();
   sb += "\n";
//#  if COMPILE_SOCKET_PLUGIN && COMPILE_CORBA_PLUGIN
   sb += "\n   -protocol SOCKET | IOR";
   sb += "\n                       IOR for CORBA, SOCKET for our native protocol.";
   sb += "\n";
//#  endif
#  ifdef COMPILE_SOCKET_PLUGIN
      sb += org::xmlBlaster::client::protocol::socket::SocketDriver::usage();
      sb += "   -logLevel           ERROR | WARN | INFO | TRACE | DUMP [WARN]\n";
      sb += "\n";
#  endif
#  ifdef COMPILE_CORBA_PLUGIN
      sb += org::xmlBlaster::client::protocol::corba::CorbaDriver::usage();
      sb += "\n";
#  endif
   sb += org::xmlBlaster::util::qos::SessionQos::usage();
   sb += "\n";
   sb += org::xmlBlaster::util::qos::address::Address(Global::getInstance()).usage();
   sb += "\n";
   sb += org::xmlBlaster::util::qos::storage::ClientQueueProperty::usage();
   sb += "\n";
   //sb += org::xmlBlaster::util::qos::storage::MsgUnitStoreProperty::usage();
   //sb += "\n";
   sb += org::xmlBlaster::util::qos::address::CallbackAddress(Global::getInstance()).usage();
   sb += "\n";
   sb += org::xmlBlaster::util::qos::storage::CbQueueProperty::usage();
   sb += "\n";
   sb += org::xmlBlaster::util::Log::usage();
   return sb;
   /*
      StringBuffer sb = new StringBuffer(4028);
      sb.append(org.xmlBlaster.client.XmlBlasterAccess.usage(this));
      sb.append(logUsage());
      return sb.toString();
   */
}



Log& Global::getLog(const string &logName)
{
   LogMap::iterator pos = logMap_.find(logName);
   if (pos != logMap_.end()) return (*pos).second;

   Log help(getProperty(), args_, argv_, logName);
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
   return argv_;
}

string Global::getLocalIP() const
{
   // change this to a better way later ...
   return string("127.0.0.1");
}

string Global::getBootstrapHostname() const
{
   return getProperty().getStringProperty(string("bootstrapHostname"), getLocalIP());
   /* URL:
   string bootstrapHostname = getProperty().getStringProperty(string("bootstrapHostname"), getLocalIP());
   int bootstrapPort = getProperty().getIntProperty(string("bootstrapPort"), Constants::XMLBLASTER_PORT);
   return "http://" + bootstrapHostname + ":" + lexical_cast<std::string>(bootstrapPort);
   */
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

DispatchManager& Global::getDispatchManager()
{
   if (dispatchManager_ == NULL) {
      dispatchManager_ = new DispatchManager(*this);
   }
   return *dispatchManager_;
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

