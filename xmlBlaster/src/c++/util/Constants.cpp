/*-----------------------------------------------------------------------------
Name:      Constants.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding some constants, see Constants.h for a description
-----------------------------------------------------------------------------*/
#if defined(_WINDOWS)
#pragma warning(disable:4786)
#endif


#include <util/Constants.h>


namespace org { namespace xmlBlaster {
  namespace util { namespace Constants {

const char * DEFAULT_SECURITYPLUGIN_TYPE = "htpasswd";
const char * DEFAULT_SECURITYPLUGIN_VERSION = "1.0";

long XMLBLASTER_OID_ROOT[] = { 1, 3, 6, 1, 4, 1, XMLBLASTER_SNMP }; // 11662

const char * STATE_OK = "OK";
const char * RET_OK = "<qos><state id='OK'/></qos>";

const char * STATE_TIMEOUT = "TIMEOUT";
const char * STATE_ERASED = "ERASED";
const char * STATE_FORWARD_ERROR = "FORWARD_ERROR";

const char * INFO_QUEUED = "QUEUED";

const char * RELATING_CALLBACK   = "callback";
const char * RELATING_SUBJECT    = "subject";
const char * RELATING_UNRELATED  = "unrelated";
const char * RELATING_CLIENT     = "client";
const char * RELATING_HISTORY    = "history";
const char * RELATING_MSGUNITSTORE="msgUnitStore";
const char * RELATING_TOPICSTORE = "topicStore";

const char * ONOVERFLOW_BLOCK = "block";
const char * ONOVERFLOW_DEADLETTER = "deadLetter";
const char * ONOVERFLOW_DISCARD = "discard";
const char * ONOVERFLOW_DISCARDOLDEST = "discardOldest";
const char * ONOVERFLOW_EXCEPTION = "exception";
const char * ONOVERFLOW_DEADMESSAGE = "deadMessage";
const char * ONEXHAUST_KILL_SESSION = "killSession";

/** If subscription ID is given by client, e.g. "__subId:/node/heron/client/joe/3/34"
  * see Requirement engine.qos.subscribe.id
  */
const char* SUBSCRIPTIONID_CLIENT_PREFIX    = "__subId:/node/";
const char* INTERNAL_OID_PREFIX_FOR_PLUGINS = "_";
const char* INTERNAL_OID_ADMIN_CMD          = "__cmd:";
const char* INTERNAL_OID_PREFIX_FOR_CORE    = "__";
const char* INTERNAL_OID_PREFIX             = "__sys__";  // Should be replaced by INTERNAL_OID_PREFIX_FOR_CORE in future
const char* INTERNAL_OID_CLUSTER_PREFIX     = "__sys__cluster";  // "__sys__cluster"

const char * JDBC_OID = "__sys__jdbc";

const char * OID_DEAD_LETTER = "__sys__deadLetter";

const char * XPATH = "XPATH";
const char * EXACT = "EXACT";
//const char * const Constants::DOMAIN = "DOMAIN"; // doesn't compile with g++ 3.1.1
const char * D_O_M_A_I_N = "DOMAIN"; // doesn't compile with g++ 3.1.1
const char * REGEX       = "REGEX";

const char * IOR     = "IOR";
const char * EMAIL   = "EMAIL";
const char * XML_RPC = "XML-RPC";

const char * OFFSET  = "   ";
const char * INDENT  = "   ";

const Timestamp THOUSAND = 1000;
const Timestamp MILLION  = 1000 * THOUSAND;
const Timestamp BILLION  = 1000 * MILLION;

/** Prefix to create a sessionId */
const char* SESSIONID_PREFIX = "sessionId:";
const char* SUBSCRIPTIONID_PREFIX = "__subId:";
const char* const SUBSCRIPTIONID_PtP = "__subId:PtP";

/*
const int getPriority(string prio, int defaultPriority)
{
  if (prio != null) {
     prio = prio.trim();
     try {
        return new Integer(prio).intValue();
     } catch (NumberFormatException e) {
        prio = prio.toUpperCase();
        if (prio.startsWith("MIN"))
           return Constants.MIN_PRIORITY;
        else if (prio.startsWith("LOW"))
           return Constants.LOW_PRIORITY;
        else if (prio.startsWith("NORM"))
           return Constants.NORM_PRIORITY;
        else if (prio.startsWith("HIGH"))
           return Constants.HIGH_PRIORITY;
        else if (prio.startsWith("MAX"))
           return Constants.MAX_PRIORITY;
        else
           Global.instance().getLog("core").warn(ME, "Wrong format of <priority>" + prio +
                "</priority>, expected a number between (inclusiv) 0 - 9, setting to message priority to "
                + defaultPriority);
     }
  }
  if (defaultPriority < Constants.MIN_PRIORITY || defaultPriority > Constants.MAX_PRIORITY) {
      Global.instance().getLog("core").warn(ME, "Wrong message defaultPriority=" + defaultPriority + " given, setting to NORM_PRIORITY");
      return Constants.NORM_PRIORITY;
  }
  return defaultPriority;
}
*/

}}}}; // namespace 


