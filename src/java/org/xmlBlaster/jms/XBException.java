/*------------------------------------------------------------------------------
Name:      XBException.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.jms;

import javax.jms.JMSException;

import org.xmlBlaster.util.XmlBlasterException;


/**
 * XBException
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
class XBException extends JMSException {
   
   XBException(String id, String txt) {
      super(id, txt);
   }

   XBException(Exception ex, String additionalTxt) {
      this(getTxt(ex, additionalTxt), ex instanceof XmlBlasterException ? ((XmlBlasterException)ex).getErrorCodeStr() : "");
   }

   private static String getTxt(Exception ex, String additionalTxt) {
      if (additionalTxt == null) additionalTxt = "";
      String txt = additionalTxt + " " + ex.getMessage();
      if (ex instanceof XmlBlasterException) {
         String embedded = ((XmlBlasterException)ex).getEmbeddedMessage();
         if (embedded != null) txt += " " + embedded;
      }
      return txt;
   }
   
}
