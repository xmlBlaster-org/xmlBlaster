/*------------------------------------------------------------------------------
Name:      GetReturnQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * QoS (quality of service) informations sent from server to client<br />
 * via the return value of the get() method. 
 * <p />
 * If you are a Java client you may use this class to parse the QoS argument.
 * <p />
 * Example:
 * <pre>
 *   &lt;qos> &lt;!-- GetReturnQos -->
 *     &lt;state id='OK'/>
 *     &lt;sender>Tim&lt;/sender>
 *     &lt;priority>5&lt;/priority>
 *     &lt;rcvTimestamp nanos='1007764305862000002'> &lt;!-- UTC time when message was created in xmlBlaster server with a publish() call, in nanoseconds since 1970 -->
 *           2001-12-07 23:31:45.862000002   &lt;!-- The nanos from above but human readable -->
 *     &lt;/rcvTimestamp>
 *     &lt;expiration lifeTime='1200'/> &lt;!-- The overall life time of the message [milliseconds] -->
 *     &lt;route>
 *        &lt;node id='heron'/>
 *     &lt;/route>
 *  &lt;/qos>
 * </pre>
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */


#ifndef _CLIENT_QOS_GETRETURNQOS_H
#define _CLIENT_QOS_GETRETURNQOS_H

# include <util/qos/MsgQosData.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cluster;

using namespace std;

namespace org { namespace xmlBlaster { namespace client { namespace qos {

class Dll_Export GetReturnQos
{
protected:
   string     ME;
   Global&    global_;
   Log&       log_;
   MsgQosData data_;

public:

   GetReturnQos(Global& global, const MsgQosData data);

   GetReturnQos(const GetReturnQos& data);

   GetReturnQos& operator=(const GetReturnQos& data);

   /**
    * @return true/false
    */
   bool isVolatile() const;

   /**
    * @return true/false
    */
   bool isDurable() const;

   /**
    * @return true/false
    */
   bool isReadonly() const;

   /**
    * Access sender unified naming object.
    * @return sessionName of sender or null if not known
    */
   SessionQos getSender();

   /**
    * Message priority.
    * @return priority 0-9
    * @see org.xmlBlaster.engine.helper.Constants
    */
   PriorityEnum getPriority() const;

   /**
    * This is the value delivered in the QoS (as it was calculated by the server on sending)
    * and is NOT dynamically recalculated.
    * So trust this value only if your client clock is out of date (or not trusted) and
    * if you know the message sending latency is not too big.
    * @return Milliseconds until message expiration (from now) or -1L if forever
    *         if 0L the message is expired
    */
   long getRemainingLifeStatic() const;

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the message QoS as a XML ASCII string
    */
   string toXml(const string& extraOffset="");


   /**
    * Access state of message on update().
    * @return OK (Other values are not yet supported)
    */
   string getState() const;

   /**
    * True if the message is OK on update(). 
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
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   Timestamp getRcvTimestamp() const;

    // the following where not present before ...
   RouteVector getRouteNodes();

   string getRcvTime() const;

};

}}}}

#endif
