/*------------------------------------------------------------------------------
Name:      KeyData.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

/**
 * This class encapsulates the Message meta data and unique identifier (key)
 * of a publish()/update() or get()-return message.
 * <p />
 * A typical key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711' contentMime='text/xml'>
 *        &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
 *           &lt;DRIVER id='FileProof' pollingFreq='10'>
 *           &lt;/DRIVER>
 *        &lt;/AGENT>
 *     &lt;/key>
 * </pre>
 * <br />
 * Note that the AGENT and DRIVER tags are application know how, which you have
 * to supply to the setClientTags() method.<br />
 * A well designed xml hierarchy of your problem domain is essential for a proper working xmlBlaster
 * <p />
 * <p>
 * NOTE: Message oid starting with "__" is reserved for internal usage.
 * </p>
 * <p>
 * NOTE: Message oid starting with "_" is reserved for xmlBlaster plugins.
 * </p>
 */

#ifndef _UTIL_KEY_KEYDATA_H
#define _UTIL_KEY_KEYDATA_H

#include <util/xmlBlasterDef.h>
#include <util/Log.h>

using namespace std;
//using namespace org::xmlBlaster::util; <-- VC CRASH

namespace org { namespace xmlBlaster { namespace util { namespace key {

/** The default content MIME type is null */
extern Dll_Export const char* CONTENTMIME_DEFAULT;

/** is "" */
extern Dll_Export const char* DEFAULT_DOMAIN;

/** The default queryType is "EXACT" */
extern Dll_Export const char* QUERYTYPE_DEFAULT;

class Dll_Export KeyData
{
protected:
   string ME;
   Global& global_;
   Log&    log_;

   /** value from attribute <key oid="..."> */
   mutable string oid_;

   /** value from attribute <key oid="" contentMime="..."> */
   string contentMime_;
   
   /** value from attribute <key oid="" contentMimeExtended="..."> */
   string contentMimeExtended_;

   /** value from attribute <key oid="" domain="..."> */
   string domain_;
   
   /** Is the key oid generated? */
   mutable bool isGeneratedOid_;
   
   /** The query type */
   string queryType_;
   
   /** The query string */
   string queryString_;

   void init();

   void copy(const KeyData& key);

public:
   /**
    * Minimal constructor.
    */
   KeyData(Global& global);

   KeyData(const KeyData& key);

   KeyData& operator =(const KeyData& key);

   virtual ~KeyData();

   void setOid(const string& oid);

   /**
    *  @return The key oid or null if not set (see MsgKeyData.getOid() which generates the oid if it was null).
    */
   string getOid() const;

   /**
    * Test if oid is '__sys__deadMessage'. 
    * <p />
    * Dead letters are unrecoverable lost messages, usually an administrator
    * should subscribe to those messages.
    * <p>
    * This is an internal message (isInternal() returns true)
    * </p>
    */
   bool isDeadMessage() const;

   /**
    * Messages starting with "_" are reserved for usage in plugins
    */
   bool isPluginInternal() const;

   /**
    * Messages starting with "__" are reserved for internal usage
    */
   bool isInternal() const;

   /**
    * Messages starting with "__cmd:" are administrative messages
    */
   bool isAdministrative() const;

   /**
    * Set mime type (syntax) of the message content. 
    * @return The MIME type, for example "text/xml" in &lt;key oid='' contentMime='text/xml'><br />
    *         default is "text/plain" if not set
    * @see <a href="ftp://ftp.std.com/customers3/src/mail/imap-3.3/RFC1521.TXT">RFC1521 - MIME (Multipurpose Internet Mail Extensions)</a>
    */
   void setContentMime(const string& contentMime);

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
    * @param The MIME-extended info, for example<br />
    *         "Version 1.1" in &lt;key oid='' contentMime='text/xml' contentMimeExtended='Version 1.1'><br />
    *         or "" (empty string) if not known
    */
   void setContentMimeExtended(const string& contentMimeExtended);

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
    * Set the domain for this message, can be used for a simple grouping of
    * messages to their master node with xmlBlaster clusters. 
    * @param The domain, any chosen string in your problem domain, e.g. "RUGBY" or "RADAR_TRACK"
    *         defaults to "" where the local xmlBlaster instance is the master of the message.
    * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html">The cluster requirement</a>
    */
   void setDomain(const string& domain);

   /**
    * Access the domain setting
    * @return A domain string or null
    */
   string getDomain() const;

   /**
    * @return true if no domain is given (null or empty string). 
    */
   bool isDefaultDomain() const;

   /**
    * Access the query type "XPATH" or "EXACT"
    * @return A queryType string or null
    */
   string getQueryType() const;

   bool isExact() const;

   bool isQuery() const;

   bool isXPath() const;

   bool isDomain() const;

   /**
    * The size in bytes of the data in XML form. 
    */
   int size() const;

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII string
    */
   virtual string toXml(const string& extraOffset="") const = 0;

   /**
    * Generates a unique key oid in scope of a cluster node (on server or on client side).
    * @param glob.getStrippedId() on server side
    */
   string generateOid(const string& uniquePrefix) const;
   
   /**
    * @return true if the key oid is generated by xmlBlaster
    */
   bool isGeneratedOid() const;
};

}}}} // namespaces

#endif

