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
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
class XBException extends JMSException {
   
   XBException(String id, String txt) {
      super(id, txt);
   }

   XBException(XmlBlasterException ex, String additionalTxt) {
      this(getTxt(ex, additionalTxt), ex.getErrorCodeStr());
   }

   private static String getTxt(XmlBlasterException ex, String additionalTxt) {
      if (additionalTxt == null) additionalTxt = "";
      String txt = additionalTxt + " " + ex.getMessage();
      String embedded = ex.getEmbeddedMessage();
      if (embedded != null) txt += " " + embedded;
      return txt;
   }
   
}
