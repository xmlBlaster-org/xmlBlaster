/*------------------------------------------------------------------------------
Name:      DisconnectQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: DisconnectQos.h,v 1.4 2003/01/16 14:20:55 johnson Exp $
------------------------------------------------------------------------------*/

/**
 * This class encapsulates the qos of a logout() or disconnect()
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>logout</b> qos could look like this:<br />
 * <pre>
 *  &lt;qos>
 *    &lt;deleteSubjectQueue>true&lt;/deleteSubjectQueue>
 *    &lt;clearSessions>false&lt;/clearSessions>
 *  &lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.disconnect.html">The interface.disconnect requirement</a>
 * @see org.xmlBlaster.test.classtest.DisconnectQosTest
 */

#ifndef _UTIL_QOS_DISCONNECTQOS_H
#define _UTIL_QOS_DISCONNECTQOS_H

#include <util/xmlBlasterDef.h>
#include <util/Log.h>
#include <string>

using namespace std;
// using namespace org::xmlBlaster::util; <-- VC CRASH

namespace org { namespace xmlBlaster { namespace util { namespace qos {


class DisconnectQos
{
private:
   string  ME; // = "DisconnectQos";
   Global& global_;
   Log&    log_;
   bool    deleteSubjectQueue_; // = true;
   bool    clearSessions_; // = false;

public:
   /**
    * Default constructor
    */
   DisconnectQos(Global& global);

   /**
    * copy constructor
    */
   DisconnectQos(const DisconnectQos& qos);

   /**
    * assignment operator
    */
   DisconnectQos& operator =(const DisconnectQos& qos);

   /**
    * Return true if subject queue shall be deleted with last user session
    * @return true;
    */
   bool getDeleteSubjectQueue() const;

   /**
    * @param true if subject queue shall be deleted with last user session logout
    */
   void setSubjectQueue(bool del);

   /**
    * Return true if we shall kill all other sessions of this user on logout (defaults to false). 
    * @return false
    */
   bool getClearSessions() const;

   /**
    * @param true if we shall kill all other sessions of this user on logout (defaults to false). 
    */
   void setClearSessions(bool del);

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   string toXml(const string& extraOffset="") const;

};

}}}}

#endif


