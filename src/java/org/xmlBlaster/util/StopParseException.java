/*------------------------------------------------------------------------------
Name:      StopParseException.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Throw this exception to stop SAX parsing
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


/**
 * Throw this exception to stop SAX parsing. 
 * <p />
 * Usually thrown in startElement() or endElement() if
 * you are not interested in the following tags anymore.<br />
 * Note that this exception extends RuntimeException,
 * so we don't need to declare it with a throws clause.
 */
public class StopParseException extends RuntimeException
{
   private static final long serialVersionUID = -8413175809990498728L;
   XmlBlasterException e;
   /**
    * Use this constructor to stop parsing when you are done. 
    */
   public StopParseException() {}

   /**
    * Use this constructor to stop parsing when an exception occurred. 
    * The XmlBlasterException is transported embedded in this class
    */
   public StopParseException(XmlBlasterException e) {
      this.e = e;
   }

   public boolean hasError() {
      return this.e != null;
   }

   public XmlBlasterException getXmlBlasterException() {
      return this.e;
   }
}
