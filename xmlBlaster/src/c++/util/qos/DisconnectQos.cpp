/*------------------------------------------------------------------------------
Name:      DisconnectQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: DisconnectQos.cpp,v 1.1 2002/12/26 22:36:24 laghi Exp $
------------------------------------------------------------------------------*/

#include <util/qos/DisconnectQos.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

DisconnectQos::DisconnectQos(Global& global)
   : ME("DisconnectQos"), global_(global), log_(global.getLog("core"))
{
   deleteSubjectQueue_ = true;
   clearSessions_      = false;
}

DisconnectQos::DisconnectQos(const DisconnectQos& qos)
   : ME(qos.ME), global_(qos.global_), log_(qos.log_)
{
   deleteSubjectQueue_ = qos.deleteSubjectQueue_;
   clearSessions_      = qos.clearSessions_;
}

DisconnectQos& DisconnectQos::operator =(const DisconnectQos& qos)
{
   deleteSubjectQueue_ = qos.deleteSubjectQueue_;
   clearSessions_      = qos.clearSessions_;
   return *this;
}


bool DisconnectQos::getDeleteSubjectQueue() const
{
   return deleteSubjectQueue_;
}

void DisconnectQos::setSubjectQueue(bool del)
{
   deleteSubjectQueue_ = del;
}

bool DisconnectQos::getClearSessions() const
{
   return clearSessions_;
}

void DisconnectQos::setClearSessions(bool del)
{
   clearSessions_ = del;
}

string DisconnectQos::toXml(const string& extraOffset) const
{
   string ret;
   string offset = "\n   ";
   offset += extraOffset;

   ret += offset + "<qos>";
   ret += offset + "  <deleteSubjectQueue>" + global_.getBoolAsString(deleteSubjectQueue_) + "</deleteSubjectQueue>";
   ret += offset + "  <clearSessions>" + global_.getBoolAsString(clearSessions_) + "</clearSessions>";
   ret += offset + "</qos>";

   return ret;
}

}}}}


