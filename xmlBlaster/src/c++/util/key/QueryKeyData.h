/*------------------------------------------------------------------------------
Name:      QueryKeyData.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * This class encapsulates the Message key information of query invocations. 
 * <p />
 * <ul>
 * <li>SubscribeKey Client side access facade</i>
 * <li>UnSubscribeKey Client side access facade</i>
 * <li>GetKey Client side access facade</i>
 * <li>EraseKey Client side access facade</i>
 * </ul>
 * <p>
 * For the xml representation see MsgKeySaxFactory.
 * </p>
 * @see org.xmlBlaster.util.key.QueryKeySaxFactory
 */

#ifndef _UTIL_KEY_QUERYKEYDATA_H
#define _UTIL_KEY_QUERYKEYDATA_H

#include <util/key/KeyData.h>
#include <util/qos/AccessFilterQos.h>
#include <string>

using namespace std;
//using namespace org::xmlBlaster::util;<-- VC CRASH
//using namespace org::xmlBlaster::util::qos;<-- VC CRASH

namespace org { namespace xmlBlaster { namespace util { namespace key {

typedef vector<AccessFilterQos> AccessFilterVector;

class Dll_Export QueryKeyData : public KeyData
{
protected:

   /**
    * subscribe(), get() and cluster configuration keys may contain a filter rule
    */
   AccessFilterVector accessFilterVector_;

public:

   /**
    * Minimal constructor.
    */
   QueryKeyData(Global& global);
   
   QueryKeyData(Global& global, const string& query, const string& queryType);

   QueryKeyData(const QueryKeyData& key);

   string checkQueryType(const string& queryType);

   QueryKeyData& operator =(const QueryKeyData& key);

   void setOid(const string& oid);

   void setQueryType(const string& queryType);

   /**
    * Your XPath query string. 
    * @param str Your tags in ASCII XML syntax
    */
   void setQueryString(const string& tags);

   string getQueryString() const;

   /**
    * Return the filters or array with size==0 if none is specified. 
    * <p />
    * For subscribe() and get() and cluster messages.
    * @return never null
    */
   AccessFilterVector getAccessFilterVector() const;

   void addFilter(const AccessFilterQos& qos);

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII string
    */
   string toXml(const string& extraOffset="") const;

};

}}}} // namespace

#endif



