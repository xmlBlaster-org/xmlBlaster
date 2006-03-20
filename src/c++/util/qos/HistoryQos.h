/*------------------------------------------------------------------------------
Name:      HistoryQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Helper class holding QoS settings to acces historical message. 
 * <p />
 * <pre>
 *   &lt;history numEntries='20' newestFirst='true'/>
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

#ifndef _UTIL_QOS_HISTORYQOS_H
#define _UTIL_QOS_HISTORYQOS_H

# include <util/xmlBlasterDef.h>
# include <util/Log.h>
# include <util/Property.h>



        
namespace org { namespace xmlBlaster { namespace util { namespace qos {

extern Dll_Export const long DEFAULT_numEntries;
extern Dll_Export const bool DEFAULT_newestFirst;


class Dll_Export HistoryQos
{
private:
   const std::string ME; //  = "HistoryQos";
   org::xmlBlaster::util::Global&      global_;
   org::xmlBlaster::util::I_Log&         log_;

   long numEntries_; // = DEFAULT_numEntries;
   bool newestFirst_; // = DEFAULT_newestFirst;

public:
   /**
    * @param glob The global handle holding environment and logging objects
    */
   HistoryQos(org::xmlBlaster::util::Global& global, long numOfEntries=-1); 

   HistoryQos(const HistoryQos& qos);

   HistoryQos& operator =(const HistoryQos& qos);

   /**
    * @param numEntries The number of history entries
    */
   void setNumEntries(long numOfEntries);
 
   /**
    * Returns the number of history entries.
    * @return e.g. 1
    */
   long getNumEntries() const;
 
   /**
    * The sorting order in which the history entries are delivered. 
    * The higher priority messages are always delivered first.
    * In one priority the newest message is delivered first with 'true', setting 'false'
    * reverts the delivery sequence in this priority.
    * @param newestFirst defaults to true. 
    */
   void setNewestFirst(bool newestFirst);
 
   /**
    * @return defaults to true
    * @see #setNewestFirst(boolean)
    */
   bool getNewestFirst() const;
 
   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * Only none default values are dumped for performance reasons
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation or "" if all settings are default
    */
   std::string toXml(const std::string& extraOffset="") const;
};

}}}} //namespace

#endif

