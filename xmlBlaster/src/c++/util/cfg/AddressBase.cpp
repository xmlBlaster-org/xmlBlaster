/*------------------------------------------------------------------------------
Name:      AddressBase.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding connect address and callback address string including protocol
Version:   $Id: AddressBase.cpp,v 1.2 2002/12/06 19:28:14 laghi Exp $
------------------------------------------------------------------------------*/

/**
 * Abstract helper class holding connect address and callback address string
 * and protocol string.
 * <p />
 * See examples in the implementing classes
 * @see Address
 * @see CallbackAddress
 */

#include <util/cfg/AddressBase.h>
#include <boost/lexical_cast.hpp>

using namespace org::xmlBlaster::util;
using boost::lexical_cast;

namespace org { namespace xmlBlaster { namespace util { namespace cfg {

Dll_Export const int       DEFAULT_port               = 3412;
Dll_Export const string    DEFAULT_type               = "IOR";
Dll_Export const string    DEFAULT_version            = "1.0";
Dll_Export const Timestamp DEFAULT_collectTime        = 0L;
Dll_Export const Timestamp DEFAULT_collectTimeOneway  = 0L;
Dll_Export const bool      DEFAULT_oneway             = false;
Dll_Export const string    DEFAULT_compressType       = "";
Dll_Export const long      DEFAULT_minSize            = 0L;
Dll_Export const bool      DEFAULT_ptpAllowed         = true;
Dll_Export const string    DEFAULT_sessionId          = "unknown";
Dll_Export const bool      DEFAULT_useForSubjectQueue = true;
Dll_Export       string    DEFAULT_dispatchPlugin     = "";


