/*-----------------------------------------------------------------------------
Name:      Destination.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding destination address attributes
-----------------------------------------------------------------------------*/

#include <util/Destination.h>
#include <util/Global.h>

using namespace std;

namespace org { namespace xmlBlaster { namespace util {
   
Destination::Destination(Global& global,
                         const SessionQos& sessionQos,
                         const string &queryType,
                         bool forceQueuing)
                       : global_(global),
                         log_(global.getLog("core")),
                         sessionQos_(sessionQos)
{
   queryType_     = queryType;
   forceQueuing_  = forceQueuing;
}

Destination::Destination(Global& global,
                         const string& address,
                         const string &queryType,
                         bool forceQueuing)
                       : global_(global),
                         log_(global.getLog("core")),
                         sessionQos_(SessionQos(global, address))
{
   queryType_     = queryType;
   forceQueuing_  = forceQueuing;
}


Destination::Destination(const Destination& dest)
   : global_(dest.global_), log_(dest.log_), sessionQos_(dest.sessionQos_)
{
   copy(dest);
}

Destination& Destination::operator =(const Destination& dest)
{
   copy(dest);
   return *this;
}

bool Destination::isXPathQuery() const
{
   return queryType_ == "XPATH";
}
      
bool Destination::isExactAddress() const
{
   return queryType_ == "EXACT";
}

bool Destination::forceQueuing() const
{
   return forceQueuing_;
}

void Destination::forceQueuing(bool forceQueuing)
{
   forceQueuing_ = forceQueuing;
}

void Destination::setDestination(const SessionQos& sessionQos)
{
   sessionQos_ = sessionQos;
}

SessionQos Destination::getDestination() const
{
   return sessionQos_;
}

void Destination::setQueryType(const string &queryType)
{
   if (queryType.compare("EXACT") == 0) queryType_ = queryType;
   else
      if (queryType.compare("XPATH") == 0) {}
      else
         log_.error(ME, string("Sorry, destination queryType='")
                    + queryType_ + string("' is not supported"));
}

string Destination::toXml(const string &extraOffset) const
{
   string ret    = "\n   ";
   string offset = extraOffset;
    ret += offset + "<destination queryType='" + queryType_ + "'" +
      ">" + offset + "   " + sessionQos_.getAbsoluteName();
   if (forceQueuing())
      ret +=  offset + "   <ForceQueuing />";
   ret += offset + "</destination>";
   return ret;
}

}}} // namespace

