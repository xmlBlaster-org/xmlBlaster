/*------------------------------------------------------------------------------
Name:      ConnectQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Defines ConnectQos, ReturnConnectQos and ConnectQosData
------------------------------------------------------------------------------*/

#ifndef _UTIL_QOS_CONNECTQOS_H
#define _UTIL_QOS_CONNECTQOS_H

#include <util/xmlBlasterDef.h>
#include <string>
#include <util/XmlQoSBase.h>
#include <util/StringTrim.h>
#include <util/ServerRef.h>
#include <util/qos/SessionQos.h>
#include <authentication/SecurityQos.h>
#include <util/Log.h>

#include <util/qos/address/AddressFactory.h>
#include <util/qos/address/Address.h>
#include <util/qos/address/CallbackAddress.h>
#include <util/qos/storage/QueuePropertyFactory.h>
#include <util/qos/storage/QueueProperty.h>
#include <util/qos/storage/CbQueueProperty.h>

#include <vector>

/**
 * <qos>\n") +
 *    <securityService type='htpasswd' version='1.0'>
 *      <![CDATA[
 *      <user>joe</user>
 *      <passwd>secret</passwd>
 *      ]]>
 *    </securityService>
 *    <session name='/node/heron/client/joe/-9' timeout='3600000' maxSessions='10' clearSessions='false' sessionId='4e56890ghdFzj0'/>
 *    <ptp>true</ptp>
 *    <!-- The client side queue: -->
 *    <queue relating='client' type='CACHE' version='1.0' maxMsg='1000' maxBytes='4000' onOverflow='exception'>
 *       <address type='IOR' sessionId='secretTokenForUpdateCheckOnClientSide'>
 *          IOR:10000010033200000099000010....
 *       </address>
 *    </queue>
 *    <!-- The server side callback queue: -->
 *    <queue relating='callback' type='CACHE' version='1.0' maxMsg='1000' maxBytes='4000' onOverflow='deadMessage'>
 *       <callback type='IOR' sessionId='secretTokenForUpdateCheckOnClientSide'>
 *          IOR:10000010033200000099000010....
 *          <burstMode collectTime='400' />
 *       </callback>
 *    </queue>
 * </qos>
 */


namespace org { namespace xmlBlaster { namespace util { namespace qos {

using namespace org::xmlBlaster::authentication;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos::address;
using namespace org::xmlBlaster::util::qos::storage;

class Dll_Export ConnectQosData
{
private:
   Global&     global_;
   Log&        log_;
   SecurityQos securityQos_;
   SessionQos  sessionQos_;
//   ServerRef   serverRef_;
   bool        ptp_;
   bool        isDirty_;
   bool        clusterNode_;
   bool        duplicateUpdates_;

   vector<Address>         addresses_;
   vector<CallbackAddress> cbAddresses_;
   vector<QueueProperty>   clientQueueProperties_;
   vector<CbQueueProperty> cbQueueProperties_;
   vector<ServerRef>       serverReferences_;

   friend class ConnectQosFactory;

   void copy(const ConnectQosData& data)
   {
      securityQos_            = data.securityQos_;
      sessionQos_             = data.sessionQos_;
//      serverRef_              = data.serverRef_;
      ptp_                    = data.ptp_;
      isDirty_                = data.isDirty_;
      clusterNode_            = data.clusterNode_;
      duplicateUpdates_       = data.duplicateUpdates_;
      serverReferences_       = data.serverReferences_;
      addresses_              = data.addresses_;
      cbAddresses_            = data.cbAddresses_;
      clientQueueProperties_  = data.clientQueueProperties_;
      cbQueueProperties_      = data.cbQueueProperties_;

   }

public:
   ConnectQosData(Global& global, const string& user="", const string& passwd="", long publicSessionId=-1);
   ConnectQosData(const ConnectQosData& data);
   ConnectQosData& operator =(const ConnectQosData& data);
   bool getPtp() const;
//   string getPtpAsString() const;
   string getBoolAsString(bool boolVal) const;
   void setPtp(bool ptp);
   void setSessionQos(const SessionQos& sessionQos);
   SessionQos getSessionQos() const;
   string getSessionId() const;
   string getUserId() const;
   string getCallbackType() const;
   void setSecurityQos(const SecurityQos& securityQos);
   SecurityQos getSecurityQos() const;
//   void setServerRef(const ServerRef& serverRef);
//   ServerRef getServerRef() const;
   void setClusterNode(bool clusterNode);
   bool isClusterNode() const;
   void setDuplicateUpdates(bool duplicateUpdates);
   bool isDuplicateUpdates() const;
   void addServerRef(const ServerRef& serverRef);
   vector<ServerRef> getServerReferences() const;
   /** returns the first found server reference */
   ServerRef getServerRef() const;

   string toXml(const string& extraOffset="");

   // methods for queues and addresses ...
   void setAddress(const Address& address);
   Address getAddress() const;

   void addCbAddress(const CallbackAddress& cbAddress);
   CallbackAddress getCbAddress() const;

   void addClientQueueProperty(const QueueProperty& prop);
   QueueProperty getClientQueueProperty() const;

   void addCbQueueProperty(const CbQueueProperty& prop);
   void setCbQueueProperty(const CbQueueProperty& prop);
   CbQueueProperty getSessionCbQueueProperty() const;

};

typedef ConnectQosData ConnectQos;

typedef ConnectQosData ConnectReturnQos;

}}}} // namespaces

#endif
