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
 * This is the base class for the UpdateKey and the PublishKey. 
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */

#ifndef _CLIENT_KEY_MSGKEYBASE_H
#define _CLIENT_KEY_MSGKEYBASE_H

#include <util/key/MsgKeyData.h>
#include <util/Log.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::key;

namespace org { namespace xmlBlaster { namespace client { namespace key {

class Dll_Export MsgKeyBase
{
protected:
   string  ME;
   Global& global_;
   Log&    log_;

   /**
    * subscribe(), get() and cluster configuration keys may contain a filter rule
    */
   MsgKeyData msgKeyData_;

public:

   /**
    * Minimal constructor.
    */
   MsgKeyBase(Global& global);
   
   MsgKeyBase(Global& global, const MsgKeyData& data);

   MsgKeyBase(const MsgKeyBase& key);

   MsgKeyBase& operator =(const MsgKeyBase& key);

   MsgKeyData getData() const;


   /**
    *  @return The key oid or "" if not set (see MsgKeyData.getOid() which generates the oid if it was "").
    */
   string getOid() const;

   /**
    * Find out which mime type (syntax) the content of the message has.
    * @return The MIME type, for example "text/xml" in &lt;key oid='' contentMime='text/xml'><br />
    *         default is "text/plain" if not set
    * @see <a href="ftp://ftp.std.com/customers3/src/mail/imap-3.3/RFC1521.TXT">RFC1521 - MIME (Multipurpose Internet Mail Extensions)</a>
    */
   string getContentMime() const;

   /**
    * Some further specifying information of the content.
    * <p />
    * For example the application version number the document in the content.<br />
    * You may use this attribute for you own purposes.
    * @return The MIME-extended info, for example<br />
    *         "Version 1.1" in &lt;key oid='' contentMime='text/xml' contentMimeExtended='Version 1.1'><br />
    *         or "" (empty string) if not known
    */
   string getContentMimeExtended() const;

   /**
    * Access the domain setting
    * @return A domain string or null
    */
   string getDomain() const;

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



