/*------------------------------------------------------------------------------
Name:      MsgKeyBase.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * Wrap a XML key for an unSubscribe() invocation. 
 * <p>
 * See QueryKeySaxFactory for a syntax description of the allowed xml structure
 * </p>
 * This is the base class for the org::xmlBlaster::client::key::UpdateKey and the org::xmlBlaster::client::key::PublishKey. 
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */

#ifndef _CLIENT_KEY_MSGKEYBASE_H
#define _CLIENT_KEY_MSGKEYBASE_H

#include <util/key/MsgKeyData.h>
#include <util/I_Log.h>

namespace org { namespace xmlBlaster { namespace client { namespace key {

class Dll_Export MsgKeyBase
{
protected:
   std::string  ME;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log&    log_;

   /**
    * subscribe(), get() and cluster configuration keys may contain a filter rule
    */
   org::xmlBlaster::util::key::MsgKeyData msgKeyData_;

public:

   /**
    * Minimal constructor.
    */
   MsgKeyBase(org::xmlBlaster::util::Global& global);
   
   MsgKeyBase(org::xmlBlaster::util::Global& global, const org::xmlBlaster::util::key::MsgKeyData& data);

   MsgKeyBase(const MsgKeyBase& key);

   MsgKeyBase& operator =(const MsgKeyBase& key);

   const org::xmlBlaster::util::key::MsgKeyData& getData() const;

   org::xmlBlaster::util::Global& getGlobal() { return global_; }

   /**
    *  @return The key oid or "" if not set (see org::xmlBlaster::util::key::MsgKeyData.getOid() which generates the oid if it was "").
    */
   std::string getOid() const;

   /**
    * Find out which mime type (syntax) the content of the message has.
    * @return The MIME type, for example "text/xml" in &lt;key oid='' contentMime='text/xml'><br />
    *         default is "text/plain" if not set
    * @see <a href="ftp://ftp.std.com/customers3/src/mail/istd::map-3.3/RFC1521.TXT">RFC1521 - MIME (Multipurpose Internet Mail Extensions)</a>
    */
   std::string getContentMime() const;

   /**
    * Some further specifying information of the content.
    * <p />
    * For example the application version number the document in the content.<br />
    * You may use this attribute for you own purposes.
    * @return The MIME-extended info, for example<br />
    *         "Version 1.1" in &lt;key oid='' contentMime='text/xml' contentMimeExtended='Version 1.1'><br />
    *         or "" (empty std::string) if not known
    */
   std::string getContentMimeExtended() const;

   /**
    * Access the domain setting
    * @return A domain std::string or null
    */
   std::string getDomain() const;

   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII std::string
    */
   std::string toXml(const std::string& extraOffset="") const;

};

}}}} // namespace

#endif



