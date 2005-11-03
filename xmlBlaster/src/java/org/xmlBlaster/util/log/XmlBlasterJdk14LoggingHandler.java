/*------------------------------------------------------------------------------
Name:      XmlBlasterJdk14LoggingHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Properties for xmlBlaster, using org.jutils
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.log;

import java.io.*;
import java.net.*;
import java.util.logging.*;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * This <tt>Handler</tt> redirects JDK 1.4 log records to xmlBlasters <tt>org.jutils.log.LogableDevice</tt>. 
 * <p>
 * <tt>xmlBlasterJdk14Logging.properties</tt> is the default configuration file,
 * it is looked up the the default xmlBlaster search pathes (similiar to <tt>xmlBlaster.properties</tt>).
 * The template used resides in directory <tt>xmlBlaster/config</tt>.
 * </p>
 * <p>
 * This redirection is useful for xmlBlaster native plugins which use JDK 1.4 logging.
 * </p>
 * <p>
 * As a default this redirection is switched on, you can disable it with
 * <tt>-xmlBlaster/jdk14loggingCapture false</tt> on startup.
 * </p>
 * @since 1.4
 * @since XmlBlaster 1.1
 */
public class XmlBlasterJdk14LoggingHandler extends Handler {
   private Global glob;

    /**
     * Is created by java.util.logging.LogManager. 
     */
   public XmlBlasterJdk14LoggingHandler() {
      this.glob = Global.instance();
   }

   /**
    * Configure JDK 1.4 java.util.logging to use xmlBlasters jutils logging. 
    * <p>
    * XmlBlaster should call initially on startup one time this method to redirect
    * JDK 1.4 logging output to our logging system.
    * </p>
    * @param glob The configuration
    * @return The used configuration file (can be used for user notification)
    * @throws XmlBlasterException if redirection fails
    */
   public static URL initLogManager(Global glob) throws XmlBlasterException {
      FileLocator fl = new FileLocator(glob);
      URL url = fl.findFileInXmlBlasterSearchPath("xmlBlaster/jdk14LogFile", "xmlBlasterJdk14Logging.properties");
      if (url == null) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION,
         "XmlBlasterJdk14LoggingHandler", "Can't find xmlBlaster/jdk14LogFile=xmlBlasterJdk14Logging.properties");
      }
      try {
         InputStream in = url.openStream();
         LogManager.getLogManager().readConfiguration(in);
         in.close();
         return url;
      }
      catch (Exception e) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION,
                   "XmlBlasterJdk14LoggingHandler", url.toString(), e);
      }
   }

   /**
    * Publish a <tt>LogRecord</tt>.
    * <p>
    * The logging request was made initially to a <tt>Logger</tt> object,
    * which initialized the <tt>LogRecord</tt> and forwarded it here.
    * We now redirect logging to xmlBlasters jutils logging framework.
    * <p>
    * @param  record  description of the log event. A null record is
    *                 silently ignored and is not published
    */
   public void publish(LogRecord record) {
      final LogChannel log = this.glob.getLog(record.getLoggerName());
      final String ME = record.getSourceClassName() + "." + record.getSourceMethodName();
      Level curr = record.getLevel();
      if (curr == Level.FINEST)
         log.dump(ME, record.getMessage());
      else if (curr == Level.FINER)
         log.call(ME, record.getMessage());
      else if (curr == Level.FINE)
         log.trace(ME, record.getMessage());
      else if (curr == Level.INFO)
         log.info(ME, record.getMessage());
      else if (curr == Level.WARNING)
         log.warn(ME, record.getMessage());
      else if (curr == Level.SEVERE)
         log.error(ME, record.getMessage());
      else // Log.CONFIG
         log.info(ME, record.getMessage());
   }

   public void flush() {
   }

   public void close() {
   }

   /**
    * Testing only
    * java org.xmlBlaster.util.log.XmlBlasterJdk14LoggingHandler -trace true
    */
   public static void main(String args[]) {
      try { // configure jutils as JDK 1.4 logging handler:
         Global glob = new Global(args);
         XmlBlasterJdk14LoggingHandler.initLogManager(glob);
      }
      catch (XmlBlasterException e) {
         System.err.println(e.toString());
      }

      Logger logger = Logger.getLogger("oioi");

      Handler[] handlers = logger.getHandlers();
      for (int i=0; i<handlers.length; i++) {
         System.out.println("Handler is '" + handlers[i].toString() + "'");
      }

      logger.warning("Testing");
      logger.info("Testing");
      logger.fine("Testing");
   }
}


