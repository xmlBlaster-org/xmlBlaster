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
#include <vector>

namespace org { namespace xmlBlaster { namespace util { namespace key {

typedef std::vector<org::xmlBlaster::util::qos::AccessFilterQos> AccessFilterVector;

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
   QueryKeyData(org::xmlBlaster::util::Global& global);
   
   QueryKeyData(org::xmlBlaster::util::Global& global, const std::string& query, const std::string& queryType);

   QueryKeyData(const QueryKeyData& key);

   std::string checkQueryType(const std::string& queryType);

   QueryKeyData& operator =(const QueryKeyData& key);

   void setOid(const std::string& oid);

   void setQueryType(const std::string& queryType);

   /**
    * Your XPath query std::string. 
    * @param str Your tags in ASCII XML syntax
    */
   void setQueryString(const std::string& tags);

   std::string getQueryString() const;

   /**
    * Return the filters or array with size==0 if none is specified. 
    * <p />
    * For subscribe() and get() and cluster messages.
    * @return never null
    */
   AccessFilterVector getAccessFilterVector() const;

   void addFilter(const org::xmlBlaster::util::qos::AccessFilterQos& qos);

   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII std::string
    */
   virtual std::string toXml(const std::string& extraOffset) const;
   virtual std::string toXml() const;

   /**
    * Allocate a clone. 
    * @return The caller needs to free it with 'delete'.
    */
   QueryKeyData* getClone() const;
};

}}}} // namespace

#endif



