/*------------------------------------------------------------------------------
Name:      ServerRef.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding serverRef address std::string and protocol std::string to
           access XmlBlaster
------------------------------------------------------------------------------*/

/**
 * Helper class holding serverRef address std::string and protocol std::string.
 * <p />
 * Holds example a CORBA "IOR:00012..." std::string
 * @version $Revision: 1.8 $
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */
#ifndef _UTIL_SERVERREF_H
#define _UTIL_SERVERREF_H

#include <util/XmlBCfg.h>
#include <string>

namespace org { namespace xmlBlaster { namespace util {

class Dll_Export ServerRef
{
private:
   /** The unique address, e.g. the CORBA IOR std::string */
   std::string address_;
   /** The unique protocol type, e.g. "IOR" */
   std::string type_;

public:

    ServerRef(const ServerRef& serverRef);

    ServerRef& operator =(const ServerRef& serverRef);

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XMLRPC"
    * @param address A serverRef address for your client, suitable to the protocol
    *                for email e.g. "xmlblaster@xmlBlaster.org"
    */
   ServerRef(const std::string& type, const std::string& address="");

   /**
    * Set the serverRef address, it should fit to the protocol-type.
    *
    * @param address The serverRef address, e.g. "et@mars.univers"
    */
   void setAddress(const std::string& address);

   /**
    * Returns the address.
    * @return e.g. "IOR:00001100022...."
    */
   std::string getAddress() const;

   /**
    * Returns the protocol type.
    * @return e.g. "SOCKET" or "IOR"
    */
   std::string getType() const;

   /**
    * Dump state of this object into a XML ASCII std::string.
    */
   std::string toXml() const;

   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   std::string toXml(const std::string& extraOffset) const;
};

}}} // namespaces

#endif
