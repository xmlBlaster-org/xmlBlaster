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
                         const SessionName& sessionName,
                         const string &queryType,
                         bool forceQueuing)
                       : global_(global),
                         log_(global.getLog("org.xmlBlaster.util")),
                         sessionName_(0)
{
   SessionName *p = new SessionName(global_, sessionName.getAbsoluteName());
   SessionNameRef r(p);
   sessionName_ = r;

   queryType_     = queryType;
   forceQueuing_  = forceQueuing;
}

Destination::Destination(Global& global,
                         const string& address,
                         const string &queryType,
                         bool forceQueuing)
                       : global_(global),
                         log_(global.getLog("org.xmlBlaster.util")),
                         sessionName_(0)
{
   SessionName *p = new SessionName(global_, address);
   SessionNameRef r(p);
   sessionName_ = r;

   queryType_     = queryType;
   forceQueuing_  = forceQueuing;
}


Destination::Destination(const Destination& dest)
   : global_(dest.global_), log_(dest.log_), sessionName_(0)
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

/*
void Destination::setDestination(const SessionName& sessionName)
{
   sessionName_ = sessionName;
}
*/

SessionNameRef Destination::getDestination() const
{
   return sessionName_;
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
   string offset = Constants::OFFSET + extraOffset;
   string ret;
   ret += offset + "<destination queryType='" + queryType_ + "'";
   ret += " forceQueuing='" + lexical_cast<string>(forceQueuing()) + "'";
   ret +=  ">" + sessionName_->getAbsoluteName() + "</destination>";
   return ret;
}

}}} // namespace

