/*------------------------------------------------------------------------------
Name:      Address.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding address string and protocol string
Version:   $Id: Address.cpp,v 1.3 2003/01/16 10:11:53 laghi Exp $
------------------------------------------------------------------------------*/

/**
 * Helper class holding address string, protocol string and client side connection properties.
 * <p />
 * <pre>
 * &lt;address type='XML-RPC' sessionId='4e56890ghdFzj0'
 *           pingInterval='60000' retries='5' delay='10000'
 *           oneway='false'>
 *    http://server:8080/cb
 *    &lt;compress type='gzip' minSize='1000'/>
 *    &lt;burstMode collectTime='400'/> <!-- for publishOneway() calls -->
 * &lt;/address>
 * </pre>
 */

#include <util/qos/address/Address.h>
#include <boost/lexical_cast.hpp>
#include <util/Global.h>

using namespace boost;

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace address {


inline void Address::initialize()
{
   setPort(global_.getProperty().getIntProperty("port", getPort()));
   setPort(global_.getProperty().getIntProperty("client.port", getPort())); // this is stronger (do we need it?)

   setType(global_.getProperty().getStringProperty("client.protocol", getType()));
   setCollectTime(global_.getProperty().getLongProperty("burstMode.collectTime", DEFAULT_collectTime));
   setCollectTimeOneway(global_.getProperty().getLongProperty("burstMode.collectTimeOneway", DEFAULT_collectTimeOneway));
   setPingInterval(global_.getProperty().getLongProperty("pingInterval", defaultPingInterval_));
   setRetries(global_.getProperty().getIntProperty("retries", defaultRetries_));
   setDelay(global_.getProperty().getLongProperty("delay", defaultDelay_));
   setOneway(global_.getProperty().getBoolProperty("oneway", DEFAULT_oneway));
   setCompressType(global_.getProperty().getStringProperty("compress.type", DEFAULT_compressType));
   setMinSize(global_.getProperty().getLongProperty("compress.minSize", DEFAULT_minSize));
   setPtpAllowed(global_.getProperty().getBoolProperty("ptpAllowed", DEFAULT_ptpAllowed));
   setSessionId(global_.getProperty().getStringProperty("sessionId", DEFAULT_sessionId));
   setDispatchPlugin(global_.getProperty().getStringProperty("DispatchPlugin.defaultPlugin", DEFAULT_dispatchPlugin));
   if (nodeId_ != "") {
      setPort(global_.getProperty().getIntProperty("port["+nodeId_+"]", getPort()));
      setPort(global_.getProperty().getIntProperty("client.port["+nodeId_+"]", getPort())); // this is stronger (do we need it?)

      setType(global_.getProperty().getStringProperty("client.protocol["+nodeId_+"]", getType()));
      setCollectTime(global_.getProperty().getLongProperty("burstMode.collectTime["+nodeId_+"]", getCollectTime()));
      setCollectTimeOneway(global_.getProperty().getLongProperty("burstMode.collectTimeOneway["+nodeId_+"]", getCollectTimeOneway()));
      setPingInterval(global_.getProperty().getLongProperty("pingInterval["+nodeId_+"]", getPingInterval()));
      setRetries(global_.getProperty().getIntProperty("retries["+nodeId_+"]", getRetries()));
      setDelay(global_.getProperty().getLongProperty("delay["+nodeId_+"]", getDelay()));
      setOneway(global_.getProperty().getBoolProperty("oneway["+nodeId_+"]", oneway()));
      setCompressType(global_.getProperty().getStringProperty("compress.type["+nodeId_+"]", getCompressType()));
      setMinSize(global_.getProperty().getLongProperty("compress.minSize["+nodeId_+"]", getMinSize()));
      setPtpAllowed(global_.getProperty().getBoolProperty("ptpAllowed["+nodeId_+"]", isPtpAllowed()));
      setSessionId(global_.getProperty().getStringProperty("sessionId["+nodeId_+"]", getSessionId()));
      setDispatchPlugin(global_.getProperty().getStringProperty("DispatchPlugin.defaultPlugin["+nodeId_+"]", dispatchPlugin_));
   }

   // TODO: This is handled in QueueProperty.java already ->
   //      long maxMsg = global_.getProperty().getLongProperty("queue.maxMsg", CbQueueProperty.DEFAULT_maxMsgDefault);
   long maxMsg = global_.getProperty().getLongProperty("queue.maxMsg", 10000l);
   setMaxMsg(maxMsg);
   if (nodeId_ != "") {
      setMaxMsg(global_.getProperty().getLongProperty("queue.maxMsg["+nodeId_+"]", getMaxMsg()));
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


void Address::setMaxMsg(long maxMsg)
{
   maxMsg_ = maxMsg;
}

long Address::getMaxMsg() const
{
   return maxMsg_;
}

/** For logging only */
string Address::getSettings()
{
   string ret;
   ret = AddressBase::getSettings();
   if (getDelay() > 0)
      ret += string(" delay=") + lexical_cast<string>(getDelay()) +
             string(" retries=") + lexical_cast<string>(getRetries()) +
             string(" maxMsg=") + lexical_cast<string>(getMaxMsg()) +
             string(" pingInterval=") + lexical_cast<string>(getPingInterval());
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
   text += string("Control fail save connection to xmlBlaster server:\n");
   // is in QueueProperty.java: text += "   -queue.maxMsg       The max. capacity of the client queue in number of messages [" + CbQueueProperty.DEFAULT_maxMsgDefault + "].\n";
   //text += "   -queue.onOverflow   Error handling when queue is full, 'block | deadMessage' [" + CbQueueProperty.DEFAULT_onOverflow + "].\n";
   //text += "   -queue.onFailure    Error handling when connection failed (after all retries etc.) [" + CbQueueProperty.DEFAULT_onFailure + "].\n";
   text += string("   -burstMode.collectTimeOneway Number of milliseconds we shall collect oneway publish messages [" + lexical_cast<string>(DEFAULT_collectTime) + "].\n");
   text += string("                       This allows performance tuning, try set it to 200.\n");
 //text += "   -oneway             Shall the publish() messages be send oneway (no application level ACK) [" + Address.DEFAULT_oneway + "]\n";
   text += string("   -pingInterval       Pinging every given milliseconds [" + lexical_cast<string>(defaultPingInterval_) + "]\n");
   text += string("   -retries            How often to retry if connection fails (-1 is forever) [" + lexical_cast<string>(defaultRetries_) + "]\n");
   text += string("   -delay              Delay between connection retries in milliseconds [" + lexical_cast<string>(defaultDelay_) + "]\n");
   text += string("                       A delay value > 0 switches fails save mode on, 0 switches it off\n");
 //text += "   -DispatchPlugin.defaultPlugin  Specify your specific dispatcher plugin [" + CallbackAddress.DEFAULT_dispatchPlugin + "]\n";
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
         a.setSessionId("0x4546hwi89");
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
