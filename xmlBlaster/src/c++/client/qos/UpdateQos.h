/*------------------------------------------------------------------------------
Name:      UpdateQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * QoS (quality of service) informations sent from server to client<br />
 * via the update() method from the org::xmlBlaster::client::I_Callback interface.
 * <p />
 * If you are a Java client you may use this class to parse the QoS argument.
 * <p />
 * Example:
 * <pre>
 *   &lt;qos> &lt;!-- UpdateQos -->
 *     &lt;state id='OK'/>
 *     &lt;sender>Tim&lt;/sender>
 *     &lt;priority>5&lt;/priority>
 *     &lt;subscribe id='__subId:1'/&lt;
 *     &lt;rcvTimestamp nanos='1007764305862000002'> &lt;!-- UTC time when message was created in xmlBlaster server with a publish() call, in nanoseconds since 1970 -->
 *           2001-12-07 23:31:45.862000002   &lt;!-- The nanos from above but human readable -->
 *     &lt;/rcvTimestamp>
 *     &lt;expiration lifeTime='1200'/> &lt;!-- The overall life time of the message [milliseconds] -->
 *     &lt;queue index='0' of='1'/> &lt;!-- If queued messages are flushed on login -->
 *     &lt;redeliver>4&lt;/redeliver>
 *     &lt;route>
 *        &lt;node id='heron'/>
 *     &lt;/route>
 *  &lt;/qos>
 * </pre>
 * The receive timestamp can be delivered in human readable form as well
 * by setting on server command line:
 * <pre>
 *   -cb.receiveTimestampHumanReadable true
 *
 *   &lt;rcvTimestamp nanos='1015959656372000000'>
 *     2002-03-12 20:00:56.372
 *   &lt;/rcvTimestamp>
 * </pre>
 * @see org.xmlBlaster.util.qos.MsgQosData
 * @see org.xmlBlaster.util.qos.MsgQosSaxFactory
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.update.html">update interface</a>
 */

#ifndef _CLIENT_QOS_UPDATEQOS_H
#define _CLIENT_QOS_UPDATEQOS_H

# include <client/qos/GetReturnQos.h>






namespace org { namespace xmlBlaster { namespace client { namespace qos {

class Dll_Export UpdateQos: public GetReturnQos
{

public:

   UpdateQos(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::qos::MsgQosData data);

   UpdateQos(const UpdateQos& data);

   UpdateQos& operator=(const UpdateQos& data);

   /**
    * Test if Publish/Subscribe style is used.
    *
    * @return true if Publish/Subscribe style is used
    */
   bool isSubscribable() const;

   /**
    * Test if Point to Point addressing style is used.
    *
    * @return true if addressing of the destination is used
    *         false if no destination is given
    */
   bool isPtp();

   /**
    * If Pub/Sub style update: contains the subscribe ID which caused this update
    * @return subscribeId or null if PtP message
    */
   std::string getSubscriptionId() const;

   /**
    * Returns > 0 if the message probably is redelivered. 
    * @return == 0 The message is guaranteed to be delivered only once.
    */
   int getRedeliver() const;

    /**
    * @return The number of queued messages
    */
   long getQueueSize() const;

   /**
    * @return The index of the message in the queue
    */
   long getQueueIndex() const;

   /**
    * True if the message is OK
    */
   bool isOk() const;

   /**
    * True if the message was erased by timer or by a
    * client invoking erase(). 
    */
   bool isErased() const;

   /**
    * True if a timeout on this message occurred. 
    * <p />
    * Timeouts are spanned by the publisher and thrown by xmlBlaster
    * on timeout to indicate for example
    * STALE messages or any other user problem domain specific event.
    */
   bool isTimeout() const;

   /**
    * True on cluster forward problems
    */
   bool isForwardError() const;
};

}}}}

#endif
