/*------------------------------------------------------------------------------
Name:      CallbackAddress.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback address string and protocol string
------------------------------------------------------------------------------*/

#include <util/qos/address/CallbackAddress.h>
#include <util/lexical_cast.h>
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace address {

using namespace std;
using namespace org::xmlBlaster::util;


inline void CallbackAddress::initialize()
{
   initHostname(global_.getCbHostname()); // don't use setHostname() as it would set isCardcodedHostname=true
   setPort(global_.getProperty().getIntProperty("dispatch/callback/port", getPort()));
   setType(global_.getProperty().getStringProperty("protocol", getType()));
   setType(global_.getProperty().getStringProperty("dispatch/callback/protocol", getType()));
   setCollectTime(global_.getProperty().getLongProperty("dispatch/callback/burstMode/collectTime", DEFAULT_collectTime)); // sync update()
   setPingInterval(global_.getProperty().getLongProperty("dispatch/callback/pingInterval", defaultPingInterval_));
   setRetries(global_.getProperty().getIntProperty("dispatch/callback/retries", defaultRetries_));
   setDelay(global_.getProperty().getLongProperty("dispatch/callback/delay", defaultDelay_));
   useForSubjectQueue(global_.getProperty().getBoolProperty("dispatch/callback/useForSubjectQueue", DEFAULT_useForSubjectQueue));
   setOneway(global_.getProperty().getBoolProperty("dispatch/callback/oneway", DEFAULT_oneway));
   setDispatcherActive(global_.getProperty().getBoolProperty("dispatch/callback/dispatcherActive", DEFAULT_dispatcherActive));
   setCompressType(global_.getProperty().getStringProperty("dispatch/callback/compress.type", DEFAULT_compressType));
   setMinSize(global_.getProperty().getLongProperty("dispatch/callback/compress.minSize", DEFAULT_minSize));
   setPtpAllowed(global_.getProperty().getBoolProperty("dispatch/callback/ptpAllowed", DEFAULT_ptpAllowed));
   setSecretSessionId(global_.getProperty().getStringProperty("dispatch/callback/sessionId", DEFAULT_sessionId));
   setDispatchPlugin(global_.getProperty().getStringProperty("dispatch/callback/DispatchPlugin/defaultPlugin", DEFAULT_dispatchPlugin));
   if (nodeId_ != "") {
      setPort(global_.getProperty().getIntProperty("dispatch/callback/port["+nodeId_+"]", getPort()));
      setType(global_.getProperty().getStringProperty("dispatch/callback/protocol["+nodeId_+"]", getType()));
      setCollectTime(global_.getProperty().getLongProperty("dispatch/callback/burstMode/collectTime["+nodeId_+"]", collectTime_));
      setPingInterval(global_.getProperty().getLongProperty("dispatch/callback/pingInterval["+nodeId_+"]", pingInterval_));
      setRetries(global_.getProperty().getIntProperty("dispatch/callback/retries["+nodeId_+"]", retries_));
      setDelay(global_.getProperty().getLongProperty("dispatch/callback/delay["+nodeId_+"]", delay_));
      useForSubjectQueue(global_.getProperty().getBoolProperty("dispatch/callback/useForSubjectQueue["+nodeId_+"]", useForSubjectQueue_));
      setOneway(global_.getProperty().getBoolProperty("dispatch/callback/oneway["+nodeId_+"]", oneway_));
      setDispatcherActive(global_.getProperty().getBoolProperty("dispatch/callback/dispatcherActive["+nodeId_+"]", dispatcherActive_));
      setCompressType(global_.getProperty().getStringProperty("dispatch/callback/compress.type["+nodeId_+"]", compressType_));
      setMinSize(global_.getProperty().getLongProperty("dispatch/callback/compress.minSize["+nodeId_+"]", minSize_));
      setPtpAllowed(global_.getProperty().getBoolProperty("dispatch/callback/ptpAllowed["+nodeId_+"]", ptpAllowed_));
      setSecretSessionId(global_.getProperty().getStringProperty("dispatch/callback/sessionId["+nodeId_+"]", sessionId_));
      setDispatchPlugin(global_.getProperty().getStringProperty("dispatch/callback/DispatchPlugin/defaultPlugin["+nodeId_+"]", dispatchPlugin_));
   }
}



CallbackAddress::CallbackAddress(Global& global, const string& type, const string nodeId)
   : AddressBase(global, "callback")
{
   defaultRetries_      = 0;
   defaultDelay_        = Constants::MINUTE_IN_MILLIS;
   defaultPingInterval_ = Constants::MINUTE_IN_MILLIS;
   ME = "CallbackAddress";
   if (nodeId != "") nodeId_ = nodeId;
   pingInterval_ = defaultPingInterval_;
   retries_      = defaultRetries_;
   delay_        = defaultDelay_;
   initialize();
   if (type != "") setType(type);
}

CallbackAddress::CallbackAddress(const AddressBase& addr) : AddressBase(addr)
{
}

CallbackAddress& CallbackAddress::operator =(const AddressBase& addr)
{
   AddressBase::copy(addr);
   return *this;
}

/**
 * Shall this address be used for subject queue messages?
 * @return false if address is for session queue only
 */
bool CallbackAddress::useForSubjectQueue()
{
   return useForSubjectQueue_;
}

/**
 * Shall this address be used for subject queue messages?
 * @param useForSubjectQueue false if address is for session queue only
 */
void CallbackAddress::useForSubjectQueue(bool useForSubjectQueue)
{
   useForSubjectQueue_ = useForSubjectQueue;
}

string CallbackAddress::toString()
{
   return getRawAddress();
}

/**
 * Get a usage string for the server side supported callback connection parameters
 */
string CallbackAddress::usage()
{
   string text;
   text += string("Control xmlBlaster server side callback (if we install a local callback server):\n");
   text += string("   -dispatch/callback/sessionId []\n");
   text += string("                       The session ID which is passed to our callback server update() method.\n");
   text += string("   -dispatch/callback/burstMode/collectTime [") + lexical_cast<std::string>(DEFAULT_collectTime) + string("]\n");
   text += string("                       Number of milliseconds xmlBlaster shall collect callback messages.\n");
   text += string("                       The burst mode allows performance tuning, try set it to 200.\n");

   text += string("   -dispatch/callback/oneway [") + lexical_cast<std::string>(DEFAULT_oneway) + string("]\n");
   text += string("                       Shall the update() messages be send oneway (no application level ACK).\n");

   text += string("   -dispatch/callback/dispatcherActive [") + lexical_cast<string>(DEFAULT_dispatcherActive) + string("]\n");
   text += string("                       If false inhibit delivery of callback messages.\n");

   text += string("   -dispatch/callback/pingInterval [") + lexical_cast<std::string>(defaultPingInterval_) + string("]\n");
   text += string("                       Pinging every given milliseconds.\n");
   text += string("   -dispatch/callback/retries [") + lexical_cast<std::string>(defaultRetries_) + string("]\n");
   text += string("                       How often to retry if callback fails.\n");
   text += string("                       -1 forever, 0 no retry, > 0 number of retries.\n");
   text += string("   -dispatch/callback/delay [") + lexical_cast<std::string>(defaultDelay_) + string("]\n");
   text += string("                       Delay between callback retries in millisecond.\n");
   //text += string("   -dispatch/callback/compress.type   With which format message be compressed on callback [") + DEFAULT_compressType + string("]\n");
   //text += string("   -dispatch/callback/compress.minSize Messages bigger this size in bytes are compressed [") + lexical_cast<std::string>(DEFAULT_minSize) + string("]\n");

   //text += string("   -cb.ptpAllowed      PtP messages wanted? false prevents spamming [") + lexical_cast<std::string>(DEFAULT_ptpAllowed) + string("]\n");
   //text += "   -cb.DispatchPlugin/defaultPlugin  Specify your specific dispatcher plugin [" + CallbackAddress.DEFAULT_dispatchPlugin + "]\n";
   return text;
}

}}}}} // namespace

