/*------------------------------------------------------------------------------
Name:      AddressBase.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding connect address and callback address string including protocol
Version:   $Id: AddressBase.h,v 1.8 2003/02/13 18:59:28 laghi Exp $
------------------------------------------------------------------------------*/

/**
 * Abstract helper class holding connect address and callback address string
 * and protocol string.
 * <p />
 * See examples in the implementing classes
 * @see Address
 * @see CallbackAddress
 */

#ifndef _UTIL_CFG_ADDRESSBASE_H
#define _UTIL_CFG_ADDRESSBASE_H

#include <util/xmlBlasterDef.h>
#include <util/Constants.h>
#include <util/Log.h>

#include <string>

using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace address {

extern Dll_Export const int       DEFAULT_port;
extern Dll_Export const string    DEFAULT_type;
extern Dll_Export const string    DEFAULT_version;
extern Dll_Export const long      DEFAULT_collectTime;
extern Dll_Export const long      DEFAULT_collectTimeOneway;
extern Dll_Export const bool      DEFAULT_oneway;
extern Dll_Export const string    DEFAULT_compressType;
extern Dll_Export const long      DEFAULT_minSize;
extern Dll_Export const bool      DEFAULT_ptpAllowed;
extern Dll_Export const string    DEFAULT_sessionId;
extern Dll_Export const bool      DEFAULT_useForSubjectQueue;
extern Dll_Export       string    DEFAULT_dispatchPlugin;



class Dll_Export AddressBase
{
   friend class AddressFactory;

private:
   int port_;

   /**
    * Sets the root xml tag, &lt;callback> or &lt;address>
    */
   void setRootTag(const string& rootTag)
   {
      rootTag_ = rootTag;
   }

protected:
   string  ME;
   Global& global_;
   Log&    log_;

   string rootTag_;

   /** The node id to which we want to connect */
   string nodeId_;

   /** TODO: Move this attribute to CbQueueProperty.java */
   long maxMsg_; // only used in Address

   /** The unique address, e.g. the CORBA IOR string */
   mutable string address_;

   mutable string hostname_;
   bool isHardcodedHostname_; // = false; // set to true if setHostname() was explicitly called by user

   /** The unique protocol type, e.g. "IOR" */
   string type_; //  = DEFAULT_type;
   
   /** The protocol version, e.g. "1.0" */
   string version_; // = DEFAULT_version;
   
   /** BurstMode: The time to collect messages for publish/update */
   long collectTime_; //  = DEFAULT_collectTime;
   
   /** BurstMode: The time to collect messages for oneway publish/update */
   long collectTimeOneway_; // = DEFAULT_collectTimeOneway;

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
   
   /** Compress messages if set to "gzip" or "zip" */
   string compressType_; // = DEFAULT_compressType;
   
   /** Messages bigger this size in bytes are compressed */
   long minSize_; // = DEFAULT_minSize;
   
   /** PtP messages wanted? Defaults to true, false prevents spamming */
   bool ptpAllowed_; // = DEFAULT_ptpAllowed;
   
   /** The identifier sent to the callback client, the client can decide if he trusts this invocation */
   string sessionId_; // = DEFAULT_sessionId;

   /** Shall this session callback be used for subjectQueue messages as well? For &lt;callback> only */
   bool useForSubjectQueue_; // = DEFAULT_useForSubjectQueue;

   /**
    * Does client whish a dispatcher plugin. 
    * <p>
    * Set to "undef" forces to switch off, or e.g. "Priority,1.0" to access the PriorizedDeliveryPlugin
    * </p>
    * <p>
    * Setting it to 'null' (which is the default) lets the server choose the plugin
    * </p>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/delivery.control.plugin.html">The delivery.control.plugin requirement</a>
    */
   string dispatchPlugin_; // = DEFAULT_dispatchPlugin;

   void initHostname(const string& hostname)
   {
      hostname_ = hostname;
      address_  = ""; // reset cache
   }

   void copy(const AddressBase& addr)
   {
      port_                = addr.port_;
      ME                   = addr.ME;
      rootTag_             = addr.rootTag_;
      address_             = addr.address_;
      hostname_            = addr.hostname_;
      isHardcodedHostname_ = addr.isHardcodedHostname_;
      type_                = addr.type_;
      version_             = addr.version_;
      collectTime_         = addr.collectTime_;
      collectTimeOneway_   = addr.collectTimeOneway_;
      pingInterval_        = addr.pingInterval_;
      retries_             = addr.retries_;
      delay_               = addr.delay_;
      oneway_              = addr.oneway_;
      compressType_        = addr.compressType_;
      minSize_             = addr.minSize_;
      ptpAllowed_          = addr.ptpAllowed_;
      sessionId_           = addr.sessionId_;
      useForSubjectQueue_  = addr.useForSubjectQueue_;
      dispatchPlugin_      = addr.dispatchPlugin_;
      nodeId_              = addr.nodeId_;
      maxMsg_              = addr.maxMsg_;
      defaultPingInterval_ = addr.defaultPingInterval_;
      defaultRetries_      = addr.defaultRetries_;
      defaultDelay_        = addr.defaultDelay_;
   }

   long defaultPingInterval_;
   int defaultRetries_;
   long defaultDelay_;

public:

   /**
    * common constructor
    */
   AddressBase(Global& global, const string& rootTag="");

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
   string getName();

   /**
    * Check if supplied address would connect to the address of this instance
    */
   bool isSameAddress(AddressBase& other);

   /**
    * Show some important settings for logging
    */
   string getSettings() const;

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    */
   void setType(const string& type);

   /**
    * @param version   The protocol version, e.g. "1.0"
    */
   void setVersion(const string& version);

   /**
    * Updates the internal address as well. 
    * @param host An IP or DNS
    */
   void setHostname(const string& host);

   /**
    * @return true if the hostname is explicitly set by user with setHostname()
    * false if it is determined automatically
    */
   bool isHardcodedHostname();

   /**
    * Check if a hostname is set already
    */
   bool hasHostname();

   /**
    * @return The Hostname, IP or "" if not known
    */
   string getHostname() const;

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
   void setAddress(const string& address);

   /**
    * Returns the address.
    * @return e.g. "IOR:00001100022...." or "et@universe.com" or ""
    */
   string getAddress() const;

   /**
    * Returns the protocol type.
    * @return e.g. "EMAIL" or "IOR" (never null).
    */
   string getType() const;

   /**
    * Returns the protocol version.
    * @return e.g. "1.0" or null
    */
   string getVersion() const;

   /**
    * What to do if max retries is exhausted. 
    * <p />
    * This mode is currently not configurable, we always destroy the login session. 
    * This is interpreted only server side if callback fails.
    * @return Constants.ONEXHAUST_KILL_SESSION="killSession"
    */
   string getOnExhaust() const;

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
    * BurstMode: The time span to collect oneway messages before sending. 
    * @return The time to collect in milliseconds
    */
   long getCollectTimeOneway() const;

   /**
    * BurstMode: The time to collect messages for sending in a bulk. 
    * @param The time to collect in milliseconds
    */
   void setCollectTime(long collectTime);

   /**
    * BurstMode: The time to collect oneway messages for sending in a bulk. 
    * @param The time to collect in milliseconds
    */
   void setCollectTimeOneway(long collectTimeOneway);

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
    * @param Set if we accept point to point messages
    */
   void setPtpAllowed(bool ptpAllowed);

   /**
    * @return true if we may send PtP messages
    */
   bool isPtpAllowed();

   void setCompressType(const string& compressType);

   /**
    * The identifier sent to the callback client, the client can decide if he trusts this invocation
    * @return never null
    */
   string getSecretSessionId() const;

   /** The identifier sent to the callback client, the client can decide if he trusts this invocation */
   void setSecretSessionId(const string& sessionId);

   /**
    * Get the compression method. 
    * @return "" No compression
    */
   string getCompressType() const;

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
    * Set to "undef" to switch off, or to e.g. "Priority,1.0" to access the PriorizedDeliveryPlugin
    * </p>
    * <p>
    * This overwrites the xmlBlaster.properties default setting e.g.:
    * <pre>
    * DispatchPlugin[Priority][1.0]=org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDeliveryPlugin
    * DispatchPlugin[SlowMotion][1.0]=org.xmlBlaster.util.dispatch.plugins.motion.SlowMotion
    * DispatchPlugin.defaultPlugin=Priority,1.0
    * </pre>
    * </p>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/delivery.control.plugin.html">The delivery.control.plugin requirement</a>
    */
   void setDispatchPlugin(const string& dispatchPlugin);

   /**
    * @return "undef" or e.g. "Priority,1.0"
    */
   string getDispatchPlugin() const;

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * Only none default values are dumped for performance reasons
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   string toXml(const string& extraOffset = "") const;
};

}}}}}

#endif



