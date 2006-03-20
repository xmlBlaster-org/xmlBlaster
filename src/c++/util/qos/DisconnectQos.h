/*------------------------------------------------------------------------------
Name:      DisconnectQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id$
------------------------------------------------------------------------------*/

/**
 * This class encapsulates the qos of a logout() or disconnect()
 * <p />
 * So you don't need to type the 'ugly' XML ASCII std::string by yourself.
 * After construction access the ASCII-XML std::string with the toXml() method.
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
#include <util/I_Log.h>
#include <string>
#include <map>

namespace org { namespace xmlBlaster { namespace util { namespace qos {

class Dll_Export DisconnectQos
{
typedef std::map<std::string, std::string> ClientPropertyMap;

private:
   std::string  ME; // = "DisconnectQos";
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log&    log_;
   bool    deleteSubjectQueue_; // = true;
   bool    clearSessions_; // = false;
   bool    clearClientQueue_; 

protected:
   ClientPropertyMap clientProperties_; 

public:
   /**
    * Default constructor
    */
   DisconnectQos(org::xmlBlaster::util::Global& global);

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

   void addClientProperty(const std::string& key, const std::string& value, const std::string& type="", const std::string& encoding="");

   const ClientPropertyMap& getClientProperties() const;

   /**
    * @param true if we shall kill all other sessions of this user on logout (defaults to false). 
    */
   void setClearSessions(bool del);

   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII std::string
    */
   std::string toXml(const std::string& extraOffset="") const;

   bool getClearClientQueue() const;
   
   void setClearClientQueue(bool clearClientQueue);
};

}}}}

#endif


