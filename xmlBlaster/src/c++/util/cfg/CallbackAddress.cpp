/*------------------------------------------------------------------------------
Name:      CallbackAddress.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback address string and protocol string
Version:   $Id: CallbackAddress.cpp,v 1.1 2002/12/06 21:13:32 laghi Exp $
------------------------------------------------------------------------------*/

#include <util/cfg/CallbackAddress.h>
#include <boost/lexical_cast.hpp>

namespace org { namespace xmlBlaster { namespace util { namespace cfg {

using namespace org::xmlBlaster::util;
using boost::lexical_cast;

   CallbackAddress::CallbackAddress(Global& global, const string& type, const string nodeId)
      : AddressBase(global, "callback"), ME("CallbackAddress")
   {
      if (nodeId != "") nodeId_ = nodeId;
      initialize();
      if (type != "") setType(type);
   }

   /** How often to retry if connection fails: defaults to 0 retries, on failure we give up */
   int CallbackAddress::getDefaultRetries()
   {
      return 0;
   }

   /** Delay between connection retries in milliseconds: defaults to one minute */
   Timestamp CallbackAddress::getDefaultDelay()
   {
      return Constants::MINUTE_IN_MILLIS;
   }

   /** Ping interval: pinging every given milliseconds, defaults to one minute */
   Timestamp CallbackAddress::getDefaultPingInterval()
   {
      return Constants::MINUTE_IN_MILLIS;
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

      text += string("   -cb.pingInterval    Pinging every given milliseconds [") + lexical_cast<string>(getDefaultPingInterval()) + string("]\n");
      text += string("   -cb.retries         How often to retry if callback fails (-1 forever, 0 no retry, > 0 number of retries) [") + lexical_cast<string>(getDefaultRetries()) + string("]\n");
      text += string("   -cb.delay           Delay between callback retries in milliseconds [") + lexical_cast<string>(getDefaultDelay()) + string("]\n");
      text += string("   -cb.compress.type   With which format message be compressed on callback [") + DEFAULT_compressType + string("]\n");
      text += string("   -cb.compress.minSize Messages bigger this size in bytes are compressed [") + lexical_cast<string>(DEFAULT_minSize) + string("]\n");

      help = "false";
      if (DEFAULT_ptpAllowed) help = "true";
      text += string("   -cb.ptpAllowed      PtP messages wanted? false prevents spamming [") + help + string("]\n");
      //text += "   -cb.DispatchPlugin.defaultPlugin  Specify your specific dispatcher plugin [" + CallbackAddress.DEFAULT_dispatchPlugin + "]\n";
      return text;
   }

}}}} // namespace

#ifdef _XMLBLASTER_CLASSTEST

using namespace std;
using org::xmlBlaster::util::cfg::CallbackAddress;

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
         a.setSessionId("0x4546hwi89");
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
