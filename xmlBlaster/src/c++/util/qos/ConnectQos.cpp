/*------------------------------------------------------------------------------
Name:      ConnectQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementation of ConnectQos (ConnectReturnQos ConnectQosData)
------------------------------------------------------------------------------*/

#include <util/qos/ConnectQos.h>
#include <util/Global.h>
// #include <util/XmlBlasterException>
#include <boost/lexical_cast.hpp>

namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace org::xmlBlaster::authentication;
using namespace org::xmlBlaster::util;
using boost::lexical_cast;

/*---------------------------- ConnectQosData --------------------------------*/

ConnectQosData::ConnectQosData(Global& global)
    : global_(global),
      log_(global.getLog("core")),
      securityQos_(global),
      serverRef_("")
{
}

ConnectQosData::ConnectQosData(const ConnectQosData& data)
    : global_(data.global_),
      log_(data.log_),
      securityQos_(data.securityQos_),
      serverRef_(data.serverRef_)
{
   copy(data);
}

ConnectQosData& ConnectQosData::operator =(const ConnectQosData& data)
{
   copy(data);
   return *this;
}


bool ConnectQosData::getPtp() const
{
   return ptp_;
}

string ConnectQosData::getPtpAsString() const
{
   if (ptp_ == true) return string("true");
   return string("false");
}

void ConnectQosData::setPtp(bool ptp)
{
   ptp_ = ptp;
}

void ConnectQosData::setSessionQos(const SessionQos& sessionQos)
{
   sessionQos_ = sessionQos;
}

SessionQos ConnectQosData::getSessionQos() const
{
   return sessionQos_;
}

string ConnectQosData::getSessionId() const
{
   return sessionQos_.getSessionId();
}

string ConnectQosData::getUserId() const
{
//   return securityQos_.getUserId();
   return sessionQos_.getName();
}

string ConnectQosData::getCallbackType() const
{
   return serverRef_.getType();
//   return getCallbackAddress().getType();
}

void ConnectQosData::setSecurityQos(const SecurityQos& securityQos)
{
   securityQos_ = securityQos;
}

SecurityQos ConnectQosData::getSecurityQos() const
{
   return securityQos_;
}

void ConnectQosData::setServerRef(const ServerRef& serverRef)
{
   serverRef_ = serverRef;
}

ServerRef ConnectQosData::getServerRef() const
{
   return serverRef_;
}

// methods for queues and addresses ...

void ConnectQosData::setAddress(const Address& address)
{
   addresses_.insert(addresses_.begin(), address);
}

Address ConnectQosData::getAddress() const
{
   if (addresses_.empty()) return Address(global_);
   return *(addresses_.begin());
}

void ConnectQosData::addCbAddress(const CallbackAddress& cbAddress)
{
   cbAddresses_.insert(cbAddresses_.begin(), cbAddress);
}

CallbackAddress ConnectQosData::getCbAddress() const
{
   if (cbAddresses_.empty()) return CallbackAddress(global_);
   return *(cbAddresses_.begin());
}

void ConnectQosData::addClientQueueProperty(const QueueProperty prop)
{
   clientQueueProperties_.insert(clientQueueProperties_.begin(), prop);
}

QueueProperty ConnectQosData::getClientQueueProperty() const
{
   if (clientQueueProperties_.empty())
      return QueueProperty(global_, "");
   return *(clientQueueProperties_.begin());
}

void ConnectQosData::addCbQueueProperty(const CbQueueProperty& prop)
{
   cbQueueProperties_.insert(cbQueueProperties_.begin(), prop);
}

CbQueueProperty ConnectQosData::getCbQueueProperty() const
{
   if (cbQueueProperties_.empty())
      return CbQueueProperty(global_, "", "");
   return *(cbQueueProperties_.begin());
}

string ConnectQosData::toXml(const string& extraOffset) const
{
   string ret = string("<qos>\n") +
                getSecurityQos().toXml() +
                getSessionQos().toXml() +
                string("   <ptp>") + getPtpAsString() + string("</ptp>\n") +
                string("\n") + 
                string("   <!-- client queue here ... (still missing) -->\n") +
                string("   <!-- callback queue here ... (still missing) -->\n") +
                string("\n") + 
                string("</qos>\n");

   return ret;
}

}}}} // namespaces