   AddressBase::AddressBase(Global& global, const string& rootTag)
      : ME("AddressBase"), global_(global), log_(global.getLog("core"))
   {
      // set the defaults here ...
      setRootTag(rootTag);
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
   string AddressBase::getHostname()
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
   string AddressBase::getAddress()
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
   Timestamp AddressBase::getCollectTime() const
   {
      return collectTime_;
   }

   /**
    * BurstMode: The time span to collect oneway messages before sending. 
    * @return The time to collect in milliseconds
    */
   Timestamp AddressBase::getCollectTimeOneway() const
   {
      return collectTimeOneway_;
   }

   /**
    * BurstMode: The time to collect messages for sending in a bulk. 
    * @param The time to collect in milliseconds
    */
   void AddressBase::setCollectTime(Timestamp collectTime)
   {
      if (collectTime < 0) collectTime_ = 0;
      else collectTime_ = collectTime;
   }

   /**
    * BurstMode: The time to collect oneway messages for sending in a bulk. 
    * @param The time to collect in milliseconds
    */
   void AddressBase::setCollectTimeOneway(Timestamp collectTimeOneway)
   {
      if (collectTimeOneway < 0) collectTimeOneway_ = 0;
      else collectTimeOneway_ = collectTimeOneway;
   }

   /**
    * How long to wait between pings to the callback server. 
    * @return The pause time between pings in millis
    */
   Timestamp AddressBase::getPingInterval() const
   {
      return pingInterval_;
   }

   /**
    * How long to wait between pings to the callback server. 
    * @param pingInterval The pause time between pings in millis
    */
   void AddressBase::setPingInterval(Timestamp pingInterval)
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
   Timestamp AddressBase::getDelay() const
   {
      return delay_;
   }

   /**
    * Delay between callback retries in milliseconds, defaults to one minute
    */
   void AddressBase::setDelay(Timestamp delay)
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
   string AddressBase::getSessionId() const
   {
      return sessionId_;
   }

   /** The identifier sent to the callback client, the client can decide if he trusts this invocation */
   void AddressBase::setSessionId(const string& sessionId)
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
   long AddressBase::getMinSize()
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
    * Called for SAX callback start tag
    */

/*
   void startElement(const string& uri, const string& localName, const string& name, const string& character, Attributes attrs)
   {
      // log.info(ME, "startElement(rootTag=" + rootTag + "): name=" + name + " character='" + character.toString() + "'");

      String tmp = character.toString().trim(); // The address
      if (tmp.length() > 0) {
         setAddress(tmp);
      }
      character.setLength(0);

      if (name.equalsIgnoreCase(rootTag)) { // "callback"
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getQName(i).equalsIgnoreCase("type") ) {
                  setType(attrs.getValue(i).trim());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("version") ) {
                  setVersion(attrs.getValue(i).trim());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("hostname") ) {
                  setHostname(attrs.getValue(i).trim());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("port") ) {
                  String ll = attrs.getValue(i).trim();
                  try {
                     setPort(new Integer(ll).intValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <" + rootTag + " port='" + ll + "'>, expected an integer number.");
                  }
               }
               else if( attrs.getQName(i).equalsIgnoreCase("sessionId") ) {
                  setSessionId(attrs.getValue(i).trim());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("pingInterval") ) {
                  String ll = attrs.getValue(i).trim();
                  try {
                     setPingInterval(new Long(ll).longValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <" + rootTag + " pingInterval='" + ll + "'>, expected a long in milliseconds.");
                  }
               }
               else if( attrs.getQName(i).equalsIgnoreCase("retries") ) {
                  String ll = attrs.getValue(i).trim();
                  try {
                     setRetries(new Integer(ll).intValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <" + rootTag + " retries='" + ll + "'>, expected an integer number.");
                  }
               }
               else if( attrs.getQName(i).equalsIgnoreCase("delay") ) {
                  String ll = attrs.getValue(i).trim();
                  try {
                     setDelay(new Long(ll).longValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <" + rootTag " delay='" + ll + "'>, expected a long in milliseconds.");
                  }
               }
               else if( attrs.getQName(i).equalsIgnoreCase("oneway") ) {
                  setOneway(new Boolean(attrs.getValue(i).trim()).booleanValue());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("useForSubjectQueue") ) {
                  this.useForSubjectQueue = new Boolean(attrs.getValue(i).trim()).booleanValue();
               }
               else if( attrs.getQName(i).equalsIgnoreCase("dispatchPlugin") ) {
                  this.dispatchPlugin = attrs.getValue(i).trim();
               }
               else {
                  log.error(ME, "Ignoring unknown attribute " + attrs.getQName(i) + " in " + rootTag + " section.");
               }
            }
         }
         if (getType() == null) {
            log.error(ME, "Missing '" + rootTag + "' attribute 'type' in QoS");
            setType("IOR");
         }
         if (getSessionId() == null) {
            log.warn(ME, "Missing '" + rootTag + "' attribute 'sessionId' QoS");
         }
         return;
      }

      if (name.equalsIgnoreCase("burstMode")) {
         if (attrs != null) {
            int len = attrs.getLength();
            int ii=0;
            for (ii = 0; ii < len; ii++) {
               if (attrs.getQName(ii).equalsIgnoreCase("collectTime")) {
                  String ll = attrs.getValue(ii).trim();
                  try {
                     setCollectTime(new Long(ll).longValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <burstMode collectTime='" + ll + "'>, expected a long in milliseconds, burst mode is switched off sync messages.");
                  }
               }
               else if (attrs.getQName(ii).equalsIgnoreCase("collectTimeOneway")) {
                  String ll = attrs.getValue(ii).trim();
                  try {
                     setCollectTimeOneway(new Long(ll).longValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <burstMode collectTimeOneway='" + ll + "'>, expected a long in milliseconds, burst mode is switched off for oneway messages.");
                  }
               }
            }
         }
         else {
            log.error(ME, "Missing 'collectTime' or 'collectTimeOneway' attribute in login-qos <burstMode>");
         }
         return;
      }

      if (name.equalsIgnoreCase("compress")) {
         if (attrs != null) {
            int len = attrs.getLength();
            for (int ii = 0; ii < len; ii++) {
               if (attrs.getQName(ii).equalsIgnoreCase("type")) {
                  setCompressType(attrs.getValue(ii).trim());
               }
               else if (attrs.getQName(ii).equalsIgnoreCase("minSize")) {
                  String ll = attrs.getValue(ii).trim();
                  try {
                     setMinSize(new Long(ll).longValue());
                  } catch (NumberFormatException e) {
                     log.error(ME, "Wrong format of <compress minSize='" + ll + "'>, expected a long in bytes, compress is switched off.");
                  }
               }
            }
         }
         else {
            log.error(ME, "Missing 'type' attribute in qos <compress>");
         }
         return;
      }

      if (name.equalsIgnoreCase("ptp")) {
         return;
      }
   }
*/

   /**
    * Handle SAX parsed end element
    */
/*
   public final void endElement(String uri, String localName, String name, StringBuffer character) {
      if (name.equalsIgnoreCase(rootTag)) { // "callback"
         String tmp = character.toString().trim(); // The address (if after inner tags)
         if (tmp.length() > 0)
            setAddress(tmp);
         else if (getAddress() == null)
            log.error(ME, rootTag + " QoS contains no address data");
      }
      else if (name.equalsIgnoreCase("burstMode")) {
      }
      else if (name.equalsIgnoreCase("compress")) {
      }
      else if (name.equalsIgnoreCase("ptp")) {
         this.ptpAllowed = new Boolean(character.toString().trim()).booleanValue();
      }

      character.setLength(0);
   }
*/

   /**
    * Dump state of this object into a XML ASCII string.
    */
   string AddressBase::toXml()
   {
      return toXml("");
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * Only none default values are dumped for performance reasons
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   string AddressBase::toXml(const string& extraOffset)
   {
      if (log_.CALL) log_.call(ME, "::toXml");
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
      if (DEFAULT_sessionId != getSessionId())
          ret += string(" sessionId='") + getSessionId() + string("'");
      if (getDefaultPingInterval() != getPingInterval())
          ret += string(" pingInterval='") + lexical_cast<string>(getPingInterval()) + string("'");
      if (getDefaultRetries() != getRetries())
          ret += string(" retries='") + lexical_cast<string>(getRetries()) + string("'");
      if (getDefaultDelay() != getDelay())
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

}}}} // namespaces


