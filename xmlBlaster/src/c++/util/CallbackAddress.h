/*-----------------------------------------------------------------------------
Name:      CallbackAddress.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback address string and protocol string
Version:   $Id: CallbackAddress.h,v 1.1 2000/07/06 22:55:44 laghi Exp $
-----------------------------------------------------------------------------*/

#ifndef _UTIL_CALLBACKADDRESS_H
#define _UTIL_CALLBACKADDRESS_H

#include <string>

namespace util {

   /**
    * Helper class holding callback address string and protocol string.
    */
   class CallbackAddress {

   private: 
      string address_;
      string type_;

   public:
      /**
       * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
       */
      CallbackAddress(const string &type) {
	 type_ = type;
      }


      /**
       * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
       * @param address A callback address for your client, suitable to the 
       * protocol
       */
      CallbackAddress(const string &type, const string &address) {
	 type_ = type;
	 setAddress(address);
      }


      /**
       * Set the callback address, it should fit to the protocol-type.
       * @param address The callback address, e.g. "et@mars.univers"
       */
      void setAddress(const string &address) {
	 address_ = address;
      }


      /**
       * Returns the address.
       * @return e.g. "IOR:00001100022...."
       */
      string getAddress() const {
	 return address_;
      }


      /**
       * Returns the protocol type.
       * @return e.g. "EMAIL" or "IOR"
       */
      string getType() const {
	 return type_;
      }


      /**
       * Dump state of this object into a XML ASCII string.
       */
      string toXml() const {
	 return toXml("");
      }


      /**
       * Dump state of this object into a XML ASCII string.
       * <br>
       * @param extraOffset indenting of tags for nice output
       * @return The xml representation
       */
      string toXml(const string &extraOffset) const {
	 string sb, offset = "\n   ";
	 offset += extraOffset;
	 sb  = offset + "<callback type='" + getType() + "'>";
	 sb += offset + "   " + getAddress();
	 sb += offset + "</callback>";
	 return sb;
      }
   };

}; // namespace 

#endif


