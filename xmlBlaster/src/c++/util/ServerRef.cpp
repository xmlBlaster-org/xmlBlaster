/*------------------------------------------------------------------------------
Name:      ServerRef.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding serverRef address string and protocol string to
           access XmlBlaster
Version:   $Id: ServerRef.cpp,v 1.4 2002/12/18 12:15:40 ruff Exp $
------------------------------------------------------------------------------*/

/**
 * Helper class holding serverRef address string and protocol string.
 * <p />
 * Holds example a CORBA "IOR:00012..." string
 * @version $Revision: 1.4 $
 * @author xmlBlaster@marcelruff.info
 * @author laghi@swissinfo.org
 */

#include <util/ServerRef.h>
#include <string>

using namespace std;

namespace org { namespace xmlBlaster { namespace util {

ServerRef::ServerRef(const ServerRef& serverRef)
{
   type_    = serverRef.type_;
   address_ = serverRef.address_;
}

ServerRef& ServerRef::operator =(const ServerRef& serverRef)
{
   type_    = serverRef.type_;
   address_ = serverRef.address_;
   return *this;
}


ServerRef::ServerRef(const string& type, const string& address)
{
   type_ = type;
   address_ = address;
}

void ServerRef::setAddress(const string& address)
{
   address_ = address;
}

string ServerRef::getAddress() const
{
   return address_;
}

string ServerRef::getType() const
{
   return type_;
}

string ServerRef::toXml() const
{
   return toXml("");
}

string ServerRef::toXml(const string& extraOffset) const
{
   string ret = "";
   string offset = "\n   ";
   offset += extraOffset;

   ret += offset + "<serverRef type='" + getType() + "'>";
   ret += offset + "   " + getAddress();
   ret += offset + "</serverRef>";

   return ret;
}

}}} // namespaces


#ifdef _XMLBLASTER_CLASSTEST

#include <util/ServerRef.h>
#include <string>

using org::xmlBlaster::util::ServerRef;

/** For testing: java org.xmlBlaster.engine.helper.ServerRef */
int main()
{
   try {
      ServerRef ref("IOR", "IOR:000102111000");
      std::cout << ref.toXml() << std::endl;
   }
   catch(...) {
      std::cerr << " an exception occured" << std::endl;
   }
}

#endif
