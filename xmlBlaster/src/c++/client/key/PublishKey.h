/**
 * Wrap the XML key for a subscribe() invocation. 
 * <p>
 * See QueryKeySaxFactory for a syntax description of the allowed xml structure
 * </p>
 * @see org.xmlBlaster.util.key.QueryKeySaxFactory
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html" target="others">the interface.subscribe requirement</a>
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */

#ifndef _CLIENT_KEY_PUBLISHKEY_H
#define _CLIENT_KEY_PUBLISHKEY_H

#include <client/key/MsgKeyBase.h>

namespace org { namespace xmlBlaster { namespace client { namespace key {

class Dll_Export PublishKey : public MsgKeyBase
{
public:

   /**
    * Minimal constructor.
    */
   PublishKey(org::xmlBlaster::util::Global& global, const std::string& oid="", const std::string& mime="", const std::string& mimeExt="");
   
   PublishKey(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::key::MsgKeyData& data);

   PublishKey(const PublishKey& key);

   PublishKey& operator =(const PublishKey& key);

   /**
    * Set the domain for this message, can be used for a simple grouping of
    * messages to their master node with xmlBlaster clusters. 
    * @param The domain, any chosen std::string in your problem domain, e.g. "RUGBY" or "RADAR_TRACK"
    *         defaults to "" where the local xmlBlaster instance is the master of the message.
    * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html">The cluster requirement</a>
    */
   void setDomain(const std::string& domain);


   void setOid(const std::string& oid);

   /**
    * Set mime type (syntax) of the message content. 
    * @return The MIME type, for example "text/xml" in &lt;key oid='' contentMime='text/xml'><br />
    *         default is "text/plain" if not set
    * @see <a href="ftp://ftp.std.com/customers3/src/mail/istd::map-3.3/RFC1521.TXT">RFC1521 - MIME (Multipurpose Internet Mail Extensions)</a>
    */
   void setContentMime(const std::string& contentMime);

   /**
    * Some further specifying information of the content.
    * <p />
    * For example the application version number the document in the content.<br />
    * You may use this attribute for you own purposes.
    * @param The MIME-extended info, for example<br />
    *         "Version 1.1" in &lt;key oid='' contentMime='text/xml' contentMimeExtended='Version 1.1'><br />
    *         or "" (empty std::string) if not known
    */
   void setContentMimeExtended(const std::string& contentMimeExtended);

   /**
    * Set client specific meta informations. 
    * <p />
    * May be used to integrate your application tags, for example:
    * <p />
    * <pre>
    *&lt;key oid='4711' contentMime='text/xml'>
    *   &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
    *      &lt;DRIVER id='FileProof' pollingFreq='10'>
    *      &lt;/DRIVER>
    *   &lt;/AGENT>
    *&lt;/key>
    * </pre>
    * @param str Your tags in ASCII XML syntax
    */
   void setClientTags(const std::string& tags);

};

}}}} // namespace

#endif



