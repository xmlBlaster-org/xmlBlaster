/*-----------------------------------------------------------------------------
Name:      Destination.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding destination address attributes
-----------------------------------------------------------------------------*/

#ifndef _UTIL_DESTINATION_H
#define _UTIL_DESTINATION_H
 
#include <string>
#include <util/Log.h>
#include <util/XMLString.hpp> // xerces: used to compare case insensitive str.
using namespace std;

//  package org.xmlBlaster.util;
//  import org.xmlBlaster.util.Log;
//  import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;

namespace org { namespace xmlBlaster {
namespace util {
   
   
   /**
    * Holding destination address attributes.
    * <p />
    * This class corresponds to the QOS destination tag
    */
   class Destination {

   private:
      string me() {
         return "Destination";
      }

      util::Log log_;

      /** The destination address (==login name) or the XPath query string */
      string destination_;
      /** EXACT is default */
      string queryType_;
      /** No queuing is default */
      bool forceQueuing_;

   public:
      /**
       * Constructs the specialized quality of service destination object.
       */
      Destination(const string &address="", 
                  const string &queryType="EXACT") : log_() {
         destination_   = address;
         queryType_     = queryType;
         if (queryType_ == "") queryType_ = "EXACT";
         forceQueuing_  = false;
      }

      
      /**
       * @return true/false
       */
      bool isXPathQuery() const {
         return queryType_ == "XPATH";
      }
      

      /**
       * @return true/false
       */
      bool isExactAddress() const {
         return queryType_ == "EXACT";
      }


      /**
       * @return true/false
       */
      bool forceQueuing() const {
         return forceQueuing_;
      }


      /**
       * Set queuing of messages.
       * <p />
       * true: If client is not logged in, messages will be queued until he 
       *       comes. <br />
       * false: Default is that on PtP messages when the destination address is
       *        not online, an Exception is thrown
       */
      void forceQueuing(bool forceQueuing) {
         forceQueuing_ = forceQueuing;
      }


      /**
       * Set the destination address or the destination query string.
       * @param destination The destination address or the query string
       */
      void setDestination(const string &destination) {
         destination_ = destination;
      }


      /**
       * @param The destination address or XPath query string
       */
      string getDestination() const {
         return destination_;
      }



      /**
       * Compares two strings (where name1 is a Unicode3.0 string!!) for 
       * unsensitive case compare. It returns true if the content of the
       * strings is equal (no matter what the case is). Using this method to
       * compare the strings should be portable to all platforms supported by
       * xerces.
       */
      bool caseCompare(const char *name1, const char *name2) {
         XMLCh* name1Helper = XMLString::transcode(name1);
         XMLString::upperCase(name1Helper);
         XMLCh* name2Helper = XMLString::transcode(name2);
         XMLString::upperCase(name2Helper);
         bool ret = (XMLString::compareIString(name1Helper, name2Helper) == 0);
         delete name1Helper;
         delete name2Helper;
         return ret;
      }

      /**
       * @param queryType The query type, one of "EXACT" | "XPATH"
       */
      void setQueryType(const string &queryType) {
         if (caseCompare(queryType.c_str(), "EXACT")) queryType_ = queryType;
         else 
            if (caseCompare(queryType_.c_str(), "XPATH")) {}
            else
               log_.error(me(), string("Sorry, destination queryType='")
                          + queryType_ + "' is not supported");
      }

      
      /**
       * Dump state of this object into a XML ASCII string.
       * <br>
       * @param extraOffset indenting of tags for nice output
       * @return The Destination as a XML ASCII string
       */
      string toXml(const string &extraOffset="") {
         string ret    = "\n   ";
         string offset = extraOffset;

         ret += offset + "<destination queryType='" + queryType_ + "'" +
            ">" + offset + "   " + destination_;
         if (forceQueuing())
            ret +=  offset + "   <ForceQueuing />";
         ret += offset + "</destination>";
         return ret;
      }
   };
}}} // namespace

#endif
