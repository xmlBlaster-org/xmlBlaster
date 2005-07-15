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
#include <util/qos/address/AddressFactory.h>
#include <util/qos/address/Address.h>
#include <util/qos/address/CallbackAddress.h>
#include <util/qos/storage/QueuePropertyFactory.h>
#include <util/qos/storage/ClientQueueProperty.h>
#include <util/qos/storage/CbQueueProperty.h>
#include <util/qos/ClientProperty.h>

#include <vector>
#include <map>

namespace org { namespace xmlBlaster { namespace util { namespace qos {

/**
 * Holds the connect() QoS XML markup. 
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">The interface.connect requirement</a>
 */
class Dll_Export ConnectQosData
{

public:   typedef std::map<std::string, org::xmlBlaster::util::qos::ClientProperty> ClientPropertyMap;

private:
   org::xmlBlaster::util::Global&     global_;
   org::xmlBlaster::util::I_Log&        log_;
   mutable org::xmlBlaster::authentication::SecurityQos securityQos_;
   mutable org::xmlBlaster::util::qos::SessionQosRef  sessionQos_;
   bool        ptp_;
   bool        clusterNode_;
   bool        refreshSession_;
   bool        duplicateUpdates_;
   bool        reconnected_;
   std::string instanceId_;
   bool        persistent_;

   //std::vector<org::xmlBlaster::util::qos::address::Address>         addresses_;
   //std::vector<org::xmlBlaster::util::qos::address::CallbackAddress> cbAddresses_;
   std::vector<org::xmlBlaster::util::qos::storage::ClientQueueProperty>   clientQueueProperties_;
   org::xmlBlaster::util::qos::storage::CbQueueProperty         sessionCbQueueProperty_;
   std::vector<ServerRef>       serverReferences_;


   friend class ConnectQosFactory;

   /**
    * Used by xml parser only, the ServerRef is returned by xmlBlaster in ConnectReturnQos
    */
   void addServerRef(const org::xmlBlaster::util::ServerRef& serverRef);

   void copy(const ConnectQosData& data)
   {
      securityQos_            = data.securityQos_;
      org::xmlBlaster::util::qos::SessionQosData* p = data.sessionQos_.getElement();
      org::xmlBlaster::util::qos::SessionQosData* p2 = new org::xmlBlaster::util::qos::SessionQosData(*p);
      org::xmlBlaster::util::qos::SessionQosRef r(p2);
      sessionQos_             = r;
      ptp_                    = data.ptp_;
      clusterNode_            = data.clusterNode_;
      refreshSession_         = data.refreshSession_;
      duplicateUpdates_       = data.duplicateUpdates_;
      serverReferences_       = data.serverReferences_;
      //addresses_              = data.addresses_;
      //cbAddresses_            = data.cbAddresses_;
      clientQueueProperties_  = data.clientQueueProperties_;
      sessionCbQueueProperty_ = data.sessionCbQueueProperty_;
      clientProperties_       = data.clientProperties_;
      reconnected_            = data.reconnected_;
      instanceId_             = data.instanceId_;
      persistent_             = data.persistent_;
   }

protected:
   ClientPropertyMap clientProperties_; 

public:
   ConnectQosData(org::xmlBlaster::util::Global& global, const std::string& user="", const std::string& passwd="", long publicSessionId=0);
   ConnectQosData(const ConnectQosData& data);
   ConnectQosData& operator =(const ConnectQosData& data);
   bool getPtp() const;
   const std::string& getBoolAsString(bool boolVal) const;
   void setPtp(bool ptp);
   void setSessionQos(const org::xmlBlaster::util::qos::SessionQos& sessionQos);
   org::xmlBlaster::util::qos::SessionQos& getSessionQos() const;
   void setSessionQos(org::xmlBlaster::util::qos::SessionQosRef sessionQos);
   org::xmlBlaster::util::qos::SessionQosRef getSessionQosRef() const;
   std::string getSecretSessionId() const;
   std::string getUserId() const;
   std::string getCallbackType() const;
   void setSecurityQos(const org::xmlBlaster::authentication::SecurityQos& securityQos);
   org::xmlBlaster::authentication::SecurityQos& getSecurityQos() const;
   void setClusterNode(bool clusterNode);
   bool isClusterNode() const;
   /**
    * Extend the session lifetime. 
    * @param refreshSession true: The client notifies xmlBlaster that it is alive
    * and the login session is extended
    */
   void setRefreshSession(bool refreshSession);
   bool isRefreshSession() const;
   void setDuplicateUpdates(bool duplicateUpdates);
   bool isDuplicateUpdates() const;
   /**
    * Returned in ConnectReturnQos from xmlBlaster showing all access addresses. 
    */
   const std::vector<ServerRef> getServerReferences() const;

