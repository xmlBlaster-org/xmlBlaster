/*------------------------------------------------------------------------------
Name:      DisconnectQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id$
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
   clearClientQueue_   = false;
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

void DisconnectQos::addClientProperty(const ClientProperty& clientProperty)
{
   clientProperties_.insert(ClientPropertyMap::value_type(clientProperty.getName(), clientProperty));   
}

void DisconnectQos::addClientProperty(const std::string& key, const std::string& value,
            const std::string& type, const std::string& encoding, const std::string& charset)
{
   org::xmlBlaster::util::qos::ClientProperty clientProperty(key, value, type, encoding, charset);
   clientProperties_.insert(ClientPropertyMap::value_type(key, clientProperty));   
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

   const bool clearText=false;
   QosData::ClientPropertyMap::const_iterator iter = clientProperties_.begin();
   while (iter != clientProperties_.end()) {
      const ClientProperty& cp = (*iter).second;
      ret += cp.toXml(extraOffset, clearText);
      iter++;
   }

   ret += offset + "</qos>";

   return ret;
}

   bool DisconnectQos::getClearClientQueue() const {
      return clearClientQueue_;
   }

   void DisconnectQos::setClearClientQueue(bool clearClientQueue) {
      clearClientQueue_ = clearClientQueue;
   }


}}}}


