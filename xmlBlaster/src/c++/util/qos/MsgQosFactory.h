/*------------------------------------------------------------------------------
Name:      MsgQosSaxFactory.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Parsing xml QoS (quality of service) of publish() and update(). 
 * <p>
 * See REQ interface.publish for a XML markup description
 * </p>
 * @see org.xmlBlaster.test.classtest.qos.MsgQosFactoryTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
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

class Dll_Export MsgQosFactory : public SaxHandlerBase
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
   bool inIsPersistent_; // false;
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

