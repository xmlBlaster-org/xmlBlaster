package org.xmlBlaster.util.log;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.log4j.Appender;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.ErrorHandler;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;

public class Log4jToJavaLoggingAppender implements Appender {

   private final static Logger log = Logger.getLogger(Log4jToJavaLoggingAppender.class.getName());
   
   private Filter filter;
   private ErrorHandler errorHandler;
   private Layout layout;
   private String name;

   
   public void addFilter(Filter filter) {
      this.filter = filter;
   }

   public void clearFilters() {
      this.filter = null;
   }

   public void close() {
   }

   private final Level mapLevel(org.apache.log4j.Level level) {
      if (level.equals(org.apache.log4j.Level.DEBUG)) {
         return Level.FINEST;
      }
      else if (level.equals(org.apache.log4j.Level.ERROR)) {
         return Level.SEVERE;
      }
      else if (level.equals(org.apache.log4j.Level.FATAL)) {
         return Level.SEVERE;
      }
      else if (level.equals(org.apache.log4j.Level.INFO)) {
         return Level.INFO;
      }
      else if (level.equals(org.apache.log4j.Level.TRACE)) {
         return Level.FINE;
      }
      else if (level.equals(org.apache.log4j.Level.WARN)) {
         return Level.WARNING;
      }
      else { // FINER is not mapped
         log.warning("The level '" + level.toString() + " is unknown");
         return Level.WARNING;
      }
   }
   
   
   public void doAppend(LoggingEvent event) {
      try {
         String loggerName = event.getLoggerName();
         String className = event.getLocationInformation().getClassName();
         String method = event.getLocationInformation().getMethodName();
         String line =  event.getLocationInformation().getLineNumber();
         org.apache.log4j.Level level = event.getLevel();
         
         Level javaLevel = mapLevel(level);
         log.logp(javaLevel, className, method + line, event.getMessage().toString());
      }
      catch (Throwable ex) {
         ex.printStackTrace();
      }
   }

   public ErrorHandler getErrorHandler() {
      return errorHandler;
   }

   public Filter getFilter() {
      return this.filter;
   }

   public Layout getLayout() {
      return this.layout;
   }

   public String getName() {
      return name;
   }

   public boolean requiresLayout() {
      return false;
   }

   public void setErrorHandler(ErrorHandler errorHandler) {
      this.errorHandler = errorHandler;
   }

   public void setLayout(Layout layout) {
      this.layout = layout;
   }

   public void setName(String name) {
      this.name = name;
   }
   
}
