/*-----------------------------------------------------------------------------
Name:      Destination.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding destination address attributes
-----------------------------------------------------------------------------*/

#ifndef _UTIL_DESTINATION_H
#define _UTIL_DESTINATION_H
 
#include <util/xmlBlasterDef.h>
#include <string>
#include <util/Log.h>
#include <util/XMLString.hpp> // xerces: used to compare case insensitive str.
#include <util/qos/SessionQos.h>

using namespace std;

namespace org { namespace xmlBlaster { namespace util {

using namespace qos;
   
/**
 * Holding destination address attributes.
 * <p />
 * This class corresponds to the QOS destination tag
 */
class Dll_Export Destination
{

private:

   string ME;
   Global& global_;
   Log&    log_;

   /** The destination address (==login name) or the XPath query string */
   SessionQos sessionQos_;
   /** EXACT is default */
   string queryType_;
   /** No queuing is default */
   bool forceQueuing_;

   void copy(const Destination& dest)
   {
      queryType_    = dest.queryType_;
      forceQueuing_ = dest.forceQueuing_;
   }

public:

   /**
    * Constructs the specialized quality of service destination object.
    */
   Destination(Global& global,
               const SessionQos& sessionQos,
               const string &queryType="EXACT",
               bool forceQueuing=false);

   Destination(Global& global,
               const string& address="",
               const string &queryType="EXACT",
               bool forceQueuing=false);

    Destination(const Destination& dest);

    Destination& operator =(const Destination& dest);

   /**
    * @return true/false
    */
   bool isXPathQuery() const;

   /**
    * @return true/false
    */
   bool isExactAddress() const;

   /**
    * @return true/false
    */
   bool forceQueuing() const;

   /**
    * Set queuing of messages.
    * <p />
    * true: If client is not logged in, messages will be queued until he
    *       comes. <br />
    * false: Default is that on PtP messages when the destination address is
    *        not online, an Exception is thrown
    */
   void forceQueuing(bool forceQueuing);

   /**
    * Set the destination address or the destination query string.
    * @param destination The destination address or the query string
    */
   void setDestination(const SessionQos& sessionQos);

   /**
    * @param The destination address or XPath query string
    */
   SessionQos getDestination() const;

   /**
    * Compares two strings (where name1 is a Unicode3.0 string!!) for
    * unsensitive case compare. It returns true if the content of the
    * strings is equal (no matter what the case is). Using this method to
    * compare the strings should be portable to all platforms supported by
    * xerces.
    */
   bool caseCompare(const char *name1, const char *name2);

   /**
    * @param queryType The query type, one of "EXACT" | "XPATH"
    */
   void setQueryType(const string &queryType);

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return The Destination as a XML ASCII string
    */
   string toXml(const string &extraOffset="");
};

}}} // namespace

#endif
