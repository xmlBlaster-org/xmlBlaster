/*------------------------------------------------------------------------------
Name:      ContentLenFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: ContentLenFilter.java,v 1.1 2002/03/14 19:17:18 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime.demo;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.mime.FilterRule;
import org.xmlBlaster.engine.Global;


/**
 * This demo plugin filters away all messages having a content longer than the given filter length. 
 * <p />
 * Message which are longer then the max length are used to filter subscribed messages,
 * they are not send via updated() to the subscriber.
 * <p />
 * Please register this plugin in xmlBlaster.properties:
 * <pre>
 * MimeSubscribePlugin[text/plainl][1.0]=org.xmlBlaster.engine.mime.demo.ContentLenFilter
 * </pre>
 * @author ruff@swand.lake.de
 */
public class ContentLenFilter
{
   private final String ME = "ContentLenFilter";
   private Global glob;
   private Log log;
   private final long DEFAULT_MAX_LEN = 1000000; // 1 Mbyte

   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(Global glob) {
      this.glob = glob;
      this.log = glob.getLog();
      log.info(ME, "Filter is initialized, we check mime '" + getMime() + "' if content is not to long");
   }

   /** Get a human readable name of this filter implementation */
   public String getName() {
      return "ContentLenFilter";
   }

   /** Get the content MIME type for which this plugin applies */
   public String getMime() {
      return "text/plain";
   }

   /** Get the content MIME version number for which this plugin applies */
   public String getMimeExtended() {
      return "1.0";
   }

   /**
    * Check if the filter rule matches for this message. 
    * @param msgUnit The message to check
    * @param query   The max. message length as given by the subscriber.<br />
    *                If null we use 1 MByte as max size
    * @return true   If message is not to long
    * @exception XmlBlasterException Is thrown on problems, for example if MIME type
    *            does not fit to message content
    */
   public boolean match(MessageUnit msgUnit, FilterRule query) throws XmlBlasterException {
      if (msgUnit == null || query == null) {
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Illegal argument in match() call");
      }
      try {
         long maxLen;

         if (query != null) { // Subscriber has given own max length
            String q = query.getQuery(); // q == null is caught below
            maxLen = new Long(q).longValue();
         }
         else                 // Use default max length
            maxLen = DEFAULT_MAX_LEN;

         if (msgUnit.getContent().length > maxLen)
            return false; // message will not be send to client
         else
            return true;  // message will be delivered
      }
      catch (Throwable e) {
         String tmp = "Can't filter message, your filter string '" + query.getQuery() + "' is illegal, expected a max size integer: " + e.toString();
         log.error(ME, tmp);
         throw new XmlBlasterException(ME, tmp);
      }
   }
}

