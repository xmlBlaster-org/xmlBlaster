/*------------------------------------------------------------------------------
Name:      ServerRef.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding serverRef address string and protocol string to
           access XmlBlaster
Version:   $Id: ServerRef.h,v 1.1 2002/11/29 20:35:18 laghi Exp $
------------------------------------------------------------------------------*/

/**
 * Helper class holding serverRef address string and protocol string.
 * <p />
 * Holds example a CORBA "IOR:00012..." string
 * @version $Revision: 1.1 $
 * @author ruff@swand.lake.de
 * @author laghi@swissinfo.org
 */

#ifndef _UTIL_SERVERREF_H
#define _UTIL_SERVERREF_H

#include <string>

namespace org { namespace xmlBlaster { namespace util {

class ServerRef
{
private:
   const string ME; // = "ServerRef";

   /** The unique address, e.g. the CORBA IOR string */
   string address_;
   /** The unique protocol type, e.g. "IOR" */
   string type_;

public:

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    */
   ServerRef(const string& type);

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    * @param address A serverRef address for your client, suitable to the protocol
    *                for email e.g. "xmlblaster@xmlBlaster.org"
    */
   ServerRef(const string& type, const string& address);

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