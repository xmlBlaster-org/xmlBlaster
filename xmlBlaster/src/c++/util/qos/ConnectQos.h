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
#include <util/ServerRef.h>
#include <util/qos/SessionQos.h>
#include <authentication/SecurityQos.h>
#include <util/Log.h>

#include <util/qos/address/AddressFactory.h>
#include <util/qos/address/Address.h>
#include <util/qos/address/CallbackAddress.h>
#include <util/qos/storage/QueuePropertyFactory.h>
#include <util/qos/storage/ClientQueueProperty.h>
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
 *    <queue relating='client' type='CACHE' version='1.0' maxEntries='1000' maxBytes='4000' onOverflow='exception'>
 *       <address type='IOR' sessionId='secretTokenForUpdateCheckOnClientSide'>
 *          IOR:10000010033200000099000010....
 *       </address>
 *    </queue>
 *    <!-- The server side callback queue: -->
 *    <queue relating='callback' type='CACHE' version='1.0' maxEntries='1000' maxBytes='4000' onOverflow='deadMessage'>
 *       <callback type='IOR' sessionId='secretTokenForUpdateCheckOnClientSide'>
 *          IOR:10000010033200000099000010....
 *          <burstMode collectTime='400' />
 *       </callback>
 *    </queue>
 * </qos>
 */
namespace org { namespace xmlBlaster { namespace util { namespace qos {

class Dll_Export ConnectQosData
{
private:
   org::xmlBlaster::util::Global&     global_;
   org::xmlBlaster::util::Log&        log_;
   mutable org::xmlBlaster::authentication::SecurityQos securityQos_;
   mutable org::xmlBlaster::util::qos::SessionQos  sessionQos_;
   bool        ptp_;
   bool        clusterNode_;
   bool        duplicateUpdates_;

   std::vector<org::xmlBlaster::util::qos::address::Address>         addresses_;
   std::vector<org::xmlBlaster::util::qos::address::CallbackAddress> cbAddresses_;
   std::vector<org::xmlBlaster::util::qos::storage::ClientQueueProperty>   clientQueueProperties_;
   org::xmlBlaster::util::qos::storage::CbQueueProperty         sessionCbQueueProperty_;
   std::vector<ServerRef>       serverReferences_;

   friend class ConnectQosFactory;

   void copy(const ConnectQosData& data)
   {
      securityQos_            = data.securityQos_;
      sessionQos_             = data.sessionQos_;
      ptp_                    = data.ptp_;
      clusterNode_            = data.clusterNode_;
      duplicateUpdates_       = data.duplicateUpdates_;
      serverReferences_       = data.serverReferences_;
      addresses_              = data.addresses_;
      cbAddresses_            = data.cbAddresses_;
      clientQueueProperties_  = data.clientQueueProperties_;
      sessionCbQueueProperty_ = data.sessionCbQueueProperty_;
   }

public:
   ConnectQosData(org::xmlBlaster::util::Global& global, const std::string& user="", const std::string& passwd="", long publicSessionId=0);
   ConnectQosData(const ConnectQosData& data);
   ConnectQosData& operator =(const ConnectQosData& data);
   bool getPtp() const;
   const std::string& getBoolAsString(bool boolVal) const;
   void setPtp(bool ptp);
   void setSessionQos(const org::xmlBlaster::util::qos::SessionQos& sessionQos);
   org::xmlBlaster::util::qos::SessionQos& getSessionQos() const;
   std::string getSecretSessionId() const;
   std::string getUserId() const;
   std::string getCallbackType() const;
   void setSecurityQos(const org::xmlBlaster::authentication::SecurityQos& securityQos);
   org::xmlBlaster::authentication::SecurityQos& getSecurityQos() const;
   void setClusterNode(bool clusterNode);
   bool isClusterNode() const;
   void setDuplicateUpdates(bool duplicateUpdates);
   bool isDuplicateUpdates() const;
   void addServerRef(const org::xmlBlaster::util::ServerRef& serverRef);
   std::vector<ServerRef> getServerReferences() const;
   /** returns the first found server reference */
   org::xmlBlaster::util::ServerRef getServerRef() const;

   std::string toXml(const std::string& extraOffset="") const;

   // methods for queues and addresses ...
   void setAddress(const org::xmlBlaster::util::qos::address::Address& address);
   org::xmlBlaster::util::qos::address::Address getAddress() const;

   void addCbAddress(const org::xmlBlaster::util::qos::address::CallbackAddress& cbAddress);
   org::xmlBlaster::util::qos::address::CallbackAddress getCbAddress() const;

   void addClientQueueProperty(const org::xmlBlaster::util::qos::storage::ClientQueueProperty& prop);
   org::xmlBlaster::util::qos::storage::ClientQueueProperty getClientQueueProperty() const;

   void setSessionCbQueueProperty(const org::xmlBlaster::util::qos::storage::CbQueueProperty& prop);
   org::xmlBlaster::util::qos::storage::CbQueueProperty getSessionCbQueueProperty() const;

};

typedef ConnectQosData ConnectQos;

typedef ConnectQosData ConnectReturnQos;

}}}} // namespaces

#endif
