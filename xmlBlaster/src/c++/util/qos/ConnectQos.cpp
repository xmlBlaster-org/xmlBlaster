/*------------------------------------------------------------------------------
Name:      ConnectQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementation of ConnectQos (ConnectReturnQos ConnectQosData)
------------------------------------------------------------------------------*/

#include <util/qos/ConnectQos.h>
#include <util/Global.h>
#include <boost/lexical_cast.hpp>
#include <util/Global.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace org::xmlBlaster::authentication;
using namespace org::xmlBlaster::util;
using namespace boost;

/*---------------------------- ConnectQosData --------------------------------*/

ConnectQosData::ConnectQosData(Global& global, const string& user, const string& passwd, long publicSessionId)
    : global_(global),
      log_(global.getLog("core")),
      securityQos_(global, user, passwd),
      sessionQos_(global, user, publicSessionId),
      addresses_(),
      cbAddresses_(),
      clientQueueProperties_(),
      cbQueueProperties_(),
      serverReferences_()
{
   clusterNode_      = false;
   duplicateUpdates_ = false;
}

ConnectQosData::ConnectQosData(const ConnectQosData& data)
    : global_(data.global_),
      log_(data.log_),
      securityQos_(data.securityQos_),
      sessionQos_(data.sessionQos_),
      addresses_(data.addresses_),
      cbAddresses_(data.cbAddresses_),
      clientQueueProperties_(data.clientQueueProperties_),
      cbQueueProperties_(data.cbQueueProperties_),
      serverReferences_(data.serverReferences_)
//      serverRef_(data.serverRef_)
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

string ConnectQosData::getBoolAsString(bool boolVal) const
{
   if (boolVal == true) return string("true");
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

string ConnectQosData::getSecretSessionId() const
{
   return sessionQos_.getSecretSessionId();
}

string ConnectQosData::getUserId() const
{
//   return securityQos_.getUserId();
   return sessionQos_.getAbsoluteName();
}

string ConnectQosData::getCallbackType() const
{
   if (serverReferences_.empty()) {
      if (cbQueueProperties_.empty()) return "IOR";
      return (*cbQueueProperties_.begin()).getType();
   }
   return (*serverReferences_.begin()).getType();
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

/*
void ConnectQosData::setServerRef(const ServerRef& serverRef)
{
   serverRef_ = serverRef;
}

ServerRef ConnectQosData::getServerRef() const
{
   return serverRef_;
}
*/

void ConnectQosData::setClusterNode(bool clusterNode)
{
   clusterNode_ = clusterNode;
}

bool ConnectQosData::isClusterNode() const
{
   return clusterNode_;
}

void ConnectQosData::setDuplicateUpdates(bool duplicateUpdates)
{
   duplicateUpdates_ = duplicateUpdates;
}

bool ConnectQosData::isDuplicateUpdates() const
{
   return duplicateUpdates_;
}

void ConnectQosData::addServerRef(const ServerRef& serverRef)
{
   serverReferences_.insert(serverReferences_.begin(), serverRef);
}

vector<ServerRef> ConnectQosData::getServerReferences() const
{
   return serverReferences_;
}

ServerRef ConnectQosData::getServerRef() const
{
   if (serverReferences_.empty()) return ServerRef("IOR");
   return *(serverReferences_.begin());
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

void ConnectQosData::addClientQueueProperty(const QueueProperty& prop)
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

void ConnectQosData::setCbQueueProperty(const CbQueueProperty& prop)
{
   if (!cbQueueProperties_.empty())
      cbQueueProperties_.erase(cbQueueProperties_.begin());
   cbQueueProperties_.insert(cbQueueProperties_.begin(), prop);
}

CbQueueProperty ConnectQosData::getSessionCbQueueProperty() const
{
   if (cbQueueProperties_.empty())
      return CbQueueProperty(global_, Constants::RELATING_CALLBACK, "");
   return *(cbQueueProperties_.begin());
}

/*
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
*/

/**
 * Dump state of this object into a XML ASCII string.
 * <br>
 * @param extraOffset indenting of tags for nice output
 * @return internal state of the RequestBroker as a XML ASCII string
 */
string ConnectQosData::toXml(const string& extraOffset)
{
   string offset = "\n" + extraOffset;
   string ret;
   ret += offset + string("<qos>");

   // <securityService ...
   ret += securityQos_.toXml(extraOffset);
   ret += offset + string("   <ptp>") + getBoolAsString(ptp_)  + string("</ptp>");

   if (isClusterNode())
      ret += offset + string("   <isClusterNode>") + getBoolAsString(isClusterNode()) + string("</isClusterNode>");

      if (isDuplicateUpdates() == false)
         ret += offset + string("   <duplicateUpdates>") + getBoolAsString(isDuplicateUpdates()) + string("</duplicateUpdates>");

      ret += sessionQos_.toXml(extraOffset);

      {  // client queue properties 
         vector<QueueProperty>::iterator
            iter = clientQueueProperties_.begin();
         while (iter != clientQueueProperties_.end()) {
            ret += (*iter).toXml(extraOffset);
            iter++;
         }
      }

      {  //callback queue properties
         vector<CbQueueProperty>::iterator
            iter = cbQueueProperties_.begin();
         while (iter != cbQueueProperties_.end()) {
            ret += (*iter).toXml(extraOffset);
            iter++;
         }
      }
      {  //serverReferences
         vector<ServerRef>::iterator
            iter = serverReferences_.begin();
         while (iter != serverReferences_.end()) {
            ret += (*iter).toXml(extraOffset);
            iter++;
         }
      }

      ret += offset + string("</qos>");
      return ret;
   }

}}}} // namespaces

