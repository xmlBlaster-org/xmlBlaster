/*------------------------------------------------------------------------------
Name:      EraseReturnQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Handling the returned QoS (quality of service) of a erase() call. 
 * <p />
 * If you are a Java client and use the XmlBlasterConnection helper class
 * you get this object as the erase() return value.
 * <p />
 * Example:
 * <pre>
 *   &lt;qos>
 *     &lt;state id='OK' info='QUEUED[bilbo]'/>
 *     &lt;key oid='HelloWorld/>
 *  &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.test.classtest.qos.StatusQosFactoryTest
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.erase.html" target="others">the interface.erase requirement</a>
 */


#ifndef _CLIENT_QOS_ERASERETURNQOS_H
#define _CLIENT_QOS_ERASERETURNQOS_H

#include <client/qos/PublishReturnQos.h>

namespace org { namespace xmlBlaster { namespace client { namespace qos {

typedef PublishReturnQos EraseReturnQos;

}}}} // namespace

#endif

