/*------------------------------------------------------------------------------
Name:      Address.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding address string and protocol string
Version:   $Id: Address.cpp,v 1.14 2004/01/14 14:54:29 ruff Exp $
------------------------------------------------------------------------------*/

/**
 * Helper class holding address string, protocol string and client side connection properties.
 * <p />
 * <pre>
 * &lt;address type='XMLRPC' sessionId='4e56890ghdFzj0'
 *           pingInterval='60000' retries='5' delay='10000'
 *           oneway='false'>
 *    http://server:8080/cb
 *    &lt;compress type='gzip' minSize='1000'/>
 *    &lt;burstMode collectTime='400'/> <!-- for publishOneway() calls -->
 * &lt;/address>
 * </pre>
 */

#include <util/qos/address/Address.h>
#include <util/lexical_cast.h>
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace address {

using namespace std;

inline void Address::initialize()
{
   setPort(global_.getProperty().getIntProperty("bootstrapPort", getPort()));

   setType(global_.getProperty().getStringProperty("protocol", getType()));
   setType(global_.getProperty().getStringProperty("dispatch/connection/protocol", getType()));
   setCollectTime(global_.getProperty().getLongProperty("dispatch/connection/burstMode/collectTime", DEFAULT_collectTime));
   setPingInterval(global_.getProperty().getLongProperty("dispatch/connection/pingInterval", defaultPingInterval_));
   setRetries(global_.getProperty().getIntProperty("dispatch/connection/retries", defaultRetries_));
   setDelay(global_.getProperty().getLongProperty("dispatch/connection/delay", defaultDelay_));
   setOneway(global_.getProperty().getBoolProperty("dispatch/connection/oneway", DEFAULT_oneway));
   setCompressType(global_.getProperty().getStringProperty("dispatch/connection/compress.type", DEFAULT_compressType));
   setMinSize(global_.getProperty().getLongProperty("dispatch/connection/compress.minSize", DEFAULT_minSize));
   setPtpAllowed(global_.getProperty().getBoolProperty("dispatch/connection/ptpAllowed", DEFAULT_ptpAllowed));
   setSecretSessionId(global_.getProperty().getStringProperty("dispatch/connection/sessionId", DEFAULT_sessionId));
   setDispatchPlugin(global_.getProperty().getStringProperty("dispatch/connection/DispatchPlugin/defaultPlugin", DEFAULT_dispatchPlugin));
   if (nodeId_ != "") {
      setPort(global_.getProperty().getIntProperty("dispatch/connection/port["+nodeId_+"]", getPort()));

      setType(global_.getProperty().getStringProperty("dispatch/connection/protocol["+nodeId_+"]", getType()));
      setCollectTime(global_.getProperty().getLongProperty("dispatch/connection/burstMode/collectTime["+nodeId_+"]", getCollectTime()));
      setPingInterval(global_.getProperty().getLongProperty("dispatch/connection/pingInterval["+nodeId_+"]", getPingInterval()));
      setRetries(global_.getProperty().getIntProperty("dispatch/connection/retries["+nodeId_+"]", getRetries()));
      setDelay(global_.getProperty().getLongProperty("dispatch/connection/delay["+nodeId_+"]", getDelay()));
      setOneway(global_.getProperty().getBoolProperty("dispatch/connection/oneway["+nodeId_+"]", oneway()));
      setCompressType(global_.getProperty().getStringProperty("dispatch/connection/compress.type["+nodeId_+"]", getCompressType()));
      setMinSize(global_.getProperty().getLongProperty("dispatch/connection/compress.minSize["+nodeId_+"]", getMinSize()));
      setPtpAllowed(global_.getProperty().getBoolProperty("dispatch/connection/ptpAllowed["+nodeId_+"]", isPtpAllowed()));
      setSecretSessionId(global_.getProperty().getStringProperty("dispatch/connection/sessionId["+nodeId_+"]", getSecretSessionId()));
      setDispatchPlugin(global_.getProperty().getStringProperty("dispatch/connection/DispatchPlugin/defaultPlugin["+nodeId_+"]", dispatchPlugin_));
   }

   // TODO: This is handled in ClientQueueProperty.java already ->
   //      long maxEntries = global_.getProperty().getLongProperty("queue/connection/maxEntries", CbQueueProperty.DEFAULT_maxEntriesDefault);
   long maxEntries = global_.getProperty().getLongProperty("queue/connection/maxEntries", 10000l);
   setMaxEntries(maxEntries);
   if (nodeId_ != "") {
      setMaxEntries(global_.getProperty().getLongProperty("queue/connection/maxEntries["+nodeId_+"]", getMaxEntries()));
   }
}

Address::Address(Global& global, const string& type, const string& nodeId)
 : AddressBase(global, "address")
{
   defaultRetries_      = -1;
   defaultDelay_        = 0;
   defaultPingInterval_ = 10000;
   pingInterval_ = defaultPingInterval_;
   retries_      = defaultRetries_;
   delay_        = defaultDelay_;
   ME = "Address";
   if (nodeId != "") nodeId_ = nodeId;
   initialize();
   if (type != "")   type_ = type;
}

Address::Address(const AddressBase& addr) : AddressBase(addr)
{
}

Address& Address::operator =(const AddressBase& addr)
{
   AddressBase::copy(addr);
   return *this;
}


void Address::setMaxEntries(long maxEntries)
{
   maxEntries_ = maxEntries;
}

long Address::getMaxEntries() const
{
   return maxEntries_;
}

/** For logging only */
string Address::getSettings()
{
   string ret;
   ret = AddressBase::getSettings();
   if (getDelay() > 0)
      ret += string(" delay=") + lexical_cast<std::string>(getDelay()) +
             string(" retries=") + lexical_cast<std::string>(getRetries()) +
             string(" maxEntries=") + lexical_cast<std::string>(getMaxEntries()) +
             string(" pingInterval=") + lexical_cast<std::string>(getPingInterval());
   return ret;
}

/** @return The literal address as given by getAddress() */
string Address::toString()
{
   return getAddress();
}

/**
 * Get a usage string for the connection parameters
 */
string Address::usage()
{
   string text = "";
   text += string("Control failsafe connection to xmlBlaster server:\n");
   // is in ClientQueueProperty.java: text += "   -queue/connection/maxEntries       The max. capacity of the client queue in number of messages [" + CbQueueProperty.DEFAULT_maxEntriesDefault + "].\n";
   //text += "   -queue/callback/onOverflow   Error handling when queue is full, 'block | deadMessage' [" + CbQueueProperty.DEFAULT_onOverflow + "].\n";
   //text += "   -queue/callback/onFailure    Error handling when connection failed (after all retries etc.) [" + CbQueueProperty.DEFAULT_onFailure + "].\n";
   text += string("   -dispatch/connection/burstMode/collectTime [" + lexical_cast<std::string>(DEFAULT_collectTime) + "]\n");
   text += string("                       Number of milliseconds we shall collect publish messages.\n");
   text += string("                       This allows performance tuning, try set it to 200.\n");
 //text += "   -oneway             Shall the publish() messages be send oneway (no application level ACK) [" + Address.DEFAULT_oneway + "]\n";
   text += string("   -dispatch/connection/pingInterval [" + lexical_cast<std::string>(defaultPingInterval_) + "]\n");
   text += string("                       Pinging every given milliseconds.\n");
   text += string("   -dispatch/connection/retries [" + lexical_cast<std::string>(defaultRetries_) + "]\n");
   text += string("                       How often to retry if connection fails (-1 is forever).\n");
   text += string("   -dispatch/connection/delay [" + lexical_cast<std::string>(defaultDelay_) + "]\n");
   text += string("                       Delay between connection retries in milliseconds.\n");
   text += string("                       A delay value > 0 switches fails save mode on, 0 switches it off.\n");
 //text += "   -DispatchPlugin/defaultPlugin  Specify your specific dispatcher plugin [" + CallbackAddress.DEFAULT_dispatchPlugin + "]\n";
 //text += "   -compress.type      With which format message be compressed on callback [" + Address.DEFAULT_compressType + "]\n";
 //text += "   -compress.minSize   Messages bigger this size in bytes are compressed [" + Address.DEFAULT_minSize + "]\n";
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
         Log& log = glob.getLog("core");
         log.info("main", "This is a simple info");
         Address a(glob);
         a.setType("SOCKET");
         a.setAddress("127.0.0.1:7600");
         a.setCollectTime(12345l);
         a.setPingInterval(54321l);
         a.setRetries(17);
         a.setDelay(7890l);
         a.setOneway(true);
         a.setSecretSessionId("0x4546hwi89");
         cout << a.toXml() << endl;
      }
      {
         string nodeId = "heron";
         int                nmax = 8;
         const char** argc = new const char*[nmax];
         argc[0] = "-sessionId";
         argc[1] = "ERROR";
         string help = string("-sessionId[") + nodeId + string("]");
         argc[2] = string(help).c_str();
         argc[3] = "OK";
         argc[4] = "-pingInterval";
         argc[5] = "8888";
         help = string("-delay[") + nodeId + string("]");
         argc[6] = help.c_str();
         argc[7] = "8888";

         Global& glob = Global::getInstance();
         glob.initialize(nmax, argc);
         Address a(glob, "RMI", nodeId);
         cout << a.toXml() << endl;
      }
   }
   catch(...) {
      cout << "unknown uncatched exception" << endl;
   }
}

#endif
