/*------------------------------------------------------------------------------
Name:      DisconnectQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: DisconnectQos.cpp,v 1.4 2004/02/09 10:08:02 ruff Exp $
------------------------------------------------------------------------------*/

#include <util/qos/DisconnectQos.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

DisconnectQos::DisconnectQos(Global& global)
   : ME("DisconnectQos"), 
     global_(global), 
     log_(global.getLog("org.xmlBlaster.util.qos")),
     clientProperties_()
{
   deleteSubjectQueue_ = true;
   clearSessions_      = false;
}

DisconnectQos::DisconnectQos(const DisconnectQos& qos)
   : ME(qos.ME), 
     global_(qos.global_), 
     log_(qos.log_),
     clientProperties_()
{
   deleteSubjectQueue_ = qos.deleteSubjectQueue_;
   clearSessions_      = qos.clearSessions_;
   clientProperties_ = qos.clientProperties_;
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

void DisconnectQos::addClientProperty(const std::string& key, const std::string& value)
{
   clientProperties_.insert(ClientPropertyMap::value_type(key, value));   
}
        
const DisconnectQos::ClientPropertyMap& DisconnectQos::getClientProperties() const
{
   return clientProperties_;
}

string DisconnectQos::toXml(const string& extraOffset) const
{
   string ret;
   string offset = "\n   ";
   offset += extraOffset;

   ret += offset + "<qos>";
   ret += offset + "  <deleteSubjectQueue>" + global_.getBoolAsString(deleteSubjectQueue_) + "</deleteSubjectQueue>";
   ret += offset + "  <clearSessions>" + global_.getBoolAsString(clearSessions_) + "</clearSessions>";

   DisconnectQos::ClientPropertyMap::const_iterator 
      iter = clientProperties_.begin();
   while (iter != clientProperties_.end()) {
      offset + "   <clientProperty name='" + (*iter).first + "'>" + (*iter).second + "</clientProperty>";
      iter++;
   }

   ret += offset + "</qos>";

   return ret;
}

}}}}


