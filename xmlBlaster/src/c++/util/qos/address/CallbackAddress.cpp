/*------------------------------------------------------------------------------
Name:      CallbackAddress.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback address string and protocol string
Version:   $Id: CallbackAddress.cpp,v 1.6 2003/03/26 22:28:13 ruff Exp $
------------------------------------------------------------------------------*/

#include <util/qos/address/CallbackAddress.h>
#include <util/lexical_cast.h>
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace address {

using namespace org::xmlBlaster::util;


inline void CallbackAddress::initialize()
{
   initHostname(global_.getCbHostname()); // don't use setHostname() as it would set isCardcodedHostname=true
   setPort(global_.getProperty().getIntProperty("cb.port", getPort()));
   setType(global_.getProperty().getStringProperty("cb.protocol", getType()));
   setCollectTime(global_.getProperty().getLongProperty("cb.burstMode.collectTime", DEFAULT_collectTime)); // sync update()
   setCollectTimeOneway(global_.getProperty().getLongProperty("cb.burstMode.collectTimeOneway", DEFAULT_collectTimeOneway)); // oneway update()
   setPingInterval(global_.getProperty().getLongProperty("cb.pingInterval", defaultPingInterval_));
   setRetries(global_.getProperty().getIntProperty("cb.retries", defaultRetries_));
   setDelay(global_.getProperty().getLongProperty("cb.delay", defaultDelay_));
   useForSubjectQueue(global_.getProperty().getBoolProperty("cb.useForSubjectQueue", DEFAULT_useForSubjectQueue));
   setOneway(global_.getProperty().getBoolProperty("cb.oneway", DEFAULT_oneway));
   setCompressType(global_.getProperty().getStringProperty("cb.compress.type", DEFAULT_compressType));
   setMinSize(global_.getProperty().getLongProperty("cb.compress.minSize", DEFAULT_minSize));
   setPtpAllowed(global_.getProperty().getBoolProperty("cb.ptpAllowed", DEFAULT_ptpAllowed));
   setSecretSessionId(global_.getProperty().getStringProperty("cb.sessionId", DEFAULT_sessionId));
   setDispatchPlugin(global_.getProperty().getStringProperty("cb.DispatchPlugin/defaultPlugin", DEFAULT_dispatchPlugin));
   if (nodeId_ != "") {
      setPort(global_.getProperty().getIntProperty("cb.port["+nodeId_+"]", getPort()));
      setType(global_.getProperty().getStringProperty("cb.protocol["+nodeId_+"]", getType()));
      setCollectTime(global_.getProperty().getLongProperty("cb.burstMode.collectTime["+nodeId_+"]", collectTime_));
      setCollectTimeOneway(global_.getProperty().getLongProperty("cb.burstMode.collectTimeOneway["+nodeId_+"]", collectTimeOneway_));
      setPingInterval(global_.getProperty().getLongProperty("cb.pingInterval["+nodeId_+"]", pingInterval_));
      setRetries(global_.getProperty().getIntProperty("cb.retries["+nodeId_+"]", retries_));
      setDelay(global_.getProperty().getLongProperty("cb.delay["+nodeId_+"]", delay_));
      useForSubjectQueue(global_.getProperty().getBoolProperty("cb.useForSubjectQueue["+nodeId_+"]", useForSubjectQueue_));
      setOneway(global_.getProperty().getBoolProperty("cb.oneway["+nodeId_+"]", oneway_));
      setCompressType(global_.getProperty().getStringProperty("cb.compress.type["+nodeId_+"]", compressType_));
      setMinSize(global_.getProperty().getLongProperty("cb.compress.minSize["+nodeId_+"]", minSize_));
      setPtpAllowed(global_.getProperty().getBoolProperty("cb.ptpAllowed["+nodeId_+"]", ptpAllowed_));
      setSecretSessionId(global_.getProperty().getStringProperty("cb.sessionId["+nodeId_+"]", sessionId_));
      setDispatchPlugin(global_.getProperty().getStringProperty("cb.DispatchPlugin/defaultPlugin["+nodeId_+"]", dispatchPlugin_));
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

/** @return The literal address as given by getAddress() */
string CallbackAddress::toString()
{
   return getAddress();
}

/**
 * Get a usage string for the server side supported callback connection parameters
 */
string CallbackAddress::usage()
{
   string text;
   text += string("Control xmlBlaster server side callback (if we install a local callback server):\n");
   text += string("   -cb.sessionId       The session ID which is passed to our callback server update() method.\n");
   text += string("   -cb.burstMode.collectTime Number of milliseconds xmlBlaster shall collect callback messages [") + lexical_cast<string>(DEFAULT_collectTime) + string("].\n");
   text += string("                         The burst mode allows performance tuning, try set it to 200.\n");

   string help = "false";
   if (DEFAULT_oneway) help = "true";
   text += string("   -cb.oneway          Shall the update() messages be send oneway (no application level ACK) [") + help + string("]\n");

   text += string("   -cb.pingInterval    Pinging every given milliseconds [") + lexical_cast<string>(defaultPingInterval_) + string("]\n");
   text += string("   -cb.retries         How often to retry if callback fails (-1 forever, 0 no retry, > 0 number of retries) [") + lexical_cast<string>(defaultRetries_) + string("]\n");
   text += string("   -cb.delay           Delay between callback retries in milliseconds [") + lexical_cast<string>(defaultDelay_) + string("]\n");
   text += string("   -cb.compress.type   With which format message be compressed on callback [") + DEFAULT_compressType + string("]\n");
   text += string("   -cb.compress.minSize Messages bigger this size in bytes are compressed [") + lexical_cast<string>(DEFAULT_minSize) + string("]\n");

   help = "false";
   if (DEFAULT_ptpAllowed) help = "true";
   text += string("   -cb.ptpAllowed      PtP messages wanted? false prevents spamming [") + help + string("]\n");
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
         string help = string("-cb.sessionId[") +nodeId + string("]");
         argc[0] = help.c_str();
         argc[1] = "OK";
         argc[2] = "-cb.sessionId";
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
