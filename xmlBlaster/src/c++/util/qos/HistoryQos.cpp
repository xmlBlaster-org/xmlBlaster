/*------------------------------------------------------------------------------
Name:      HistoryQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

#include <util/qos/HistoryQos.h>
#include <util/Global.h>
#include <boost/lexical_cast.hpp>

using namespace boost;
using namespace std;
using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

Dll_Export const long DEFAULT_numEntries = 1;

HistoryQos::HistoryQos(Global& global, long numOfEntries) 
   : ME("HistoryQos"), global_(global), log_(global.getLog("core"))
{
   if (numOfEntries < 0)
        setNumEntries(global_.getProperty().getLongProperty("history.numEntries", DEFAULT_numEntries));
   else setNumEntries(numOfEntries);
}

HistoryQos::HistoryQos(const HistoryQos& qos)
   : ME(qos.ME), global_(qos.global_), log_(qos.log_)
{
   numEntries_ = qos.numEntries_;
}

HistoryQos& HistoryQos::operator =(const HistoryQos& qos)
{
   numEntries_ = qos.numEntries_;
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

string HistoryQos::toXml(const string& extraOffset) const
{
   if (getNumEntries() == DEFAULT_numEntries) {
      return "";
   }
   string ret;
   string offset = "\n " + extraOffset;
   ret += offset + "<history numEntries='" + lexical_cast<string>(getNumEntries()) + "'/>";
   return ret;
}

}}}} //namespace


