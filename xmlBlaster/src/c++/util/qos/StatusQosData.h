/*------------------------------------------------------------------------------
Name:      StatusQosData.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Data container handling of status returned by subscribe(), unSubscribe(), erase() and ping(). 
 * <p>
 * This data holder is accessible through decorators, each of them allowing a specialized view on the data:
 * </p>
 * <ul>
 * <li>SubscribeReturnQos Returned QoS of a subscribe() invocation (Client side)</i>
 * <li>UnSubscribeReturnQos Returned QoS of a unSubscribe() invocation (Client side)</i>
 * <li>EraseReturnQos Returned QoS of an erase() invocation (Client side)</i>
 * </ul>
 * <p>
 * For the xml representation see StatusQosSaxFactory.
 * </p>
 * @see org.xmlBlaster.util.qos.StatusQosSaxFactory
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */

#ifndef _UTIL_QOS_STATUSQOSDATA_H
#define _UTIL_QOS_STATUSQOSDATA_H

#include <util/xmlBlasterDef.h>
#include <util/Constants.h>
#include <string>

//using namespace org::xmlBlaster::util; <-- VC CRASH
using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

class Dll_Export StatusQosData
{
private:
   string     ME;
   Global&    global_;

   /** the state of the message, defaults to "OK" if no state is returned */
   string state_; // = Constants::STATE_OK;
   string stateInfo_;

   /** The subscription ID of a subscribe() invocation */
   string subscriptionId_;

   /** The key oid of a publish(), helpful if the oid was generated by xmlBlaster */
   string keyOid_;

   void copy(const StatusQosData& data);

public:

   /**
    * Constructs the specialized quality of service object for status informations,
    * e.g. for a return of a subscribe() call
    * <p>
    * The state defaults to Constants::STATE_OK
    * </p>
    * @param The factory which knows how to serialize and parse me
    */
   StatusQosData(Global& global);

   StatusQosData(const StatusQosData& data);

   StatusQosData operator =(const StatusQosData& data);

   /**
    * @param state The state of an update message. See Constants::java
    */
   void setState(const string& state);

   /**
    * Access state of message on update().
    * @return "OK", "ERROR" etc. See Constants::java
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
    * True on cluster forward problems
    */
   bool isForwardError() const;

   /**
    * @param state The human readable state text of an update message
    */
   void setStateInfo(const string& stateInfo);

   /**
    * Access state of message on update().
    * @return The human readable info text
    */
   string getStateInfo() const;

   /**
    * If Pub/Sub style update: contains the subscribe ID which caused this update
    * @param subscriptionId null if PtP message
    */
   void setSubscriptionId(const string& subscriptionId);

   /**
    * If Pub/Sub style update: contains the subscribe ID which caused this update
    * @return subscribeId or null if PtP message
    */
   string getSubscriptionId() const;

   /**
    * Access key oid. 
    * @return The unique identifier of a message
    */
   string getKeyOid() const;

   /**
    * Access unique oid of a message topic. 
    */
   void setKeyOid(const string& oid);

   /**
    * The size in bytes of the data in XML form. 
    */
   int size() const;

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the status as a XML ASCII string
    */
   string toXml(const string& extraOffset="") const;

};

}}}} // namespace

#endif
