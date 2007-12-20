/*------------------------------------------------------------------------------
Name:      ConnectQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementation of ConnectQos (ConnectReturnQos ConnectQosData)
------------------------------------------------------------------------------*/

#include <util/qos/ConnectQos.h>
#include <util/Global.h>
#include <util/Constants.h>
#include <util/lexical_cast.h>
#include <util/Global.h>


namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace std;
using namespace org::xmlBlaster::authentication;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos::storage;
using namespace org::xmlBlaster::util::qos::address;

/*---------------------------- ConnectQosData --------------------------------*/

ConnectQosData::ConnectQosData(Global& global, const string& user, const string& passwd, long publicSessionId)
    : global_(global),
      log_(global.getLog("org.xmlBlaster.util.qos")),
      securityQos_(global, user, passwd),
      sessionQos_(new SessionQos(global, user, publicSessionId)),
      ptp_(true),
      //addresses_(),
      //cbAddresses_(),
      clientQueueProperties_(),
      sessionCbQueueProperty_(global, Constants::RELATING_CALLBACK, ""),
      serverReferences_(),
      clientProperties_()
{
   clusterNode_      = false;
   refreshSession_   = false;
   duplicateUpdates_ = true;
   reconnected_      = false;
   instanceId_       = global_.getInstanceId();
   persistent_       = false;
   if (user=="") { // Copy env setting to SecurityQos
      securityQos_.setUserId(sessionQos_->getSessionName()->getSubjectId());
   }
   getAddress();      // Force creation, to read environment, Note: The CB address is only read if a callback server is created
}

ConnectQosData::ConnectQosData(const ConnectQosData& data)
    : global_(data.global_),
      log_(data.log_),
      securityQos_(data.securityQos_),
      sessionQos_(data.sessionQos_),
      //addresses_(data.addresses_),
      //cbAddresses_(data.cbAddresses_),
      clientQueueProperties_(data.clientQueueProperties_),
      sessionCbQueueProperty_(data.sessionCbQueueProperty_),
      serverReferences_(data.serverReferences_),
      clientProperties_()
{
   copy(data);
}

ConnectQosData& ConnectQosData::operator =(const ConnectQosData& data)
{
   copy(data);
   return *this;
}

ConnectQosData::~ConnectQosData()
{
}


bool ConnectQosData::getPtp() const
{
   return ptp_;
}

const string& ConnectQosData::getBoolAsString(bool val) const
{
   return global_.getBoolAsString(val);
}

void ConnectQosData::setPtp(bool ptp)
{
   ptp_ = ptp;
}

void ConnectQosData::setSessionQos(const SessionQos& sessionQos)
{
   SessionQos *p = new SessionQos(sessionQos);
   SessionQosRef r(p);
   sessionQos_ = r;
}

SessionQos& ConnectQosData::getSessionQos() const
{
   return *sessionQos_;
}


void ConnectQosData::setSessionQos(SessionQosRef sessionQos) {
   sessionQos_ = sessionQos;
}

SessionQosRef ConnectQosData::getSessionQosRef() const {
   return sessionQos_;
}



string ConnectQosData::getSecretSessionId() const
{
   return sessionQos_->getSecretSessionId();
}

string ConnectQosData::getUserId() const
{
   return sessionQos_->getAbsoluteName();
}

string ConnectQosData::getCallbackType() const
{
   return sessionCbQueueProperty_.getType();
}

void ConnectQosData::setSecurityQos(const SecurityQos& securityQos)
{
   securityQos_ = securityQos;
}

SecurityQos& ConnectQosData::getSecurityQos() const
{
   return securityQos_;
}

void ConnectQosData::setClusterNode(bool clusterNode)
{
   clusterNode_ = clusterNode;
}

bool ConnectQosData::isClusterNode() const
{
   return clusterNode_;
}

void ConnectQosData::setRefreshSession(bool refreshSession)
{
   refreshSession_ = refreshSession;
}

