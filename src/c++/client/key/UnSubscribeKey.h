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
#include <util/Constants.h>

namespace org { namespace xmlBlaster { namespace client { namespace key {

class Dll_Export UnSubscribeKey
{
protected:
   std::string  ME;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log&    log_;

   /**
    * subscribe(), get() and cluster configuration keys may contain a filter rule
    */
   org::xmlBlaster::util::key::QueryKeyData queryKeyData_;

public:

   /**
    * Minimal constructor.
    */
   UnSubscribeKey(org::xmlBlaster::util::Global& global);

   UnSubscribeKey(org::xmlBlaster::util::Global& global, const std::string& query,
                  const std::string& queryType=org::xmlBlaster::util::Constants::EXACT);
   
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
    * Set the domain for this message, can be used for a simple grouping of
    * messages to their master node with xmlBlaster clusters. 
    * @param The domain, any chosen std::string in your problem domain, e.g. "RUGBY" or "RADAR_TRACK"
    *         defaults to "" where the local xmlBlaster instance is the master of the message.
    * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html">The cluster requirement</a>
    */
   void setDomain(const std::string& domain);

   /**
    * Access the domain setting
    * @return A domain std::string or null
    */
   std::string getDomain() const;
   
   /**
    * Access simplified URL like string. 
    * 
    * @return examples are "exact:hello", "xpath://key", "domain:sport"
    */
   std::string getUrl() const;

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

   const org::xmlBlaster::util::key::QueryKeyData& getData() const;

};

}}}} // namespace

#endif



