/*------------------------------------------------------------------------------
Name:      UnSubscribeKey.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Wrap a XML key for an unSubscribe() invocation. 
 * <p>
 * See QueryKeySaxFactory for a syntax description of the allowed xml structure
 * </p>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html" target="others">the interface.unSubscribe requirement</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */

#ifndef _UTIL_KEY_UNSUBSCRIBEKEY_H
#define _UTIL_KEY_UNSUBSCRIBEKEY_H

#include <util/key/QueryKeyData.h>
#include <util/Log.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::key;

namespace org { namespace xmlBlaster { namespace client { namespace key {

class Dll_Export UnSubscribeKey
{
protected:
   string  ME;
   Global& global_;
   Log&    log_;

   /**
    * subscribe(), get() and cluster configuration keys may contain a filter rule
    */
   QueryKeyData queryKeyData_;

public:

   /**
    * Minimal constructor.
    */
   UnSubscribeKey(Global& global);
   
   UnSubscribeKey(Global& global, const QueryKeyData& data);

   UnSubscribeKey(const UnSubscribeKey& key);

   UnSubscribeKey& operator =(const UnSubscribeKey& key);

   void setOid(const string& oid);

   /**
    *  @return The key oid or "" if not set (see MsgKeyData.getOid() which generates the oid if it was "").
    */
   string getOid() const;

   /**
    * Access the query type "XPATH" or "EXACT"
    * @return A queryType string or ""
    */
   string getQueryType() const;


   void setQueryType(const string& queryType);

   /**
    * Your XPath query string. 
    * @param str Your tags in ASCII XML syntax
    */
   void setQueryString(const string& tags);

   string getQueryString() const;

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII string
    */
   string toXml(const string& extraOffset="") const;

   /**
    * May be used to integrate your application tags.
    * @param str Your tags
    * @return The ASCII XML key containing the key tag and your tags
    */
   string wrap(const string& str);

};

}}}} // namespace

#endif



