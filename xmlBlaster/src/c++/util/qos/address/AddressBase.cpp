/*------------------------------------------------------------------------------
Name:      AddressBase.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding connect address and callback address string including protocol
Version:   $Id: AddressBase.cpp,v 1.10 2003/02/18 21:24:26 laghi Exp $
------------------------------------------------------------------------------*/

/**
 * Abstract helper class holding connect address and callback address string
 * and protocol string.
 * <p />
 * See examples in the implementing classes
 * @see Address
 * @see CallbackAddress
 */

#include <util/qos/address/AddressBase.h>
#include <util/lexical_cast.h>
#include <util/Global.h>

using namespace org::xmlBlaster::util;


namespace org { namespace xmlBlaster { namespace util { namespace qos { namespace address {

const int    DEFAULT_port               = 3412;
const string DEFAULT_type               = "IOR";
const string DEFAULT_version            = "1.0";
const long   DEFAULT_collectTime        = 0;
const long   DEFAULT_collectTimeOneway  = 0;
const bool   DEFAULT_oneway             = false;
const string DEFAULT_compressType       = "";
const long   DEFAULT_minSize            = 0L;
const bool   DEFAULT_ptpAllowed         = true;
const string DEFAULT_sessionId          = "unknown";
const bool   DEFAULT_useForSubjectQueue = true;
      string DEFAULT_dispatchPlugin     = "";


AddressBase::AddressBase(Global& global, const string& rootTag)
   : global_(global), log_(global.getLog("core"))
{

   defaultPingInterval_ = 0L;
   defaultRetries_      = 0;
   defaultDelay_        = 0L;

   // set the defaults here ...
   ME                   = "AddressBase";
   nodeId_              = "";
   maxMsg_              = 0;
   address_             = "";
   hostname_            = "";
   isHardcodedHostname_ = false;
   type_                = DEFAULT_type;
   port_                = DEFAULT_port;
   version_             = DEFAULT_version;
   collectTime_         = DEFAULT_collectTime;
   collectTimeOneway_   = DEFAULT_collectTimeOneway;
   pingInterval_        = defaultPingInterval_;
   retries_             = defaultRetries_;
   delay_               = defaultDelay_;
   oneway_              = DEFAULT_oneway;
   compressType_        = DEFAULT_compressType;
   minSize_             = DEFAULT_minSize;
   ptpAllowed_          = DEFAULT_ptpAllowed;
   sessionId_           = DEFAULT_sessionId;
   useForSubjectQueue_  = DEFAULT_useForSubjectQueue;
   dispatchPlugin_      = DEFAULT_dispatchPlugin;
   setRootTag(rootTag);
}

AddressBase::AddressBase(const AddressBase& addr)
   : global_(addr.global_), log_(addr.log_)
{
   copy(addr);
}

AddressBase& AddressBase::operator =(const AddressBase& addr)
{
   copy(addr);
   return *this;
}


AddressBase::~AddressBase()
{
}

/**
 * A nice human readable name for this address (used for logging)
 */
string AddressBase::getName()
{
   return getHostname() + string(":") + lexical_cast<string>(getPort());
}

/**
 * Check if supplied address would connect to the address of this instance
 */
bool AddressBase::isSameAddress(AddressBase& other)
{
   string oa = other.getAddress();
   if ( (oa!="") && (oa == getAddress()) ) return true;
   string oh = other.getHostname();
   int op = other.getPort();
   if ( (op>0) && (op==getPort()) && (oh!="") && (oh==getHostname()))
      return true;
   return false;
}

/**
 * Show some important settings for logging
 */
string AddressBase::getSettings() const
{
   string onewayStr = "false";
   if (oneway_) onewayStr = "true";
   string ret = string("type=") + type_ + string(" oneway=") + onewayStr + string(" burstMode.collectTime=") + lexical_cast<string>(getCollectTime());
   return ret;
}

/**
 * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
 */
void AddressBase::setType(const string& type)
{
   type_ = type;
}

/**
 * @param version   The protocol version, e.g. "1.0"
 */
void AddressBase::setVersion(const string& version)
{
   version_ = version;
}

/**
 * Updates the internal address as well. 
 * @param host An IP or DNS
 */
void AddressBase::setHostname(const string& host)
{
   initHostname(host);
   isHardcodedHostname_ = true;
}

/**
 * @return true if the hostname is explicitly set by user with setHostname()
 * false if it is determined automatically
 */
bool AddressBase::isHardcodedHostname()
{
   return isHardcodedHostname_;
}

/**             
 * Check if a hostname is set already
 */
bool AddressBase::hasHostname() {
   return hostname_ != "";
}

/**
 * @return The Hostname, IP or "" if not known
 */
string AddressBase::getHostname() const
{
   if (hostname_ == "") {
      hostname_ = global_.getBootstrapHostname();
      address_  = ""; // reset cache
   }
   return hostname_;
}

/**
 * Set the bootstrapping port. 
 * Updates the internal address as well. 
 */
void AddressBase::setPort(int port)
{
   port_    = port;
   address_ = ""; // reset cache
}

int AddressBase::getPort() const
{
   return port_;
}

/**
 * Set the callback address, it should fit to the protocol-type.
 *
 * @param address The callback address, e.g. "et@mars.univers"
 */
void AddressBase::setAddress(const string& address)
{
   address_ = address;
}

/**
 * Returns the address.
 * @return e.g. "IOR:00001100022...." or "et@universe.com" or ""
 */
string AddressBase::getAddress() const
{
   if (address_ == "") {
      address_ = "http://" + getHostname();
      if (getPort() > 0)
         address_ += ":" + lexical_cast<string>(getPort());
   }
   return address_;
}

/**
 * Returns the protocol type.
 * @return e.g. "EMAIL" or "IOR" (never null).
 */
string AddressBase::getType() const
{
   return type_;
}

/**
 * Returns the protocol version.
 * @return e.g. "1.0" or null
 */
string AddressBase::getVersion() const
{
   return version_;
}

/**
 * What to do if max retries is exhausted. 
 * <p />
 * This mode is currently not configurable, we always destroy the login session. 
 * This is interpreted only server side if callback fails.
 * @return Constants.ONEXHAUST_KILL_SESSION="killSession"
 */
string AddressBase::getOnExhaust() const
{
   return Constants::ONEXHAUST_KILL_SESSION; // in future possibly Constants.ONEXHAUST_KILL_CALLBACK
}

/**
 * Kill login session if max callback retries is exhausted?
 */
bool AddressBase::getOnExhaustKillSession() const
{
   return getOnExhaust() == Constants::ONEXHAUST_KILL_SESSION;
}

/**
 * BurstMode: The time span to collect messages before sending. 
 * @return The time to collect in milliseconds
 */
long AddressBase::getCollectTime() const
{
   return collectTime_;
}

/**
 * BurstMode: The time span to collect oneway messages before sending. 
 * @return The time to collect in milliseconds
 */
long AddressBase::getCollectTimeOneway() const
{
   return collectTimeOneway_;
}

/**
 * BurstMode: The time to collect messages for sending in a bulk. 
 * @param The time to collect in milliseconds
 */
void AddressBase::setCollectTime(long collectTime)
{
   if (collectTime < 0) collectTime_ = 0;
   else collectTime_ = collectTime;
}

/**
 * BurstMode: The time to collect oneway messages for sending in a bulk. 
 * @param The time to collect in milliseconds
 */
void AddressBase::setCollectTimeOneway(long collectTimeOneway)
{
   if (collectTimeOneway < 0) collectTimeOneway_ = 0;
   else collectTimeOneway_ = collectTimeOneway;
}

/**
 * How long to wait between pings to the callback server. 
 * @return The pause time between pings in millis
 */
long AddressBase::getPingInterval() const
{
   return pingInterval_;
}

/**
 * How long to wait between pings to the callback server. 
 * @param pingInterval The pause time between pings in millis
 */
void AddressBase::setPingInterval(long pingInterval)
{
   if (pingInterval <= 0) pingInterval_ = 0;
   else if (pingInterval < 10) {
      log_.warn(ME, string("pingInterval=") + lexical_cast<string>(pingInterval) + string(" msec is too short, setting it to 10 millis"));
      pingInterval_ = 10;
   }
   else pingInterval_ = pingInterval;
}

/**
 * How often shall we retry callback attempt on callback failure
 * @return -1 forever, 0 no retry, > 0 number of retries
 */
int AddressBase::getRetries() const
{
   return retries_;
}

/**
 * How often shall we retry callback attempt on callback failure
 * @param -1 forever, 0 no retry, > 0 number of retries
 */
void AddressBase::setRetries(int retries)
{
   if (retries < -1) retries_ = -1;
   else retries_ = retries;
}

/**
 * Delay between callback retries in milliseconds, defaults to one minute
 * @return The delay in millisconds
 */
long AddressBase::getDelay() const
{
   return delay_;
}

/**
 * Delay between callback retries in milliseconds, defaults to one minute
 */
void AddressBase::setDelay(long delay)
{
   if (delay < 0) delay_ = 0;
   else delay_ = delay;
}

/**
 * Shall the publish() or callback update() message be oneway. 
 * Is only with CORBA and our native SOCKET protocol supported
 * @return true if you want to force oneway sending
 */
bool AddressBase::oneway() const
{
   return oneway_;
}

/**
 * Shall the publish() or callback update() message be oneway. 
 * Is only with CORBA and our native SOCKET protocol supported
 * @param oneway false is default
 */
void AddressBase::setOneway(bool oneway)
{
   oneway_ = oneway;
}

/**
 * @param Set if we accept point to point messages
 */
void AddressBase::setPtpAllowed(bool ptpAllowed)
{
   ptpAllowed_ = ptpAllowed;
}

/**
 * @return true if we may send PtP messages
 */
bool AddressBase::isPtpAllowed()
{
   return ptpAllowed_;
}

void AddressBase::setCompressType(const string& compressType)
{
   compressType_ = compressType;

   // TODO !!!
   if (compressType != "")
      log_.warn(ME, "Compression of messages is not yet supported");
}

/**
 * The identifier sent to the callback client, the client can decide if he trusts this invocation
 * @return never null
 */
string AddressBase::getSecretSessionId() const
{
   return sessionId_;
}

/** The identifier sent to the callback client, the client can decide if he trusts this invocation */
void AddressBase::setSecretSessionId(const string& sessionId)
{
   sessionId_ = sessionId;
}

/**
 * Get the compression method. 
 * @return "" No compression
 */
string AddressBase::getCompressType() const
{
   return compressType_;
}

/** 
 * Messages bigger this size in bytes are compressed. 
 * <br />
 * Note: This value is only used if compressType is set to a supported value
 * @return size in bytes
 */
long AddressBase::getMinSize() const
{
   return minSize_;
}

/** 
 * Messages bigger this size in bytes are compressed. 
 * <br />
 * Note: This value is only evaluated if compressType is set to a supported value
 * @return size in bytes
 */
void AddressBase::setMinSize(long minSize)
{
   minSize_ = minSize;
}

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
void AddressBase::setDispatchPlugin(const string& dispatchPlugin)
{
   dispatchPlugin_ = dispatchPlugin;
}

/**
 * @return "undef" or e.g. "Priority,1.0"
 */
string AddressBase::getDispatchPlugin() const
{
   return dispatchPlugin_;
}


/**
 * Dump state of this object into a XML ASCII string.
 * <br>
 * Only none default values are dumped for performance reasons
 * @param extraOffset indenting of tags for nice output
 * @return The xml representation
 */
string AddressBase::toXml(const string& extraOffset) const
{
   if (log_.call()) log_.call(ME, "::toXml");
   string ret;
   string offset = string("\n   ");
   offset += extraOffset;

   ret += offset + string("<") + rootTag_  + string(" type='") + getType() + string("'");
   if ( (getVersion()!="") && (getVersion()!=DEFAULT_version))
      ret += string(" version='") + getVersion() + string("'");
   if (getHostname() != "")
      ret += string(" hostname='") + getHostname() + string("'");
   if (DEFAULT_port != getPort())
       ret += string(" port='") + lexical_cast<string>(getPort()) + string("'");
   if (DEFAULT_sessionId != getSecretSessionId())
       ret += string(" sessionId='") + getSecretSessionId() + string("'");
   if (defaultPingInterval_ != getPingInterval())
       ret += string(" pingInterval='") + lexical_cast<string>(getPingInterval()) + string("'");
   if (defaultRetries_ != getRetries())
       ret += string(" retries='") + lexical_cast<string>(getRetries()) + string("'");
   if (defaultDelay_ != getDelay())
       ret += string(" delay='") + lexical_cast<string>(getDelay()) + string("'");
   if (DEFAULT_oneway != oneway()) {
       string onewayStr = "false";
       if (oneway()) onewayStr = "true";
       ret += string(" oneway='") + onewayStr + string("'");
   }
   if (DEFAULT_useForSubjectQueue != useForSubjectQueue_) {
       string useForSubjectQueueStr = "false";
       if (useForSubjectQueue_) useForSubjectQueueStr = "true";
       ret += string(" useForSubjectQueue='") + useForSubjectQueueStr + string("'");
   }
   if (DEFAULT_dispatchPlugin != dispatchPlugin_)
       ret += string(" dispatchPlugin='") + dispatchPlugin_ + string("'");
   ret += string(">");
   if (getAddress() != "")
      ret += offset + string("   ") + getAddress();
   if (getCollectTime() != DEFAULT_collectTime || getCollectTimeOneway() != DEFAULT_collectTimeOneway) {
      ret += offset + string("   ") + string("<burstMode");
      if (getCollectTime() != DEFAULT_collectTime)
         ret += string(" collectTime='") + lexical_cast<string>(getCollectTime()) + string("'");
      if (getCollectTimeOneway() != DEFAULT_collectTimeOneway)
         ret += string(" collectTimeOneway='") + lexical_cast<string>(getCollectTimeOneway()) + string("'");
      ret += string("/>");
   }
   if (getCompressType() != DEFAULT_compressType)
      ret += offset + string("   ") + string("<compress type='") + getCompressType() + string("' minSize='") + lexical_cast<string>(getMinSize()) + string("'/>");
   if (ptpAllowed_ != DEFAULT_ptpAllowed) {
      string ptpAllowedStr = "false";
      if (ptpAllowed_) ptpAllowedStr = "true";
      ret += offset + string("   ") + string("<ptp>") + ptpAllowedStr + string("</ptp>");
   }
   ret += offset + string("</") + rootTag_ + string(">");
   return ret;
}

}}}}} // namespaces