bool ConnectQosData::isRefreshSession() const
{
   return refreshSession_;
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

const vector<ServerRef> ConnectQosData::getServerReferences() const
{
   return serverReferences_;
}

ServerRef ConnectQosData::getServerRef()
{
   if (serverReferences_.empty()) {
      addServerRef(ServerRef(Global::getDefaultProtocol()));
   }
   return *(serverReferences_.begin());
}

// methods for queues and addresses ...

void ConnectQosData::setAddress(const AddressBaseRef& address)
{
   getClientQueueProperty().setAddress(address);
   //addresses_.insert(addresses_.begin(), address);
}

AddressBaseRef ConnectQosData::getAddress()
{
   return getClientQueueProperty().getCurrentAddress();
   //return reinterpret_cast<Address&>(ab);
   /*
   if (addresses_.empty()) {
      setAddress(Address(global_));
   }
   return *(addresses_.begin());
   */
}

void ConnectQosData::addCbAddress(const AddressBaseRef& cbAddress)
{
   sessionCbQueueProperty_.setCallbackAddress(cbAddress);
   //cbAddresses_.insert(cbAddresses_.begin(), cbAddress);
}

AddressBaseRef ConnectQosData::getCbAddress()
{
   return sessionCbQueueProperty_.getCurrentCallbackAddress();
   //org::xmlBlaster::util::qos::address::AddressBaseRef ab = sessionCbQueueProperty_.getCurrentCallbackAddress();
   //return ab; reinterpret_cast<CallbackAddress&>(ab);//sessionCbQueueProperty_.getCurrentCallbackAddress();
   //if (cbAddresses_.empty()) {
   //   addCbAddress(CallbackAddress(global_));
   //}
   //return *(cbAddresses_.begin());
}

void ConnectQosData::addClientQueueProperty(const ClientQueueProperty& prop)
{
   clientQueueProperties_.insert(clientQueueProperties_.begin(), prop);
}

ClientQueueProperty& ConnectQosData::getClientQueueProperty()
{
   if (clientQueueProperties_.empty()) {
      addClientQueueProperty(ClientQueueProperty(global_, ""));
   }
   return *(clientQueueProperties_.begin());
}

void ConnectQosData::setSessionCbQueueProperty(const CbQueueProperty& prop)
{
   sessionCbQueueProperty_ = prop;
}

CbQueueProperty& ConnectQosData::getSessionCbQueueProperty()
{
   return sessionCbQueueProperty_;
}

void ConnectQosData::addClientProperty(const ClientProperty& clientProperty)
{
   clientProperties_.insert(ClientPropertyMap::value_type(clientProperty.getName(), clientProperty));   
}

const ConnectQosData::ClientPropertyMap& ConnectQosData::getClientProperties() const
{
   return clientProperties_;
}

bool ConnectQosData::isReconnected() const
{
   return reconnected_;
}

void ConnectQosData::setReconnected(bool reconnected)
{
   reconnected_ = reconnected;
}

std::string ConnectQosData::getInstanceId() const
{
   return instanceId_;
}

void ConnectQosData::setInstanceId(std::string instanceId)
{
   instanceId_ = instanceId;
}

/**
 * @param persistent mark a message as persistent
 */
void ConnectQosData::setPersistent(bool persistent)
{
   persistent_ = persistent;
}

/**
 * @return true/false
 */
bool ConnectQosData::isPersistent() const
{
   return persistent_;
}

string ConnectQosData::dumpClientProperties(const string& extraOffset, bool clearText) const
{
   string ret = "";
   QosData::ClientPropertyMap::const_iterator iter = clientProperties_.begin();
   while (iter != clientProperties_.end()) {
      const ClientProperty& cp = (*iter).second;
      ret += cp.toXml(extraOffset, clearText);
      iter++;
   }
   return ret;
}

/**
 * Dump state of this object into a XML ASCII string.
 * <br>
 * @param extraOffset indenting of tags for nice output
 * @return internal state of the RequestBroker as a XML ASCII string
 */
string ConnectQosData::toXml(const string& extraOffset) const
{
   string offset = Constants::OFFSET + extraOffset;
   string offset2 = offset + Constants::INDENT;
   string indent = extraOffset+Constants::INDENT;
   string ret;
   ret += offset + string("<qos>");

   // <securityService ...
   ret += securityQos_.toXml(indent);
   ret += offset2 + string("<ptp>") + getBoolAsString(ptp_)  + string("</ptp>");

   if (isClusterNode())
      ret += offset2 + string("<clusterNode>") + getBoolAsString(isClusterNode()) + string("</clusterNode>");

   if (isRefreshSession())
      ret += offset2 + string("<refreshSession>") + getBoolAsString(isRefreshSession()) + string("</refreshSession>");

   if (isDuplicateUpdates() == false)
      ret += offset2 + string("<duplicateUpdates>") + getBoolAsString(isDuplicateUpdates()) + string("</duplicateUpdates>");

   if (isReconnected())
      ret += offset + " <reconnected/>";

   if (getInstanceId().length() > 0)
      ret += offset + " <instanceId>" + getInstanceId() + "</instanceId>";

   if (isPersistent())
      ret += offset + " <persistent/>";

   ret += sessionQos_->toXml(indent);

   {  // client queue properties 
      vector<ClientQueueProperty>::const_iterator
         iter = clientQueueProperties_.begin();
      while (iter != clientQueueProperties_.end()) {
         ret += (*iter).toXml(indent);
         iter++;
      }
   }

   ret += sessionCbQueueProperty_.toXml(indent);

   {  //serverReferences
      vector<ServerRef>::const_iterator
         iter = serverReferences_.begin();
      while (iter != serverReferences_.end()) {
         ret += (*iter).toXml(indent);
         iter++;
      }
   }

   ret += dumpClientProperties(offset2);

   ret += offset + string("</qos>");
   return ret;
}

}}}} // namespaces

