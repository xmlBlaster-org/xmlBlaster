/*------------------------------------------------------------------------------
Name:      Address.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding address string and protocol string
Version:   $Id: Address.h,v 1.1 2002/12/06 19:28:14 laghi Exp $
------------------------------------------------------------------------------*/

/**
 * Helper class holding address string, protocol string and client side connection properties.
 * <p />
 * <pre>
 * &lt;address type='XML-RPC' sessionId='4e56890ghdFzj0'
 *           pingInterval='60000' retries='5' delay='10000'
 *           oneway='false'>
 *    http://server:8080/cb
 *    &lt;compress type='gzip' minSize='1000'/>
 *    &lt;burstMode collectTime='400'/> <!-- for publishOneway() calls -->
 * &lt;/address>
 * </pre>
 */

#ifndef _UTIL_CFG_ADDRESS_H
#define _UTIL_CFG_ADDRESS_H

#include <util/cfg/AddressBase.h>

namespace org { namespace xmlBlaster { namespace util { namespace cfg {

class Address : public AddressBase
{
private:
     const string ME; // = "Address";

   /** The node id to which we want to connect */
   string nodeId_; //  = null;

   /** TODO: Move this attribute to CbQueueProperty.java */
   long maxMsg_;

   /**
    * Configure property settings. 
    * "-delay[heron] 20" has precedence over "-delay 10"
    * @see #Address(String, String)
    */
   void initialize()
   {
      setPort(global_.getProperty().getIntProperty("port", getPort()));
      setPort(global_.getProperty().getIntProperty("client.port", getPort())); // this is stronger (do we need it?)

      setType(global_.getProperty().getStringProperty("client.protocol", getType()));
      setCollectTime(global_.getProperty().getTimestampProperty("burstMode.collectTime", DEFAULT_collectTime));
      setCollectTimeOneway(global_.getProperty().getTimestampProperty("burstMode.collectTimeOneway", DEFAULT_collectTimeOneway));
      setPingInterval(global_.getProperty().getTimestampProperty("pingInterval", getDefaultPingInterval()));
      setRetries(global_.getProperty().getIntProperty("retries", getDefaultRetries()));
      setDelay(global_.getProperty().getTimestampProperty("delay", getDefaultDelay()));
      setOneway(global_.getProperty().getBoolProperty("oneway", DEFAULT_oneway));
      setCompressType(global_.getProperty().getStringProperty("compress.type", DEFAULT_compressType));
      setMinSize(global_.getProperty().getLongProperty("compress.minSize", DEFAULT_minSize));
      setPtpAllowed(global_.getProperty().getBoolProperty("ptpAllowed", DEFAULT_ptpAllowed));
      setSessionId(global_.getProperty().getStringProperty("sessionId", DEFAULT_sessionId));
      setDispatchPlugin(global_.getProperty().getStringProperty("DispatchPlugin.defaultPlugin", DEFAULT_dispatchPlugin));
      if (nodeId_ != "") {
         setPort(global_.getProperty().getIntProperty("port["+nodeId_+"]", getPort()));
         setPort(global_.getProperty().getIntProperty("client.port["+nodeId_+"]", getPort())); // this is stronger (do we need it?)

         setType(global_.getProperty().getStringProperty("client.protocol["+nodeId_+"]", getType()));
         setCollectTime(global_.getProperty().getTimestampProperty("burstMode.collectTime["+nodeId_+"]", getCollectTime()));
         setCollectTimeOneway(global_.getProperty().getTimestampProperty("burstMode.collectTimeOneway["+nodeId_+"]", getCollectTimeOneway()));
         setPingInterval(global_.getProperty().getTimestampProperty("pingInterval["+nodeId_+"]", getPingInterval()));
         setRetries(global_.getProperty().getIntProperty("retries["+nodeId_+"]", getRetries()));
         setDelay(global_.getProperty().getTimestampProperty("delay["+nodeId_+"]", getDelay()));
         setOneway(global_.getProperty().getBoolProperty("oneway["+nodeId_+"]", oneway()));
         setCompressType(global_.getProperty().getStringProperty("compress.type["+nodeId_+"]", getCompressType()));
         setMinSize(global_.getProperty().getLongProperty("compress.minSize["+nodeId_+"]", getMinSize()));
         setPtpAllowed(global_.getProperty().getBoolProperty("ptpAllowed["+nodeId_+"]", isPtpAllowed()));
         setSessionId(global_.getProperty().getStringProperty("sessionId["+nodeId_+"]", getSessionId()));
         setDispatchPlugin(global_.getProperty().getStringProperty("DispatchPlugin.defaultPlugin["+nodeId_+"]", dispatchPlugin_));
      }

      // TODO: This is handled in QueueProperty.java already ->
//      long maxMsg = global_.getProperty().getLongProperty("queue.maxMsg", CbQueueProperty.DEFAULT_maxMsgDefault);
      long maxMsg = global_.getProperty().getLongProperty("queue.maxMsg", 10000l);
      setMaxMsg(maxMsg);
      if (nodeId_ != "") {
         setMaxMsg(global_.getProperty().getLongProperty("queue.maxMsg["+nodeId_+"]", getMaxMsg()));
      }
   }

public:

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    * @param nodeId  A unique string (typically the cluster node id we connect to).<br />
    *   This is used for extended env-variable support, e.g. for a given
    *    <code>nodeId="heron"</ code>
    *   the command line argument (or xmlBlaster.property entry)
    *    <code>-retries[heron] 20</code>
    *   is precedence over
    *    <code>-retries 10</code>
    */
   Address(Global& global, const string& type="", const string& nodeId="");

   void setMaxMsg(long maxMsg);

   long getMaxMsg() const;

   /** How often to retry if connection fails: defaults to -1 (retry forever) */
   int getDefaultRetries();

   /** Delay between connection retries in milliseconds (5000 is a good value): defaults to 0, a value bigger 0 switches fails save mode on */
   Timestamp getDefaultDelay();

   // /* Delay between connection retries in milliseconds: defaults to 5000 (5 sec), a value of 0 switches fails save mode off */
   // public long getDefaultDelay() { return 5 * 1000L; };

   /** Ping interval: pinging every given milliseconds, defaults to 10 seconds */
   Timestamp getDefaultPingInterval();

   /** For logging only */
   string getSettings();

   /** @return The literal address as given by getAddress() */
   string toString();

   /**
    * Get a usage string for the connection parameters
    */
   string usage();

};

}}}} // namespace

#endif
