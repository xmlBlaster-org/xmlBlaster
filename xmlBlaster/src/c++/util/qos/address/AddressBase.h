/*------------------------------------------------------------------------------
Name:      org::xmlBlaster::util::qos::address::AddressBase.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding connect address and callback address std::string including protocol
Version:   $Id$
------------------------------------------------------------------------------*/

/**
 * Abstract helper class holding connect address and callback address std::string
 * and protocol std::string.
 * <p />
 * See examples in the implementing classes
 * @see org::xmlBlaster::util::qos::address::Address
 * @see org::xmlBlaster::util::qos::address::CallbackAddress
 */

#ifndef _UTIL_CFG_ADDRESSBASE_H
#define _UTIL_CFG_ADDRESSBASE_H

#include <util/xmlBlasterDef.h>
#include <util/Constants.h>
#include <util/I_Log.h>
#include <util/qos/ClientProperty.h>
#include <string>
#include <map>
#include <util/ReferenceCounterBase.h>
#include <util/ReferenceHolder.h>

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace address {

extern Dll_Export const int       DEFAULT_port;
extern Dll_Export const std::string    DEFAULT_type;
extern Dll_Export const std::string    DEFAULT_version;
extern Dll_Export const long      DEFAULT_collectTime;
extern Dll_Export const int       DEFAULT_burstModeMaxEntries;
extern Dll_Export const long      DEFAULT_burstModeMaxBytes;
extern Dll_Export const bool      DEFAULT_oneway;
extern Dll_Export const bool      DEFAULT_dispatcherActive;
extern Dll_Export const std::string    DEFAULT_compressType;
extern Dll_Export const long      DEFAULT_minSize;
extern Dll_Export const bool      DEFAULT_ptpAllowed;
extern Dll_Export const std::string    DEFAULT_sessionId;
extern Dll_Export const bool      DEFAULT_useForSubjectQueue;
extern Dll_Export std::string    DEFAULT_dispatchPlugin;
extern Dll_Export std::string    ATTRIBUTE_TAG;



class Dll_Export AddressBase : public org::xmlBlaster::util::ReferenceCounterBase
{
   friend class AddressFactory;

public:   typedef std::map<std::string, org::xmlBlaster::util::qos::ClientProperty> ClientPropertyMap;

private:
   int port_;

   /**
    * Sets the root xml tag, &lt;callback> or &lt;address>
    */
   void setRootTag(const std::string& rootTag)
   {
      rootTag_ = rootTag;
   }

protected:
   std::string  ME;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log&    log_;

   std::string rootTag_;

   /** The node id to which we want to connect */
   std::string nodeId_;

   /** TODO: Move this attribute to CbQueueProperty.java */
   long maxEntries_; // only used in org::xmlBlaster::util::qos::address::Address

   /** The unique address, e.g. the CORBA IOR std::string */
   mutable std::string address_;

   mutable std::string hostname_;
   bool isHardcodedHostname_; // = false; // set to true if setHostname() was explicitly called by user

   /** The unique protocol type, e.g. "IOR" */
   std::string type_; //  = DEFAULT_type;
   
   /** The protocol version, e.g. "1.0" */
   std::string version_; // = DEFAULT_version;
   
   /** BurstMode: The time to collect messages for publish/update */
   long collectTime_; //  = DEFAULT_collectTime;

   /**
    * How many messages maximum shall the callback thread take in one bulk out of the
    * callback queue and deliver to the client in one bulk. 
    * Defaults to all available of highest priority
    */
   int burstModeMaxEntries_; //  = -1

   /**
    * How many bytes maximum shall the callback thread take in one bulk out of the
    * callback queue and deliver to the client in one bulk. 
    */
   long burstModeMaxBytes_; //  = -1L
   
   /** Ping interval: pinging every given milliseconds */
   long pingInterval_; //  = getDefaultPingInterval();
   
   /** How often to retry if connection fails */
   int retries_; //  = getDefaultRetries();
   
   /** Delay between connection retries in milliseconds */
   long delay_; //  = getDefaultDelay();

   /**
    * Shall the update() or publish() messages be send oneway (no application level ACK). 
    * <p />
    * For more info read the CORBA spec. Only CORBA and our native SOCKET protocol support oneway.
    * Defaults to false (the update() or publish() has a return value and can throw an exception).
    */
   bool oneway_; // = DEFAULT_oneway;
   
   /**
    * Control if the dispatcher is activated on login, i.e. if it is 
    * able to deliver asynchronous messages from the queue.
    * defaults to true
    */
   bool dispatcherActive_; // = DEFAULT_dispatcherActive;

   /** Compress messages if set to "gzip" or "zip" */
   std::string compressType_; // = DEFAULT_compressType;
   
   /** Messages bigger this size in bytes are compressed */
   long minSize_; // = DEFAULT_minSize;
   
   /** PtP messages wanted? Defaults to true, false prevents spamming */
   bool ptpAllowed_; // = DEFAULT_ptpAllowed;
   
   /** The identifier sent to the callback client, the client can decide if he trusts this invocation */
   std::string sessionId_; // = DEFAULT_sessionId;

   /** Shall this session callback be used for subjectQueue messages as well? For &lt;callback> only */
   bool useForSubjectQueue_; // = DEFAULT_useForSubjectQueue;

   /**
    * Does client whish a dispatcher plugin. 
    * <p>
    * Set to "undef" forces to switch off, or e.g. "Priority,1.0" to access the PriorizedDispatchPlugin
    * </p>
    * <p>
    * Setting it to 'null' (which is the default) lets the server choose the plugin
    * </p>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/dispatch.control.plugin.html">The dispatch.control.plugin requirement</a>
    */
   std::string dispatchPlugin_; // = DEFAULT_dispatchPlugin;

   ClientPropertyMap attributes_; 

   void initHostname(const std::string& hostname)
   {
      hostname_ = hostname;
      address_  = ""; // reset cache
   }

   void copy(const AddressBase& addr);

   long defaultPingInterval_;
   int defaultRetries_;
   long defaultDelay_;

public:

   /**
    * common constructor
    */
   AddressBase(org::xmlBlaster::util::Global& global, const std::string& rootTag="");

   /**
    * copy constructor
    */
   AddressBase(const AddressBase& addr);

   /**
    * Assignment operator
    */
   AddressBase& operator =(const AddressBase& addr);

   virtual ~AddressBase();

   /**
    * A nice human readable name for this address (used for logging)
    */
   std::string getName();

   /**
    * Check if supplied address would connect to the address of this instance
    */
   bool isSameAddress(AddressBase& other);

   /**
    * Show some important settings for logging
    */
   std::string getSettings() const;

   void addAttribute(const ClientProperty& attribute);

   const ClientPropertyMap& getAttributes() const;

   /**
    * The address and callbackAddress may contain additional attributes
    * which are passed to the protocol plugin
    */
   std::string dumpAttributes(const std::string& extraOffset, bool clearText=false) const;

   /**
    * @param type    The protocol type, e.g. "IOR", "SOCKET", "XMLRPC"
    */
   void setType(const std::string& type);

   /**
    * @param version   The protocol version, e.g. "1.0"
    */
   void setVersion(const std::string& version);

   /**
    * Updates the internal address as well. 
    * @param host An IP or DNS
    */
   void setHostname(const std::string& host);

   /**
    * @return true if the bootstrapHostname is explicitly set by user with setHostname()
    * false if it is determined automatically
    */
   bool isHardcodedHostname();

   /**
    * Check if a bootstrapHostname is set already
    */
   bool hasHostname();

   /**
    * @return The Hostname, IP or "" if not known
    */
   std::string getHostname() const;

   /**
    * Set the bootstrapping port. 
    * Updates the internal address as well. 
    */
   void setPort(int port);

   int getPort() const;

   /**
    * Set the callback address, it should fit to the protocol-type.
    *
    * @param address The callback address, e.g. "et@mars.univers"
    */
   void setAddress(const std::string& address);

   /**
    * Returns the address.
    * @return e.g. "IOR:00001100022...." or "et@universe.com" or "socket://192.168.1.1:7607" or ""
    */
   std::string getRawAddress() const;

   /**
    * Returns the protocol type.
    * @return e.g. "SOCKET" or "IOR" (never null).
    */
   std::string getType() const;

   /**
    * Returns the protocol version.
    * @return e.g. "1.0" or null
    */
   std::string getVersion() const;

   /**
    * What to do if max retries is exhausted. 
    * <p />
    * This mode is currently not configurable, we always destroy the login session. 
    * This is interpreted only server side if callback fails.
    * @return Constants.ONEXHAUST_KILL_SESSION="killSession"
    */
   std::string getOnExhaust() const;

   /**
    * Kill login session if max callback retries is exhausted?
    */
   bool getOnExhaustKillSession() const;

   /**
    * BurstMode: The time span to collect messages before sending. 
    * @return The time to collect in milliseconds
    */
   long getCollectTime() const;

   /**
    * BurstMode: The time to collect messages for sending in a bulk. 
    * @param The time to collect in milliseconds
    */
   void setCollectTime(long collectTime);

   /**
    * How many messages maximum shall the callback thread take in one bulk out of the
    * callback queue and deliver to the client in one bulk. 
    */
   int getBurstModeMaxEntries() const;

   /**
    * How many messages maximum shall the callback thread take in one bulk out of the
    * callback queue and deliver to the client in one bulk. 
    * @param -1 takes all available messages from highest priority in a bulk (default)
    *        only limited by burstModeMaxBytes
    */
   void setBurstModeMaxEntries(int burstModeMaxEntries);

   /**
    * How many bytes maximum shall the callback thread take in one bulk out of the
    * callback queue and deliver to the client in one bulk. 
    */
   long getBurstModeMaxBytes() const;

   /**
    * How many bytes maximum shall the callback thread take in one bulk out of the
    * callback queue and deliver to the client in one bulk. 
    * @param -1 takes all available messages from highest priority in a bulk (default)
    *        only limited by burstModeMaxEntries
    */
   void setBurstModeMaxBytes(long BurstModeMaxBytes);

   /**
    * How long to wait between pings to the callback server. 
    * @return The pause time between pings in millis
    */
   long getPingInterval() const;

   /**
    * How long to wait between pings to the callback server. 
    * @param pingInterval The pause time between pings in millis
    */
   void setPingInterval(long pingInterval);

   /**
    * How often shall we retry callback attempt on callback failure
    * @return -1 forever, 0 no retry, > 0 number of retries
    */
   int getRetries() const;

   /**
    * How often shall we retry callback attempt on callback failure
    * @param -1 forever, 0 no retry, > 0 number of retries
    */
   void setRetries(int retries);

   /**
    * Delay between callback retries in milliseconds, defaults to one minute
    * @return The delay in millisconds
    */
   long getDelay() const;

   /**
    * Delay between callback retries in milliseconds, defaults to one minute
    */
   void setDelay(long delay);

   /**
    * Shall the publish() or callback update() message be oneway. 
    * Is only with CORBA and our native SOCKET protocol supported
    * @return true if you want to force oneway sending
    */
   bool oneway() const;

   /**
    * Shall the publish() or callback update() message be oneway. 
    * Is only with CORBA and our native SOCKET protocol supported
    * @param oneway false is default
    */
   void setOneway(bool oneway);

   /**
    * Inhibits/activates the delivery of asynchronous dispatches of messages.
    * @param dispatcherActive
    */
   void setDispatcherActive(bool dispatcherActive);
   
   /**
    * @return true if the dispatcher is currently activated, i.e. if it is 
    * able to deliver asynchronous messages from the queue.
    */
   bool isDispatcherActive() const;

   /**
    * @param Set if we accept point to point messages
    */
   void setPtpAllowed(bool ptpAllowed);

   /**
    * @return true if we may send PtP messages
    */
   bool isPtpAllowed();

   void setCompressType(const std::string& compressType);

   /**
    * The identifier sent to the callback client, the client can decide if he trusts this invocation
    * @return never null
    */
   std::string getSecretSessionId() const;

   /** The identifier sent to the callback client, the client can decide if he trusts this invocation */
   void setSecretSessionId(const std::string& sessionId);

   /**
    * Get the compression method. 
    * @return "" No compression
    */
   std::string getCompressType() const;

   /** 
    * Messages bigger this size in bytes are compressed. 
    * <br />
    * Note: This value is only used if compressType is set to a supported value
    * @return size in bytes
    */
   long getMinSize() const;

   /** 
    * Messages bigger this size in bytes are compressed. 
    * <br />
    * Note: This value is only evaluated if compressType is set to a supported value
    * @return size in bytes
    */
   void setMinSize(long minSize);

   /**
    * Specify your dispatcher plugin configuration. 
    * <p>
    * Set to "undef" to switch off, or to e.g. "Priority,1.0" to access the PriorizedDispatchPlugin
    * </p>
    * <p>
    * This overwrites the xmlBlaster.properties default setting e.g.:
    * <pre>
    * DispatchPlugin[Priority][1.0]=org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDispatchPlugin
    * DispatchPlugin[SlowMotion][1.0]=org.xmlBlaster.util.dispatch.plugins.motion.SlowMotion
    * DispatchPlugin/defaultPlugin=Priority,1.0
    * </pre>
    * </p>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/dispatch.control.plugin.html">The dispatch.control.plugin requirement</a>
    */
   void setDispatchPlugin(const std::string& dispatchPlugin);

   /**
    * @return "undef" or e.g. "Priority,1.0"
    */
   std::string getDispatchPlugin() const;

   const ClientPropertyMap& getClientProperties() const;

   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * Only none default values are dumped for performance reasons
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   std::string toXml(const std::string& extraOffset = "") const;
};

typedef org::xmlBlaster::util::ReferenceHolder<AddressBase> AddressBaseRef;

}}}}}

#endif



