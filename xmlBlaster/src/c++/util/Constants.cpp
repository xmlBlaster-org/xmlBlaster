/*-----------------------------------------------------------------------------
Name:      Constants.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding some constants, see Constants.h for a description
-----------------------------------------------------------------------------*/

#include <util/Constants.h>

using namespace std;

namespace org { namespace xmlBlaster {
namespace util {

const char * const Constants::DEFAULT_SECURITYPLUGIN_TYPE = "htpasswd";
const char * const Constants::DEFAULT_SECURITYPLUGIN_VERSION = "1.0";

const long Constants::XMLBLASTER_OID_ROOT[] = { 1, 3, 6, 1, 4, 1, Constants::XMLBLASTER_SNMP }; // 11662

const char * const Constants::STATE_OK = "OK";
const char * const Constants::RET_OK = "<qos><state id='OK'/></qos>";

const char * const Constants::STATE_TIMEOUT = "TIMEOUT";
const char * const Constants::STATE_ERASED = "ERASED";
const char * const Constants::STATE_FORWARD_ERROR = "FORWARD_ERROR";

const char * const Constants::INFO_QUEUED = "QUEUED";

const char * const Constants::RELATING_SESSION = "session";
const char * const Constants::RELATING_SUBJECT = "subject";
const char * const Constants::RELATING_UNRELATED = "unrelated";

const char * const Constants::ONOVERFLOW_BLOCK = "block";
const char * const Constants::ONOVERFLOW_DEADLETTER = "deadLetter";
const char * const Constants::ONOVERFLOW_DISCARD = "discard";
const char * const Constants::ONOVERFLOW_DISCARDOLDEST = "discardOldest";
const char * const Constants::ONOVERFLOW_EXCEPTION = "exception";

const char * const Constants::ONEXHAUST_KILL_SESSION = "killSession";

const char * const Constants::SESSIONID_PRAEFIX = "sessionId:";
const char * const Constants::SUBSCRIPTIONID_PRAEFIX = "__subId:";

const char * const Constants::INTERNAL_OID_PRAEFIX = "__sys__";
const char * const Constants::INTERNAL_OID_CLUSTER_PRAEFIX = "__sys__cluster";

const char * const Constants::JDBC_OID = "__sys__jdbc";

const char * const Constants::OID_DEAD_LETTER = "__sys__deadLetter";

const char * const Constants::XPATH = "XPATH";
const char * const Constants::EXACT = "EXACT";
const char * const Constants::DOMAIN = "DOMAIN";
const char * const Constants::REGEX = "REGEX";

}}}; // namespace 


