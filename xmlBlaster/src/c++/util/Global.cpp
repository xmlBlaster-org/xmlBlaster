/*------------------------------------------------------------------------------
Name:      Global.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Create unique timestamp
Version:   $Id$
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
#include <util/parser/Sax2Parser.h>
#ifdef COMPILE_SOCKET_PLUGIN
#  include <client/protocol/socket/SocketDriver.h>
#endif
#ifdef COMPILE_CORBA_PLUGIN
#  include <client/protocol/corba/CorbaDriver.h>
#endif
#ifdef XMLBLASTER_COMPILE_LOG4CPLUS_PLUGIN
#  include<util/Log4cplus.h>
#endif

#if !defined(XMLBLASTER_NO_RCSID)
   /*
   Add the exact version of the C++ client library, this is for examination
   with for example the UNIX 'strings' command only.
   If it makes problem just set -DXMLBLASTER_NO_RCSID
   */
#  if defined(__GNUC__) || defined(__ICC)
      // To support query state with 'ident libxmlBlasterClient.so' or 'what libxmlBlasterClient.so'
      // or 'strings libxmlBlasterClient.so  | grep Global.cpp'
      static const char *rcsid_GlobalCpp  __attribute__ ((unused)) =  "@(#) $Id$ xmlBlaster @version@ #@revision.number@";
#  elif defined(__SUNPRO_CC)
      static const char *rcsid_GlobalCpp  =  "@(#) $Id$ xmlBlaster @version@ #@revision.number@";
#  endif
#endif

