/*------------------------------------------------------------------------------
Name:      MsgQosSaxFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Parsing xml QoS (quality of service) of publish() and update(). 
 * <p />
 * Example for Pub/Sub style:<p />
 * <pre>
 *  &lt;qos> &lt;
 *     &lt;state id='OK' info='Keep on running"/> <!-- Only for updates and PtP -->
 *     &lt;sender>Tim&lt;/sender>
 *     &lt;priority>5&lt;/priority>
 *     &lt;subscribe id='__subId:1'/> <!-- Only for updates -->
 *     &lt;rcvTimestamp nanos='1007764305862000002'> &lt;!-- UTC time when message was created in xmlBlaster server with a publish() call, in nanoseconds since 1970 -->
 *           2001-12-07 23:31:45.862000002   &lt;!-- The nanos from above but human readable -->
 *     &lt;/rcvTimestamp>
 *     &lt;expiration lifeTime='129595811' forceDestroy='false'/> <!-- Only for persistence layer -->
 *     &lt;queue index='0' of='1'/> &lt;!-- If queued messages are flushed on login -->
 *     &lt;isDurable/>
 *     &lt;redeliver>4&lt;/redeliver>             <!-- Only for updates -->
 *     &lt;route>
 *        &lt;node id='heron'/>
 *     &lt;/route>
 *     &lt;topic readonly='false' destroyDelay='60000' createDomEntry='true'>
 *        &lt;queue relating='topic' type='CACHE' version='1.0' maxMsg='1000' maxBytes='4000000' onOverflow='deadMessage'/>
 *        &lt;queue relating='history' type='CACHE' version='1.0' maxMsg='1000' maxBytes='4000000' onOverflow='exception'/>
 *     &lt;/topic>
 *  &lt;/qos>
 * </pre>
 * Example for PtP addressing style:&lt;p />
 * <pre>
 *  &lt;qos>
 *     &lt;subscribeable>false&lt;/subscribeable>  &lt;!-- false to make PtP message invisible for subscribes -->
 *     &lt;destination queryType='EXACT' forceQueuing='true'>
 *        Tim
 *     &lt;/destination>
 *     &lt;destination queryType='EXACT'>
 *        /node/heron/client/Ben
 *     &lt;/destination>
 *     &lt;destination queryType='XPATH'>   <!-- Not supported yet -->
 *        //[GROUP='Manager']
 *     &lt;/destination>
 *     &lt;destination queryType='XPATH'>   <!-- Not supported yet -->
 *        //ROLE/[@id='Developer']
 *     &lt;/destination>
 *     &lt;sender>
 *        Gesa
 *     &lt;/sender>
 *     &lt;priority>7&lt;/priority>
 *     &lt;route>
 *        &lt;node id='bilbo' stratum='2' timestamp='34460239640' dirtyRead='true'/>
 *     &lt;/route>
 *  &lt;/qos>
 * </pre>
 * <p>
 * Note that receiveTimestamp is in nanoseconds, whereas all other time values are milliseconds
 * </p>
 * The receive timestamp can be delivered in human readable form as well
 * by setting on server command line:
 * <pre>
 *   -cb.receiveTimestampHumanReadable true
 *
 *   &lt;rcvTimestamp nanos='1015959656372000000'>
 *     2002-03-12 20:00:56.372
 *   &lt;/rcvTimestamp>
 * </pre>
 * @see org.xmlBlaster.test.classtest.qos.MsgQosFactoryTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.publish.destination.PtP.html">The engine.qos.publish.destination.PtP requirement</a>
 * @author xmlBlaster@marcelruff.info
 */

#ifndef _UTIL_QOS_MSGQOSFACTORY_H
#define _UTIL_QOS_MSGQOSFACTORY_H

#include <util/SaxHandlerBase.h>
#include <util/Log.h>
#include <util/Destination.h>
#include <util/qos/MsgQosData.h>
#include <util/qos/SessionQos.h>
#include <util/cluster/RouteInfo.h>
#include <string>
#include <util/qos/storage/QueuePropertyFactory.h>

using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::cluster;
using namespace org::xmlBlaster::util::qos::storage;
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

class MsgQosFactory : public SaxHandlerBase
{
private:
   string               ME;
   MsgQosData           msgQosData_;
   Destination          destination_;
   RouteInfo            routeInfo_;
   QueuePropertyFactory queuePropertyFactory_;

   /** helper flag for SAX parsing: parsing inside <state> ? */
   bool inState_; // = false;
   bool inSubscribe_; // = false;
   bool inRedeliver_; // = false;
   bool inTopic_; // false;
   bool inQueue_; // =  false;
   bool inPersistence_; // false;
   bool inDestination_; // false;
   bool inSender_; // false;
   bool inPriority_; // false;
   bool inExpiration_; // false;
   bool inRcvTimestamp_;//  false;
   bool inIsVolatile_; // false;
   bool inIsDurable_; // false;
   bool inReadonly_; // false;
   bool inRoute_; // false;
   bool sendRemainingLife_; //  true;
   bool inQos_;

   XMLCh* LIFE_TIME;
   XMLCh* FORCE_DESTROY;
   XMLCh* REMAINING_LIFE;
   XMLCh* READ_ONLY;
   XMLCh* DESTROY_DELAY;
   XMLCh* CREATE_DOM_ENTRY;
   XMLCh* NANOS;
   XMLCh* ID;
   XMLCh* STRATUM;
   XMLCh* TIMESTAMP;
   XMLCh* DIRTY_READ;
   XMLCh* INDEX;
   XMLCh* SIZE;

public:
   /**
    * Can be used as singleton. 
    */
   MsgQosFactory(Global& global);

   ~MsgQosFactory();

   /**
    * Parses the given xml Qos and returns a MsgQosData holding the data. 
    * Parsing of update() and publish() QoS is supported here.
    * @param the XML based ASCII string
    */
   MsgQosData readObject(const string& xmlQos);

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   void startElement(const XMLCh* const name, AttributeList& attrs);

   /**
    * Characters.
    * The text between two tags, in the following example 'Hello':
    * <key>Hello</key>. This method is different from the java version
    * since the c++ parser always starts at the first character, so you
    * don't specify start.
    */
   void characters(const XMLCh* const ch, const unsigned int length);

   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   void endElement(const XMLCh* const name);

   /** Configure if remaingLife is sent in Qos (redesign approach to work with all QoS attributes */
   void sendRemainingLife(bool sendRemainingLife);
   
   bool sendRemainingLife();

};

}}}}

#endif

