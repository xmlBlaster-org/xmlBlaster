/*------------------------------------------------------------------------------
Name:      KeyData.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
#ifndef _UTIL_KEY_KEYDATA_H
#define _UTIL_KEY_KEYDATA_H

#include <util/xmlBlasterDef.h>
#include <util/I_Log.h>
#include <util/ReferenceCounterBase.h>
#include <util/ReferenceHolder.h>

namespace org { namespace xmlBlaster { namespace util { namespace key {

/** The default content MIME type is null */
extern Dll_Export const char* CONTENTMIME_DEFAULT;

/** is "" */
extern Dll_Export const char* DEFAULT_DOMAIN;

/** The default queryType is "EXACT" */
extern Dll_Export const char* QUERYTYPE_DEFAULT;

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
class Dll_Export KeyData : public org::xmlBlaster::util::ReferenceCounterBase
{
protected:
   std::string ME;
   org::xmlBlaster::util::Global& global_;
   org::xmlBlaster::util::I_Log&    log_;

   /** value from attribute <key oid="..."> */
   mutable std::string oid_;

   /** value from attribute <key oid="" contentMime="..."> */
   std::string contentMime_;
   
   /** value from attribute <key oid="" contentMimeExtended="..."> */
   std::string contentMimeExtended_;

   /** value from attribute <key oid="" domain="..."> */
   std::string domain_;
   
   /** Is the key oid generated? */
   mutable bool isGeneratedOid_;
   
   /** The query type */
   std::string queryType_;
   
   /** The query std::string */
   std::string queryString_;

   void init();

   void copy(const KeyData& key);

public:
   /**
    * Minimal constructor.
    */
   KeyData(org::xmlBlaster::util::Global& global);

   /**
    * Copy constructor.
    */
   KeyData(const KeyData& key);

   /**
    * Assignement constructor.
    */
   KeyData& operator =(const KeyData& key);

   virtual ~KeyData();

   void setOid(const std::string& oid);

   /**
    *  @return The key oid or null if not set (see org::xmlBlaster::util::key::MsgKeyData.getOid() which generates the oid if it was null).
    */
   std::string getOid() const;

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
    * @see <a href="ftp://ftp.std.com/customers3/src/mail/istd::map-3.3/RFC1521.TXT">RFC1521 - MIME (Multipurpose Internet Mail Extensions)</a>
    */
   void setContentMime(const std::string& contentMime);

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
    * @param The MIME-extended info, for example<br />
    *         "Version 1.1" in &lt;key oid='' contentMime='text/xml' contentMimeExtended='Version 1.1'><br />
    *         or "" (empty std::string) if not known
    */
   void setContentMimeExtended(const std::string& contentMimeExtended);

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
    * @return true if no domain is given (null or empty std::string). 
    */
   bool isDefaultDomain() const;

   /**
    * Access the query type "XPATH" or "EXACT"
    * @return A queryType std::string or null
    */
   std::string getQueryType() const;

   bool isExact() const;

   bool isQuery() const;

   bool isXPath() const;

   bool isDomain() const;

   /**
    * The size in bytes of the data in XML form. 
    */
   int size() const;

   /**
    * Dump state of this object into a XML ASCII std::string. 
    * <br>
    * Needs to be implemented by derived classes.
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII std::string
    */
   virtual std::string toXml(const std::string& extraOffset) const;
   virtual std::string toXml() const;

   /**
    * Allocate a clone, the derived classes need to implement this method. 
    * @return The caller needs to free it with 'delete'.
    */
   virtual KeyData* getClone() const;

   /**
    * Generates a unique key oid in scope of a cluster node (on server or on client side).
    * @param glob.getStrippedId() on server side
    */
   std::string generateOid(const std::string& uniquePrefix) const;
   
   /**
    * @return true if the key oid is generated by xmlBlaster
    */
   bool isGeneratedOid() const;
};

typedef org::xmlBlaster::util::ReferenceHolder<org::xmlBlaster::util::key::KeyData> KeyDataRef;

}}}} // namespaces

#endif

