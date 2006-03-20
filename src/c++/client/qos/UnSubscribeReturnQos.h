/*------------------------------------------------------------------------------
Name:      UnSubscribeReturnQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Handling the returned QoS (quality of service) of a unSubscribe() call. 
 * <p />
 * If you are a Java client and use the XmlBlasterConnection helper class
 * you get this object as the unSubscribe() return value.
 * <p />
 * Example:
 * <pre>
 *   &lt;qos>
 *     &lt;state id='OK' info='QUEUED[bilbo]'/>
 *     &lt;subscribe id='_subId:1/>
 *  &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html" target="others">the interface.unSubscribe requirement</a>
 */

#ifndef _CLIENT_QOS_UNSUBSCRIBERETURNQOS_H
#define _CLIENT_QOS_UNSUBSCRIBERETURNQOS_H

#include <client/qos/SubscribeReturnQos.h>

namespace org { namespace xmlBlaster { namespace client { namespace qos {

typedef SubscribeReturnQos UnSubscribeReturnQos;

}}}} // namespace

#endif

