/*------------------------------------------------------------------------------
Name:      RunLevelActionSaxFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.runlevel;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.util.def.ErrorCode;
import org.xml.sax.Attributes;


/**
 * This class parses an xml string to generate a RunLevelAction object.
 * <p>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.runlevel.html">engine.runlevel requirement</a>
 * <pre>
 *  &lt;action do='LOAD'
 *             onStartupRunlevel='3'
 *             sequence='5'
 *             onFail='RESOURCE_CONFIGURATION_PLUGINFAILED'/>
 * </pre>
 */
public class RunLevelActionSaxFactory extends SaxHandlerBase
{
   private String ME = "RunLevelActionSaxFactory";
   private final Global glob;
   private static Logger log = Logger.getLogger(RunLevelActionSaxFactory.class.getName());

   private RunLevelAction runLevelAction;
   private boolean isAction = false; // to set when an 'action' tag has been found (to know when to throw an ex)
   private XmlBlasterException ex = null;

   /**
    * Can be used as singleton. 
    */
   public RunLevelActionSaxFactory(Global glob) {
      super(glob);
      setUseLexicalHandler(true); // to allow CDATA wrapped attributes 
      this.glob = glob;

   }

   public void reset() {
      this.isAction = false;
      this.ex = null; // reset the exceptions 
      this.runLevelAction = new RunLevelAction(glob);
   }

   public RunLevelAction getObject() {
      return this.runLevelAction;
   }

   /**
    * Parses the given xml Qos and returns a RunLevelActionData holding the data. 
    * Parsing of update() and publish() QoS is supported here.
    * @param the XML based ASCII string
    */
   public synchronized RunLevelAction readObject(String xmlTxt) throws XmlBlasterException {
      if (xmlTxt == null || xmlTxt.trim().length() < 1)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".readObject", "the action element is empty");
      reset();
      try {
         this.init(xmlTxt);      // use SAX parser to parse it (is slow)
      }
      catch (Throwable thr) {
         if (log.isLoggable(Level.FINE)) {
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".readObject", "exception occured when parsing the <action> tag. In fact it was '" + xmlTxt + "'", thr);
         }
         else {
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".readObject", "exception occured when parsing the <action> tag. In fact it was '" + xmlTxt + "'");
         }
      }

      if (this.ex != null) throw ex;

      if (!this.isAction) 
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".readObject", "the string '" + xmlTxt + "' does not contain the <action> tag");
      return this.runLevelAction;
   }


   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public final void startElement(String uri, String localName, String name, Attributes attrs) {
      if (this.ex != null ) return;
      if ("action".equalsIgnoreCase(name)) {
         this.isAction = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               String key = attrs.getQName(i);
               String value = attrs.getValue(i).trim();

               if ("do".equalsIgnoreCase(key)) {
                  this.runLevelAction.setDo(value);
                  continue;
               }
               if ("onStartupRunlevel".equalsIgnoreCase(key)) {
                  try {
                     int level = Integer.parseInt(value);
                     this.runLevelAction.setOnStartupRunlevel(level);
                  }
                  catch (NumberFormatException ex) {
                     log.warning("startElement onStartupRunlevel='" + value + "' is not an integer");
                  }
                  continue;
               }
               if ("onShutdownRunlevel".equalsIgnoreCase(key)) {
                  try {
                     int level = Integer.parseInt(value);
                     this.runLevelAction.setOnShutdownRunlevel(level);
                  }
                  catch (NumberFormatException ex) {
                     log.warning("startElement onShutdownRunlevel='" + value + "' is not an integer");
                  }
                  continue;
               }
               if ("sequence".equalsIgnoreCase(key)) {
                  try {
                     int sequence = Integer.parseInt(value);
                     this.runLevelAction.setSequence(sequence);
                  }
                  catch (NumberFormatException ex) {
                     log.warning("startElement sequence='" + value + "' is not an integer");
                  }
                  continue;
               }
               if ("onFail".equalsIgnoreCase(key)) {
                  if (value.length() > 1) { // if empty ignore it
                     if (log.isLoggable(Level.FINE)) this.log.fine("startElement: onFail : " + key + "='" + value +"'");
                     try {
                        ErrorCode code = ErrorCode.toErrorCode(value);
                        this.runLevelAction.setOnFail(code);
                     }
                     catch (IllegalArgumentException ex) {
                        log.warning("startElement onFail='" + value + "' is an unknown error code");
                        this.ex = new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".startElement", "check the spelling of your error code, it is unknown and probably wrongly spelled", ex);
                     }
                  }
                  continue;
               }
               log.warning("startElement: unknown attribute '" + key + "' with value '" + value + "' used");
            }
         }
         return;
      }

      log.warning("startElement: unknown tag '" + name + "'");
   }

   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name) {
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String writeObject(RunLevelAction runLevelAction, String extraOffset) {
      return runLevelAction.toXml(extraOffset);
   }

   /**
    * A human readable name of this factory
    * @return "RunLevelActionSaxFactory"
    */
   public String getName() {
      return "RunLevelActionSaxFactory";
   }
}

