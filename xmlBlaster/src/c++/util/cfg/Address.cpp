/*------------------------------------------------------------------------------
Name:      Address.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding address string and protocol string
Version:   $Id: Address.cpp,v 1.1 2002/12/06 19:28:14 laghi Exp $
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

#include <util/cfg/Address.h>
#include <boost/lexical_cast.hpp>

using boost::lexical_cast;

namespace org { namespace xmlBlaster { namespace util { namespace cfg {

   Address::Address(Global& global, const string& type, const string& nodeId)
    : AddressBase(global, "address"), ME("Address")
   {
      if (nodeId != "") nodeId_ = nodeId;
      initialize();
      if (type != "")   type_ = type;
   }

   void Address::setMaxMsg(long maxMsg)
   {
      maxMsg_ = maxMsg;
   }

   long Address::getMaxMsg() const
   {
      return maxMsg_;
   }

   /** How often to retry if connection fails: defaults to -1 (retry forever) */
   int Address::getDefaultRetries()
   {
      return -1;
   }

   /** Delay between connection retries in milliseconds (5000 is a good value): defaults to 0, a value bigger 0 switches fails save mode on */
   Timestamp Address::getDefaultDelay()
   {
      return 0;
   }
   // /* Delay between connection retries in milliseconds: defaults to 5000 (5 sec), a value of 0 switches fails save mode off */
   // public long getDefaultDelay() { return 5 * 1000L; };

   /** Ping interval: pinging every given milliseconds, defaults to 10 seconds */
   Timestamp Address::getDefaultPingInterval()
   {
      return 10000;
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
      text += string("   -pingInterval       Pinging every given milliseconds [" + lexical_cast<string>(getDefaultPingInterval()) + "]\n");
      text += string("   -retries            How often to retry if connection fails (-1 is forever) [" + lexical_cast<string>(getDefaultRetries()) + "]\n");
      text += string("   -delay              Delay between connection retries in milliseconds [" + lexical_cast<string>(getDefaultDelay()) + "]\n");
      text += string("                       A delay value > 0 switches fails save mode on, 0 switches it off\n");
    //text += "   -DispatchPlugin.defaultPlugin  Specify your specific dispatcher plugin [" + CallbackAddress.DEFAULT_dispatchPlugin + "]\n";
    //text += "   -compress.type      With which format message be compressed on callback [" + Address.DEFAULT_compressType + "]\n";
    //text += "   -compress.minSize   Messages bigger this size in bytes are compressed [" + Address.DEFAULT_minSize + "]\n";
      return text;
   }

}}}} // namespace


#ifdef _XMLBLASTER_CLASSTEST

using namespace std;
using org::xmlBlaster::util::cfg::Address;

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