   /**
    * Used for ConnetReturnQos only. 
    * @return true A client has reconnected to an existing session
    */
   bool isReconnected() const;
   void setReconnected(bool reconnected);

   /**
    * Unique id of the xmlBlaster server (or a client), changes on each restart. 
    * If 'node/heron' is restarted, the instanceId changes.
    * @return nodeId + timestamp, '/node/heron/instanceId/33470080380'
    */
   std::string getInstanceId() const;
   void setInstanceId(std::string instanceId);

   bool isPersistent() const;
   void setPersistent(bool persistent);
   
   /**
    * returns the first found server reference
    * delivered with return QoS of a connect() call.
    */
   org::xmlBlaster::util::ServerRef getServerRef();

   std::string toXml(const std::string& extraOffset="") const;

   // methods for queues and addresses ...

   /**
    * @param address You need to create the address with 'new Address()', we take care to delete it.
    *                Don't pass any Address instance from the stack.
    */
   void setAddress(const org::xmlBlaster::util::qos::address::AddressBaseRef& address);
   org::xmlBlaster::util::qos::address::AddressBaseRef getAddress();

   /**
    * @param cbAddress We take a copy of this so you can destroy your address after setting.
    *                Note that if you work on your address object later it does not change
    *                the address in ConnectQos
    */
   void addCbAddress(const org::xmlBlaster::util::qos::address::AddressBaseRef& cbAddress);
   org::xmlBlaster::util::qos::address::AddressBaseRef getCbAddress();

   /**
    * @param prop We take a copy of this so you can destroy your property after setting.
    *             Note that if you work on your object later it does not change
    *             the setting in ConnectQos
    */
   void addClientQueueProperty(const org::xmlBlaster::util::qos::storage::ClientQueueProperty& prop);

   /**
    * Access the configuration settings of the client side queue and server address. 
    * @return If no instance exists it will be created on the fly and initialized
    * with the current environment settings and command line arguments
    */
   org::xmlBlaster::util::qos::storage::ClientQueueProperty& getClientQueueProperty();

   /**
    * @param prop We take a copy of this so you can destroy your property after setting.
    *             Note that if you work on your object later it does not change
    *             the setting in ConnectQos
    */
   void setSessionCbQueueProperty(const org::xmlBlaster::util::qos::storage::CbQueueProperty& prop);

   /**
    * Access the configuration settings of the server side callback queue and callback address. 
    * @return If no instance exists it will be created on the fly and initialized
    * with the current environment settings and command line arguments
    */
   org::xmlBlaster::util::qos::storage::CbQueueProperty& getSessionCbQueueProperty();

   std::string dumpClientProperties(const std::string& extraOffset, bool clearText=false) const;

   /**
    * Add a client property. 
    * @param clientProperty
    * @see ClientProperty
    */
   void addClientProperty(const ClientProperty& clientProperty);

   /**
    * Add a client property key and value
    * @param name
    * @param value "vector<unsigned char>" and "unsigned char *" is treated as a blob
    * @see ClientProperty::#ClientProperty
    */
   template <typename T_VALUE> void addClientProperty(
            const std::string& name,
            const T_VALUE& value,
            const std::string& type="",
            const std::string& encoding="");

   /**
    * Access the value for the given name, if not found returns the defaultValue. 
    * @return A copy of the given defaultValue if none was found
    */
   template <typename T_VALUE> T_VALUE getClientProperty(
            const std::string& name,
            const T_VALUE& defaultValue);
        
   const ClientPropertyMap& getClientProperties() const;
};

template <typename T_VALUE> void ConnectQosData::addClientProperty(
               const std::string& name, const T_VALUE& value,
                                        const std::string& type, const std::string& encoding)
{
   org::xmlBlaster::util::qos::ClientProperty clientProperty(name, value, type, encoding);
   clientProperties_.insert(ClientPropertyMap::value_type(name, clientProperty));   
}

template <typename T_VALUE> T_VALUE ConnectQosData::getClientProperty(
               const std::string& name, const T_VALUE& defaultValue)
{
   ClientPropertyMap::const_iterator iter = clientProperties_.find(name);
   if (iter != clientProperties_.end()) {
      T_VALUE tmp;
      (*iter).second.getValue(tmp);
      return tmp;
   }
   return defaultValue;
}

typedef ConnectQosData ConnectQos;

typedef ConnectQosData ConnectReturnQos;

}}}} // namespaces

#endif
