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

#include <util/parser/XmlHandlerBase.h>
#include <util/Destination.h>
#include <util/qos/MsgQosData.h>
#include <util/SessionName.h>
#include <util/cluster/RouteInfo.h>
#include <string>
#include <util/qos/storage/QueuePropertyFactory.h>



namespace org { namespace xmlBlaster { namespace util { namespace qos {

class Dll_Export MsgQosFactory : public parser::XmlHandlerBase
{
private:
   std::string          ME;
   MsgQosData*          msgQosDataP_;
   Destination          destination_;
   org::xmlBlaster::util::cluster::RouteInfo routeInfo_;
   org::xmlBlaster::util::qos::storage::QueuePropertyFactory queuePropertyFactory_;

   /** helper flag for SAX parsing: parsing inside <state> ? */
   bool inState_; // QosData
   bool inSubscribe_;
   bool inRedeliver_;
   bool inTopic_;
   bool inQueue_;
   bool inPersistence_;
   bool inDestination_;
   bool inSender_;
   bool inPriority_;
   bool inClientProperty_;
   bool inExpiration_;
   bool inRcvTimestamp_; // QosData
   bool inIsVolatile_;
   bool inIsPersistent_; // QosData
   bool inReadonly_;
   bool inRoute_;  // QosData
   bool sendRemainingLife_;
   bool inQos_;

   std::string LIFE_TIME;
   std::string FORCE_DESTROY;
   std::string REMAINING_LIFE;
   std::string READ_ONLY;
   std::string DESTROY_DELAY;
   std::string CREATE_DOM_ENTRY;
   std::string NANOS;
   std::string ID;
   std::string STRATUM;
   std::string TIMESTAMP;
   std::string DIRTY_READ;
   std::string INDEX;
   std::string SIZE;

   ClientProperty* clientProperty_;

   // Private copy ctor and assignement
   MsgQosFactory(const MsgQosFactory& data);
   MsgQosFactory& operator=(const MsgQosFactory& data);

public:
   /**
    * Can be used as singleton. 
    */
   MsgQosFactory(org::xmlBlaster::util::Global& global);

   ~MsgQosFactory();

   /**
    * Parses the given xml Qos and returns a MsgQosData holding the data. 
    * Parsing of update() and publish() QoS is supported here.
    * @param the XML based ASCII std::string
    */
   MsgQosData readObject(const std::string& xmlQos);

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   void startElement(const std::string &name, const parser::AttributeMap& attrs);

   /**
    * Characters.
    * The text between two tags, in the following example 'Hello':
    * <key>Hello</key>. This method is different from the java version
    * since the c++ parser always starts at the first character, so you
    * don't specify start.
    */
   void characters(const std::string &ch);

   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   void endElement(const std::string &name);

   /** Configure if remaingLife is sent in Qos (redesign approach to work with all QoS attributes */
   void sendRemainingLife(bool sendRemainingLife);
   
   bool sendRemainingLife();

};

}}}}

#endif