#ifdef _XMLBLASTER_CLASSTEST

using namespace std;
using namespace org::xmlBlaster::util::qos::address;

/** For testing: java org.xmlBlaster.authentication.plugins.simple.SecurityQos */
int main(int args, char* argv[])
{
   try {
      {
         Global& glob = Global::getInstance();
         glob.initialize(args, argv);

         CallbackAddress a(glob);
         a.setType("SOCKET");
         a.setAddress("127.0.0.1:7600");
         a.setCollectTime(12345L);
         a.setPingInterval(54321L);
         a.setRetries(17);
         a.setDelay(7890L);
         a.setOneway(true);
         a.setSecretSessionId("0x4546hwi89");
         cout << a.toXml() << endl;
      }
      {
         string nodeId = "heron";

         int                nmax = 8;
         const char** argc = new const char*[nmax];
         string help = string("-dispatch/callback/sessionId[") +nodeId + string("]");
         argc[0] = help.c_str();
         argc[1] = "OK";
         argc[2] = "dispatch/callback/sessionId";
         argc[3] = "ERROR";
         argc[4] = "-cb.pingInterval";
         argc[5] = "8888";
         help = string("-cb.delay[") +nodeId + string("]");
         argc[6] = help.c_str();
         argc[7] = "8888";

         Global& glob = Global::getInstance();
         glob.initialize(nmax, argc);
         CallbackAddress a(glob, "RMI", nodeId);
         cout << a.toXml() << endl;
      }
   }
   catch(...) {
      cout << "Exception in main method" << endl;
   }
}

#endif
