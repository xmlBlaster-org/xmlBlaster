/*-----------------------------------------------------------------------------
Name:      UpdateKey.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlKey, knows how to parse it with DOM
-----------------------------------------------------------------------------*/

#ifndef _CLIENT_UPDATEKEY_H
#define _CLIENT_UPDATEKEY_H

#include <string>
#include <util/Log.h>
#include <util/SaxHandlerBase.h>
#include <sax/AttributeList.hpp>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;

namespace org { namespace xmlBlaster { 
   
/**
 * This class encapsulates the Message meta data and unique identifier of a 
 * received message.
 * <p />
 * A typical <b>update</b> key could look like this:<br />
 * <pre>
 *     &lt;key oid='4711' contentMime='text/xml'>
 *        &lt;AGENT id='192.168.124.20' subId='1' type='generic'>
 *           &lt;DRIVER id='FileProof' pollingFreq='10'>
 *           &lt;/DRIVER>
 *        &lt;/AGENT>
 *     &lt;/key>
 * </pre>
 * <br />
 * Note that the AGENT and DRIVER tags are application know how, which you 
 * have to supply.<br />
 * A well designed xml hierarchy of your problem domain is essential for a 
 * proper working xmlBlaster <p />
 * This is exactly the key how it was published from the data source. <p />
 * Call updateKey.init(xmlKey_literal); to start parsing the received key
 * @see org.xmlBlaster.util.UpdateKeyBase <p />
 * see xmlBlaster/src/dtd/UpdateKey.xml <p />
 * see http://www.w3.org/TR/xpath
 */
   
   class UpdateKey : public util::SaxHandlerBase {
      
   private:
      
      string me() {
         return "UpdateKey";
      }
      
   protected:
      bool inKey_; // parsing inside <key> ? </key>

   /** value from attribute <key oid="..."> */
      string keyOid_;

   /** value from attribute <key oid="" contentMime="..."> */
      string contentMime_; 

   /** value from attribute <key oid="" contentMimeExtended="..."> */
      string contentMimeExtended_; 

   /**
    * Constructs an un initialized UpdateKey object.
    * You need to call the init() method to parse the XML string.
    */
   public:
      
      UpdateKey(Global& global) :
         util::SaxHandlerBase(global) {
         inKey_               = false;
         keyOid_              = "";
         contentMime_         = "text/plain";
         contentMimeExtended_ = "";
         if (log_.CALL) log_.trace(me(), "Creating new UpdateKey");
      }


      /**
       * Access the $lt;key oid="...">.
       * @return The unique key oid
       */
      const string &getUniqueKey() const {
         return keyOid_;
      }


      /**
       * Find out which mime type (syntax) the content of the message has.
       * @return e.g "text/xml" or "image/png"
       *         defaults to "text/plain"
       */
      const string &getContentMime() const {
         return contentMime_;
      }


      /**
       * Some further specifying information of the content.<p />
       * For example the application version number the document in the 
       * content.<br />
       * You may use this attribute for you own purposes.
       * @return The MIME-extended info, for example<br />
       *         "Version 1.1" in &lt;key oid='' contentMime='text/xml' 
       * contentMimeExtended='Version 1.1'><br />
       *         or "" (empty string) if not known
       */
      const string &getContentMimeExtended() const {
         return contentMimeExtended_;
      }

      
      /**
       * Start element callback, does handling of tag &lt;key> and its 
       * attributes. <p />
       * You may include this into your derived startElement() method like 
       * this:<br />
       * <pre>
       *  if (UpdateKey::startElementBase(name, attrs) == true)
       *     return;
       * </pre>
       * @return true if the tag is parsed here, the derived class doesn't 
       * need to look at this tag anymore
       *         false this tag is not handled by this Base class
       */
      bool startElementBase(const XMLCh* name, AttributeList &attrs) {
         bool sameName = caseCompare(name,"key");
         if (sameName) {
            inKey_ = true;
            
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               XMLCh* attrName = XMLString::replicate(attrs.getName(i));
               if ( caseCompare(attrName, "oid") ) {
                  XMLCh* oidHelper = xmlChTrimmer_.trim(attrs.getValue(i));
                  char *buffer = XMLString::transcode(oidHelper);
                  keyOid_      = buffer;
                  delete buffer;
                  delete oidHelper;
               }
               
               if( caseCompare(attrName, "contentMime") ) {
                  XMLCh* contentMimeHelper = 
                     xmlChTrimmer_.trim(attrs.getValue(i));
                  char *buffer = XMLString::transcode(contentMimeHelper);
                  contentMime_ = buffer;
                  if (contentMime_ == "") contentMime_ = "text/plain";
                  delete buffer;
                  delete contentMimeHelper;
               }
               
               if( caseCompare(attrName, "contentMimeExtended") ) {
                  XMLCh* contentMimeExtendedHelper = 
                     xmlChTrimmer_.trim(attrs.getValue(i));
                  char *buffer = 
                     XMLString::transcode(contentMimeExtendedHelper);
                  contentMimeExtended_ = buffer;
                  delete buffer;
                  delete contentMimeExtendedHelper;
               }
               delete attrName;
            }
            if (keyOid_ == "")
               log_.warn(me(), "The oid of the message is missing");
            if (contentMime_ == "")
               log_.warn(me(), 
                            "The contentMime of the message is missing");
            return true;
         }
         return false;
      }
      
      
      /**
       * Start element.
       * <p />
       * Default implementation, knows how to parse &lt;key> but knows nothing
       * about the tags inside of key
       */
      void startElement(const XMLCh* const name, AttributeList& attrs) {
         if (!startElementBase(name, attrs)) {
            // Now i know what i need to know, stop parsing here (i'm not 
            // interested in the tags inside)
            throw util::StopParseException();
         }
      }

      /**
       * Since xerces ver 1.2
       */
      void setDocumentLocator(const Locator * /*loc*/) {
      }

      /**
       * End element callback, does handling of tag &lt;key>. <p />
       * You may include this into your derived endElement() method like 
       * this:<br />
       * <pre>
       *  if (UpdateKey::endElementBase(name)) return;
       * </pre>
       * @return true if the tag is parsed here, the derived class doesn't 
       *           need to look at this tag anymore
       *         false this tag is not handled by this Base class
       */
   protected:
      bool endElementBase(const XMLCh* const name) {
         if( caseCompare(name, "key") ) {
            inKey_ = false;
            character_ = "";
            return true;
         }
         return false;
      }


      /**
       * End element callback, does handling of tag &lt;key>. <p />
       * You may include this into your derived endElement() method like 
       * this:<br />
       * <pre>
       *  if (super.endElementBase(name) == true)
       *     return;
       * </pre>
       * @return true if the tag is parsed here, the derived class doesn't 
       *           need to look at this tag anymore
       *         false this tag is not handled by this Base class
       */
   public:
      void endElement(const XMLCh* const name) {
         endElementBase(name);
      }


      /**
       * Dump state of this object into XML.
       * <br>
       * @return XML state of MessageUnitHandler
       */
      string printOn() {
         return printOn("");
      }


      /**
       * Dump state of this object into XML.
       * <br>
       * @param extraOffset indenting of tags
       * @return XML state of UpdateKey
       */
      string printOn(const string &extraOffset) {
         string sb, offset = "\n   ";
         offset += extraOffset;
         sb  = offset + "<key oid='" + getUniqueKey() + "'";
         sb += " contentMime='" + getContentMime() + "'";
         sb += " contentMimeExtended='" + getContentMimeExtended() + "' >\n";
         sb += offset + "</key>\n";
         return sb;
      }
   };
}} // namespace

#endif
