/*-----------------------------------------------------------------------------
Name:      Destination.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding destination address attributes
-----------------------------------------------------------------------------*/

#ifndef _UTIL_DESTINATION_H
#define _UTIL_DESTINATION_H
 
#include <util/xmlBlasterDef.h>
#include <util/SessionName.h>
#include <util/I_Log.h>
#include <string>

namespace org { namespace xmlBlaster { namespace util {

/**
 * Holding destination address attributes.
 * <p />
 * This class corresponds to the QOS destination tag
 */
class Dll_Export Destination
{

private:

   std::string ME;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log& log_;

   /** The destination address (==login name) or the XPath query std::string */
   org::xmlBlaster::util::SessionNameRef sessionName_;
   /** EXACT is default */
   std::string queryType_;
   /** No queuing is default */
   bool forceQueuing_;

   void copy(const Destination& dest)
   {
      SessionName *p = new SessionName(global_, dest.sessionName_->getAbsoluteName());
      SessionNameRef r(p);
      sessionName_ = r;

      queryType_    = dest.queryType_;
      forceQueuing_ = dest.forceQueuing_;
   }

public:

   /**
    * Constructs the specialized quality of service destination object.
    */
   Destination(org::xmlBlaster::util::Global& global,
               const org::xmlBlaster::util::SessionName& sessionName,
               const std::string &queryType="EXACT",
               bool forceQueuing=false);

   Destination(org::xmlBlaster::util::Global& global,
               const std::string& address="",
               const std::string &queryType="EXACT",
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

   /*
    * Set the destination address or the destination query std::string.
    * @param destination The destination address or the query std::string
   void setDestination(const org::xmlBlaster::util::SessionName& sessionName);
    */

   /**
    * @param The destination address or XPath query std::string
    */
   org::xmlBlaster::util::SessionNameRef getDestination() const;

   /**
    * @param queryType The query type, one of "EXACT" | "XPATH"
    */
   void setQueryType(const std::string &queryType);

   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return The Destination as a XML ASCII std::string
    */
   std::string toXml(const std::string &extraOffset="") const;
};

}}} // namespace

#endif
