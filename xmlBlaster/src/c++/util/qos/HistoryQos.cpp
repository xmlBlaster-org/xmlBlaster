/*------------------------------------------------------------------------------
Name:      HistoryQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Helper class holding QoS settings to acces historical message. 
 * <p />
 * <pre>
 *   &lt;history numEntries='20'/>
 * </pre>
 * <p>
 * Default is to deliver the most current entry (numEntries='1'),
 * '-1' would deliver all history entries available.
 * </p>
 * A future version could extend the query possibilities to e.g.
 * <pre>
 *   &lt;history>
 *      &lt;time from='yesterday' to='now'>
 *   &lt;/history>
 * </pre>
 */

# include <util/qos/HistoryQos.h>
# include <util/Global.h>
# include <boost/lexical_cast.hpp>

using boost::lexical_cast;

using namespace std;
using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

Dll_Export const long DEFAULT_numEntries = 1;

   /**
    * @param glob The global handle holding environment and logging objects
    */
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





   /**
    * @param numEntries The number of history entries
    */
   void HistoryQos::setNumEntries(long numOfEntries)
   {
      if (numOfEntries < 0) numEntries_ = -1;
      else numEntries_ = numOfEntries;
   }

   /**
    * Returns the number of history entries.
    * @return e.g. 1
    */
   long HistoryQos::getNumEntries() const
   {
      return numEntries_;
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * Only none default values are dumped for performance reasons
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation or "" if all settings are default
    */
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


