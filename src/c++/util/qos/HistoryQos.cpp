/*------------------------------------------------------------------------------
Name:      HistoryQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/qos/HistoryQos.h>
#include <util/Global.h>
#include <util/lexical_cast.h>


using namespace std;
using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

const long DEFAULT_numEntries = 1;
const bool DEFAULT_newestFirst = true;

HistoryQos::HistoryQos(Global& global, long numOfEntries) 
   : ME("HistoryQos"), global_(global), log_(global.getLog("org.xmlBlaster.util.qos")), newestFirst_(DEFAULT_newestFirst)
{
   if (numOfEntries < 0)
        setNumEntries(global_.getProperty().getLongProperty("history.numEntries", DEFAULT_numEntries));
   else setNumEntries(numOfEntries);
}

HistoryQos::HistoryQos(const HistoryQos& qos)
   : ME(qos.ME), global_(qos.global_), log_(qos.log_)
{
   numEntries_ = qos.numEntries_;
   newestFirst_ = qos.newestFirst_;
}

HistoryQos& HistoryQos::operator =(const HistoryQos& qos)
{
   if (this == &qos)
      return *this;
   numEntries_ = qos.numEntries_;
   newestFirst_ = qos.newestFirst_;
   return *this;
}

void HistoryQos::setNumEntries(long numOfEntries)
{
   if (numOfEntries < 0) numEntries_ = -1;
   else numEntries_ = numOfEntries;
}

long HistoryQos::getNumEntries() const
{
   return numEntries_;
}

void HistoryQos::setNewestFirst(bool newestFirst)
{
   newestFirst_ = newestFirst;
}

bool HistoryQos::getNewestFirst() const
{
   return newestFirst_;
}

string HistoryQos::toXml(const string& extraOffset) const
{
   if (getNumEntries() == DEFAULT_numEntries &&
       getNewestFirst() == DEFAULT_newestFirst) {
      return "";
   }
   string ret;
   string offset = "\n " + extraOffset;
   ret += offset + "<history numEntries='" +
                   lexical_cast<std::string>(getNumEntries()) +
                   "' newestFirst='" +
                   lexical_cast<std::string>(getNewestFirst()) +
                   "'/>";
   return ret;
}

}}}} //namespace