namespace org { namespace xmlBlaster { namespace util {
//#if __GNUC__ == 2 || defined(__sun)
#if __GNUC__ == 2 || defined(__SUNPRO_CC)
//#if __GNUC__ == 2
  // Problems with g++ 2.95.3 and template<>
#else
/** Specialization for bool to return "true" instead of "1", see lexical_cast.h */
template<> std::string lexical_cast(bool arg)
{
   return (arg) ? XMLBLASTER_TRUE : XMLBLASTER_FALSE; // "true", "false"
}
/** Specialization for bool to return "true" instead of "1", see lexical_cast.h */
template<> const char * lexical_cast(bool arg)
{
   return (arg) ? XMLBLASTER_TRUE.c_str() : XMLBLASTER_FALSE.c_str();
}

template<> bool lexical_cast(std::string arg)
{
   return arg == "1" || arg == XMLBLASTER_TRUE || arg == "TRUE"; // "true"
}

template<> bool lexical_cast(const char* arg)
{
   return lexical_cast<bool>(std::string(arg));
}


/** Specialization for std::string to return std::string as default impl. crashes on sun with gcc 
    Don't use template<> std::string lexical_cast(const std::string& arg) since it won't be 
    invoked (instead the default template will be invoked)
*/
template<> std::string lexical_cast(std::string arg)
{
   return arg;
}

#endif
using namespace std;
using namespace org::xmlBlaster::util::dispatch;
using namespace org::xmlBlaster::client::protocol;

Global::Global() : ME("Global"), pingerMutex_(), sessionName_(0)
{
   cbServerPluginManager_ = 0;
   pingTimer_             = 0;
   dispatchManager_       = 0;
   copy();
   property_              = new Property();
   isInitialized_ = false;

   if(global_ == NULL)
     global_ = this;
}

void Global::copy()
{
   args_        = 0 ;
   argv_        = NULL;
   property_    = NULL;
   pingTimer_   = NULL;
   dispatchManager_ = NULL;
   cbServerPluginManager_ = NULL;
   id_          = "";
}

/*
Global::Global(const Global& global) : ME("Global")
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
   }
   catch (...) {
   }
   try {
      if (this != global_) {
         global_->destroyInstance(this->getInstanceName());
      }
      else {
         globalRefMap_.clear();
         globalMap_.clear();
      }
   }
   catch (...) {
   }
}

Global *Global::global_ = NULL;
Global::GlobalRefMap Global::globalRefMap_;
Global::GlobalMap Global::globalMap_;
thread::Mutex Global::globalMutex_;

//-----------------
// Global.cpp modification
Global& Global::getInstance(string name)
{
   if (name == "" || name == "default") {
      if (global_ == NULL) {
         global_ = new Global();
         Object_Lifetime_Manager::instance()->manage_object(Constants::XB_GLOBAL_KEY, global_);  // if not pre-allocated.
      }
      return *global_;
   }

   GlobalMap::iterator iter = globalMap_.find(name);
   if (iter != globalMap_.end()) {
      Global* glP = (*iter).second;
      return *glP;
   }

   throw XmlBlasterException(USER_ILLEGALARGUMENT,
         "UNKNOWN NODE",
         string("Global::getInstance"),
            "The Global instance '" + name + "' is not known");
}

GlobalRef Global::createInstance(const string& name, const Property::MapType *propertyMapP, bool holdReferenceCount)
{
   if (name == "") {
     throw XmlBlasterException(USER_ILLEGALARGUMENT,
                  "UNKNOWN NODE",
                  string("Global::createInstance"),
                   "Please call createInstance with none empty name argument");
   }
   if (name == "default") {
     throw XmlBlasterException(USER_ILLEGALARGUMENT,
                  "UNKNOWN NODE",
                  string("Global::createInstance"),
                   "Please call getInstance to access the 'default' Global instance");
   }

   thread::Lock lock(globalMutex_);

   {
      GlobalRefMap::iterator iter = globalRefMap_.find(name);
      if (iter != globalRefMap_.end()) {
         GlobalRef gr = (*iter).second;
         return gr;
      }
   }
   {
      GlobalMap::iterator iter = globalMap_.find(name);
      if (iter != globalMap_.end()) {
         //Global* glP = (*iter).second;
         throw XmlBlasterException(USER_ILLEGALARGUMENT,
                     "UNKNOWN NODE",
                     string("Global::createInstance"),
                      "Please call getInstance to access the '" + name + "' Global instance");
      }
   }

   if (holdReferenceCount) {
      GlobalRef globRef = GlobalRef(new Global());
      globRef->instanceName_ = name;
      if (propertyMapP != NULL) {
         globRef->initialize(*propertyMapP);
      }
      GlobalRefMap::value_type el(name, globRef);
      globalRefMap_.insert(el);
      return globRef;
   }
   else {
      Global* glP = new Global();
      glP->instanceName_ = name;
      if (propertyMapP != NULL) {
         glP->initialize(*propertyMapP);
      }
      GlobalMap::value_type el(name, glP);
      globalMap_.insert(el);
      return GlobalRef(glP);
   }
}

const string& Global::getInstanceName()
{
   if (this->instanceName_ == "" && this == global_) {
      this->instanceName_ = "default";
   }
   return this->instanceName_;
}

bool Global::containsInstance(const std::string &name)
{
   bool ret = globalRefMap_.count(name) != 0;
   if (ret) {
      return true;
   }
   return globalMap_.count(name) != 0;
}

bool Global::destroyInstance(const std::string &name)
{
   if (name == "") {
      return false;
   }
   if (name == "default") {
     throw XmlBlasterException(USER_ILLEGALARGUMENT,
                  "UNKNOWN NODE",
                  string("Global::destroyInstance"),
                   "The 'default' Global instance is handled by the life time manager and can't be destroyed manually");
   }
   thread::Lock lock(globalMutex_);
   {
      bool ret = globalRefMap_.count(name) != 0;
      if (ret) {
         globalRefMap_.erase(name);
         //cout << "DEBUG ONLY: " << name << " RefSize=" + lexical_cast<std::string>(globalRefMap_.size()) <<
         //     " Size=" + lexical_cast<std::string>(globalMap_.size()) << endl;
         return ret;
      }
   }
   {
      bool ret = globalMap_.count(name) != 0;
      if (ret) {
         globalMap_.erase(name);
         //cout << "DEBUG ONLY: " << name << " Size=" + lexical_cast<std::string>(globalMap_.size()) <<
         //     " RefSize=" + lexical_cast<std::string>(globalRefMap_.size()) << endl;
         return ret;
      }
   }
   return false;
}

Global& Global::initialize(int args, const char * const argv[])
{
   if (isInitialized_) {
      getLog("org.xmlBlaster.util").warn(ME, "::initialize: the global is already initialized. Ignoring this initialization");
      return *this;
   }
   args_     = args;
   argv_     = argv;
   if (property_ != NULL) delete property_;
   property_ = NULL;
   property_ = new Property(args, argv);
   property_->loadPropertyFile(); // load xmlBlaster.properties
   isInitialized_ = true;
   return *this;
}

Global& Global::initialize(const Property::MapType &propertyMap)
{
   if (isInitialized_) {
      getLog("org.xmlBlaster.util").warn(ME, "::initialize: the global is already initialized. Ignoring this initialization");
      return *this;
   }
   args_     = 0;
   argv_     = 0;
   if (property_ != NULL) delete property_;
   property_ = NULL;
   property_ = new Property(propertyMap);
   property_->loadPropertyFile(); // load xmlBlaster.properties
   isInitialized_ = true;
   return *this;
}

void Global::fillArgs(ArgsStruct_T &args)
{
   if (property_ == 0) {
      args.argc = 0;
      args.argv = 0;
      return;
   }
   const Property::MapType &prmap = property_->getPropertyMap();
   args.argc = 2*prmap.size()+1;
   args.argv = new char *[args.argc];

   string execName = (argv_ != 0 && args_ > 0) ? argv_[0] : "xmlBlasterClient";
   args.argv[0] = new char[execName.length()+1];
   strcpy(args.argv[0],execName.c_str());
   int i = 1;
   Property::MapType::const_iterator ipm;
   for (ipm = prmap.begin(); ipm != prmap.end(); ++ipm) {
      args.argv[i] = new char[(*ipm).first.size()+2];
      *(args.argv[i]) = '-';
      strcpy(args.argv[i]+1, (*ipm).first.c_str()); i++;
      args.argv[i] = new char[(*ipm).second.size()+1];
      strcpy(args.argv[i], (*ipm).second.c_str()); i++;
   }
}

void Global::freeArgs(ArgsStruct_T &args)
{
   for (size_t i=0; i<args.argc; i++)
      delete [] args.argv[i];
   delete [] args.argv;
   args.argc = 0;
}

bool Global::wantsHelp()
{
   return getProperty().getBoolProperty("help", false, false) ||
          getProperty().getBoolProperty("-help", false, false) ||
          getProperty().getBoolProperty("h", false, false) ||
          getProperty().getBoolProperty("?", false, false); 
}

string &Global::getVersion()
{
   static string version = "@version@";  // is replaced by ant / build.xml to e.g. "0.901"
   return version;
}

string &Global::getRevisionNumber()
{
   static string revisionNumber = "@revision.number@";  // is replaced by ant / build.xml to subversions revision number, e.g. "1207"
   if (revisionNumber.find("@",0) != 0 && revisionNumber!=string("${revision.number}"))
      return revisionNumber;
   return getVersion();
}

string &Global::getReleaseId()
{
   if (Global::getVersion() == Global::getRevisionNumber())
      return Global::getVersion();
   static string releaseId = Global::getVersion() + " #" + Global::getRevisionNumber();
        return releaseId;
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


/**
 * Is overwritten be environment, for example  "-protocol IOR"
 */
string& Global::getDefaultProtocol()
{
#  if COMPILE_SOCKET_PLUGIN
   static string defaultProtocol = Constants::SOCKET;
#  elif COMPILE_CORBA_PLUGIN
   static string defaultProtocol = Constants::IOR;
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
                               ME + string("::getProperty"), "Please call initialize to init Property");
   return *property_;
}

string Global::usage()
{
   string sb;
   sb += "\n";
   sb += "\nXmlBlaster C++ client " + Global::getReleaseId() + " compiled at " + Global::getBuildTimestamp() + " with " + Global::getCompiler();
   sb += "\n";
//#  if COMPILE_SOCKET_PLUGIN && COMPILE_CORBA_PLUGIN
   sb += "\n   -protocol SOCKET | IOR";
   sb += "\n                       IOR for CORBA, SOCKET for our native protocol.";
   sb += "\n";
//#  endif
#  ifdef COMPILE_SOCKET_PLUGIN
      sb += org::xmlBlaster::client::protocol::socket::SocketDriver::usage();
      sb += "\n   -logLevel           ERROR | WARN | INFO | TRACE | DUMP [WARN]";
      sb += "\n                       NOTE: Switch on C++ logging simultaneously to see the traces";
      sb += "\n                             as the C logging is redirected to the C++ logging library\n";
      sb += "\n";
#  endif
#  ifdef COMPILE_CORBA_PLUGIN
      sb += org::xmlBlaster::client::protocol::corba::CorbaDriver::usage();
      sb += "\n";
#  endif
   sb += org::xmlBlaster::util::parser::Sax2Parser::usage();
   sb += "\n";
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
   const I_Log& ll = getInstance().getLog();
   sb += ll.usage();
   return sb;
   /*
      StringBuffer sb = new StringBuffer(4028);
      sb.append(org.xmlBlaster.client.XmlBlasterAccess.usage(this));
      sb.append(logUsage());
      return sb.toString();
   */
}

LogManager& Global::getLogManager()
{
   return logManager_;
}

I_Log& Global::getLog(const string &logName)
{
   try {
#     ifdef XMLBLASTER_COMPILE_LOG4CPLUS_PLUGIN
         static bool first = true;
         if (first) {
            logManager_.setLogFactory("log4cplus", new Log4cplusFactory());
            logManager_.initialize(getProperty().getPropertyMap());
            first = false;
         }
         return logManager_.getLogFactory().getLog(logName);
#     else
         return logManager_.getLogFactory().getLog(logName); // Use our default Log.cpp
#     endif
   }
   catch(...) {
      throw XmlBlasterException(INTERNAL_UNKNOWN, "UNKNOWN NODE", ME + string("::getLog() failed to setup logging configuration"));
   }
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
      getLog("org.xmlBlaster.util").trace(ME, "::getPingTimer: creating the singleton 'ping timer'");
      if (pingTimer_) return *pingTimer_;
      pingTimer_ = new Timeout(*this, string("ping timer"));
      return *pingTimer_;
   }
}

const string& Global::getBoolAsString(bool val)
{
   return (val) ? XMLBLASTER_TRUE : XMLBLASTER_FALSE;
   // return lexical_cast<std::string>(val); 
   // gcc complains: warning: returning reference to temporary
}

void Global::setSessionName(SessionNameRef sessionName)
{
   sessionName_ = sessionName;
}

SessionNameRef Global::getSessionName() const
{
   return sessionName_;
}


/**
 * The absolute session name. 
 * Before connection it is temporay the relative session name
 * and changed to the absolute session name after connection (when we know the cluster id)
 * @return For example "/node/heron/client/joe/2"
 */
string Global::getId() const
{
   return id_;
}

string Global::getImmutableId() const
{
   return immutableId_;
}

string Global::getStrippedId() const
{
   return getStrippedString(id_);
}

string Global::getStrippedImmutableId() const
{
   return getStrippedString(immutableId_);
}

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

void Global::setId(const string& id) 
{
   id_ = id;
   resetInstanceId();
}

void Global::setImmutableId(const string& id) 
{
   immutableId_ = id;
}

void Global::resetInstanceId() {
   thread::Lock lock(globalMutex_);
   instanceId_ = "";
}

std::string Global::getInstanceId() const {
   if (instanceId_ == "") {
      thread::Lock lock(globalMutex_);
      if (instanceId_ == "") {
         Timestamp timestamp = TimestampFactory::getInstance().getTimestamp();
         //ContextNode node(this, "instanceId",
         //                 lexical_cast<std::string>(timestamp), getContextNode());
         instanceId_ = getId() + "/instanceId/" + lexical_cast<std::string>(timestamp);
      }
   }
   return instanceId_;
}

std::string waitOnKeyboardHit(const std::string &str)
{
   char ptr[256];
   std::string retStr;

   // Flush input stream
   while (true) {
      cout.flush();
      size_t ret = std::cin.rdbuf()->in_avail();
      if (ret == 0) break;
      std::cin.getline(ptr,255,'\n');
   }

   // Request input
   if (str != "")
      std::cout << str;

   // Read input, ignore newlines
   *ptr = 0;
   bool first=true;
   while (true) {
      std::cin.getline(ptr,255,'\n');
      if (strlen(ptr))
         retStr = ptr;
      else {
         if (str != "" && !first)
            std::cout << str;
      }
      first = false;

      size_t ret = std::cin.rdbuf()->in_avail();
      if (ret == 0 && retStr != "") {
         return retStr;
      }
   }
}

}}} // namespace

