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
#ifndef _CLIENT_KEY_UNSUBSCRIBEKEY_H
#define _CLIENT_KEY_UNSUBSCRIBEKEY_H

#include <util/key/QueryKeyData.h>
#include <util/Log.h>

namespace org { namespace xmlBlaster { namespace client { namespace key {

class Dll_Export UnSubscribeKey
{
protected:
   std::string  ME;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::Log&    log_;

   /**
    * subscribe(), get() and cluster configuration keys may contain a filter rule
    */
   org::xmlBlaster::util::key::QueryKeyData queryKeyData_;

public:

   /**
    * Minimal constructor.
    */
   UnSubscribeKey(org::xmlBlaster::util::Global& global);

   UnSubscribeKey(org::xmlBlaster::util::Global& global, const std::string& query, const std::string& queryType);
   
   UnSubscribeKey(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::key::QueryKeyData& data);

   UnSubscribeKey(const UnSubscribeKey& key);

   UnSubscribeKey& operator =(const UnSubscribeKey& key);

   void setOid(const std::string& oid);

   /**
    *  @return The key oid or "" if not set (see org::xmlBlaster::util::key::MsgKeyData.getOid() which generates the oid if it was "").
    */
   std::string getOid() const;

   /**
    * Access the query type "XPATH" or "EXACT"
    * @return A queryType std::string or ""
    */
   std::string getQueryType() const;

   /**
    * Your XPath query std::string. 
    * @param str Your tags in ASCII XML syntax
    */
   void setQueryString(const std::string& tags);

   std::string getQueryString() const;

   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII std::string
    */
   std::string toXml(const std::string& extraOffset="") const;

   /**
    * May be used to integrate your application tags.
    * @param str Your tags
    * @return The ASCII XML key containing the key tag and your tags
    */
   std::string wrap(const std::string& str);

   org::xmlBlaster::util::key::QueryKeyData getData() const;

};

}}}} // namespace

#endif



