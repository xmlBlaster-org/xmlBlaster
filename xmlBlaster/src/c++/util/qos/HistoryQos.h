/*------------------------------------------------------------------------------
Name:      HistoryQos.h
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

# include <util/xmlBlasterDef.h>
# include <util/Log.h>
# include <util/Property.h>

using namespace std;
using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

extern Dll_Export const long DEFAULT_numEntries;


class Dll_Export HistoryQos
{
private:
   const string ME; //  = "HistoryQos";
   Global&      global_;
   Log&         log_;

   int numEntries_; // = DEFAULT_numEntries;

public:
   /**
    * @param glob The global handle holding environment and logging objects
    */
   HistoryQos(Global& global, long numOfEntries=-1); 

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
    * Dump state of this object into a XML ASCII string.
    * <br>
    * Only none default values are dumped for performance reasons
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation or "" if all settings are default
    */
   string toXml(const string& extraOffset="") const;
};

}}}} //namespace


