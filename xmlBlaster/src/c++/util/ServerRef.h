/*------------------------------------------------------------------------------
Name:      ServerRef.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding serverRef address string and protocol string to
           access XmlBlaster
------------------------------------------------------------------------------*/

/**
 * Helper class holding serverRef address string and protocol string.
 * <p />
 * Holds example a CORBA "IOR:00012..." string
 * @version $Revision: 1.5 $
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */

#ifndef _UTIL_SERVERREF_H
#define _UTIL_SERVERREF_H

#include <util/XmlBCfg.h>
#include <string>

using namespace std;

namespace org { namespace xmlBlaster { namespace util {

class Dll_Export ServerRef
{
private:
   /** The unique address, e.g. the CORBA IOR string */
   string address_;
   /** The unique protocol type, e.g. "IOR" */
   string type_;

public:

    ServerRef(const ServerRef& serverRef);

    ServerRef& operator =(const ServerRef& serverRef);

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    * @param address A serverRef address for your client, suitable to the protocol
    *                for email e.g. "xmlblaster@xmlBlaster.org"
    */
   ServerRef(const string& type, const string& address="");

   /**
    * Set the serverRef address, it should fit to the protocol-type.
    *
    * @param address The serverRef address, e.g. "et@mars.univers"
    */
   void setAddress(const string& address);

   /**
    * Returns the address.
    * @return e.g. "IOR:00001100022...."
    */
   string getAddress() const;

   /**
    * Returns the protocol type.
    * @return e.g. "EMAIL" or "IOR"
    */
   string getType() const;

   /**
    * Dump state of this object into a XML ASCII string.
    */
   string toXml() const;

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   string toXml(const string& extraOffset) const;
};

}}} // namespaces

#endif
