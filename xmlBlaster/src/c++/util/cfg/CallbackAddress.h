/*------------------------------------------------------------------------------
Name:      CallbackAddress.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback address string and protocol string
Version:   $Id: CallbackAddress.h,v 1.1 2002/12/06 21:13:32 laghi Exp $
------------------------------------------------------------------------------*/

/**
 * Helper class holding callback address string and protocol string.
 * <p />
 * <pre>
 * &lt;callback type='XML-RPC' sessionId='4e56890ghdFzj0'
 *           pingInterval='60000' retries='5' delay='10000'
 *           oneway='false' useForSubjectQueue='true'
 *           dispatchPlugin='Priority,1.0'>
 *    http://server:8080/cb
 *    &lt;compress type='gzip' minSize='1000'/>
 *    &lt;burstMode collectTime='400'/>
 * &lt;/callback>
 * </pre>
 */

#ifndef _UTIL_CFG_CALLBACKADDRESS_H
#define _UTIL_CFG_CALLBACKADDRESS_H


#include <util/xmlBlasterDef.h>
#include <util/cfg/AddressBase.h>
#include <string>

namespace org { namespace xmlBlaster { namespace util { namespace cfg {

using namespace org::xmlBlaster::util;

class CallbackAddress : public AddressBase
{
private:
   const string ME; // = "CallbackAddress";
   string nodeId_; //  = null;

   /**
    * Configure property settings
    */
   void initialize()
   {
      initHostname(global_.getCbHostname()); // don't use setHostname() as it would set isCardcodedHostname=true
      setPort(global_.getProperty().getIntProperty("cb.port", getPort()));
      setType(global_.getProperty().getStringProperty("cb.protocol", getType()));
      setCollectTime(global_.getProperty().getTimestampProperty("cb.burstMode.collectTime", DEFAULT_collectTime)); // sync update()
      setCollectTimeOneway(global_.getProperty().getTimestampProperty("cb.burstMode.collectTimeOneway", DEFAULT_collectTimeOneway)); // oneway update()
      setPingInterval(global_.getProperty().getTimestampProperty("cb.pingInterval", getDefaultPingInterval()));
      setRetries(global_.getProperty().getIntProperty("cb.retries", getDefaultRetries()));
      setDelay(global_.getProperty().getTimestampProperty("cb.delay", getDefaultDelay()));
      useForSubjectQueue(global_.getProperty().getBoolProperty("cb.useForSubjectQueue", DEFAULT_useForSubjectQueue));
      setOneway(global_.getProperty().getBoolProperty("cb.oneway", DEFAULT_oneway));
      setCompressType(global_.getProperty().getStringProperty("cb.compress.type", DEFAULT_compressType));
      setMinSize(global_.getProperty().getLongProperty("cb.compress.minSize", DEFAULT_minSize));
      setPtpAllowed(global_.getProperty().getBoolProperty("cb.ptpAllowed", DEFAULT_ptpAllowed));
      setSessionId(global_.getProperty().getStringProperty("cb.sessionId", DEFAULT_sessionId));
      setDispatchPlugin(global_.getProperty().getStringProperty("cb.DispatchPlugin.defaultPlugin", DEFAULT_dispatchPlugin));
      if (nodeId_ != "") {
         setPort(global_.getProperty().getIntProperty("cb.port["+nodeId_+"]", getPort()));
         setType(global_.getProperty().getStringProperty("cb.protocol["+nodeId_+"]", getType()));
         setCollectTime(global_.getProperty().getTimestampProperty("cb.burstMode.collectTime["+nodeId_+"]", collectTime_));
         setCollectTimeOneway(global_.getProperty().getTimestampProperty("cb.burstMode.collectTimeOneway["+nodeId_+"]", collectTimeOneway_));
         setPingInterval(global_.getProperty().getTimestampProperty("cb.pingInterval["+nodeId_+"]", pingInterval_));
         setRetries(global_.getProperty().getIntProperty("cb.retries["+nodeId_+"]", retries_));
         setDelay(global_.getProperty().getTimestampProperty("cb.delay["+nodeId_+"]", delay_));
         useForSubjectQueue(global_.getProperty().getBoolProperty("cb.useForSubjectQueue["+nodeId_+"]", useForSubjectQueue_));
         setOneway(global_.getProperty().getBoolProperty("cb.oneway["+nodeId_+"]", oneway_));
         setCompressType(global_.getProperty().getStringProperty("cb.compress.type["+nodeId_+"]", compressType_));
         setMinSize(global_.getProperty().getLongProperty("cb.compress.minSize["+nodeId_+"]", minSize_));
         setPtpAllowed(global_.getProperty().getBoolProperty("cb.ptpAllowed["+nodeId_+"]", ptpAllowed_));
         setSessionId(global_.getProperty().getStringProperty("cb.sessionId["+nodeId_+"]", sessionId_));
         setDispatchPlugin(global_.getProperty().getStringProperty("cb.DispatchPlugin.defaultPlugin["+nodeId_+"]", dispatchPlugin_));
      }
   }

public:

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    * @param nodeId  A unique string (typically the cluster node id we connect to).<br />
    *   This is used for extended env-variable support, e.g. for a given
    *    <code>nodeId="heron"</ code>
    *   the command line argument (or xmlBlaster.property entry)
    *    <code>-cb.retries[heron] 20</code>
    *   is precedence over
    *    <code>-cb.retries 10</code>
    */
   CallbackAddress(Global& global, const string& type="", const string nodeId="");

   /** How often to retry if connection fails: defaults to 0 retries, on failure we give up */
   int getDefaultRetries();

   /** Delay between connection retries in milliseconds: defaults to one minute */
   Timestamp getDefaultDelay();

   /** Ping interval: pinging every given milliseconds, defaults to one minute */
   Timestamp getDefaultPingInterval();

   /**
    * Shall this address be used for subject queue messages?
    * @return false if address is for session queue only
    */
   bool useForSubjectQueue();

   /**
    * Shall this address be used for subject queue messages?
    * @param useForSubjectQueue false if address is for session queue only
    */
   void useForSubjectQueue(bool useForSubjectQueue);

   /** @return The literal address as given by getAddress() */
   string toString();

   /**
    * Get a usage string for the server side supported callback connection parameters
    */
   string usage();
};

}}}} // namespace

#endif
