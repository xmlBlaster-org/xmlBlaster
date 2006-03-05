/**
 * 
 */
package org.xmlBlaster.util.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * A xmlBlaster specific formatter for java.util.logging
 * 
 * @author marcel
 */
public class XbFormatter extends Formatter {
   private final String id;

   private static long instanceCounter;

   /** Colored output to xterm */
   private boolean withXtermEscapeColor = false;

   private Date dat = new Date();

   private final static String format = "{0,date} {0,time}";

   private MessageFormat formatter;

   private Object args[] = new Object[1];

   private String lineSeparator;

   /** Reset color to original values */
   private static final String ESC = "\033[0m";

   // private static final String BOLD = "\033[1m";

   /** color foreground/background (for xterm) */
   private static final String RED_BLACK = "\033[31;40m";

   private static final String GREEN_BLACK = "\033[32;40m";

   private static final String YELLOW_BLACK = "\033[33;40m";

   // private static final String BLUE_BLACK = "\033[34;40m";
   private static final String PINK_BLACK = "\033[35;40m";

   private static final String LTGREEN_BLACK = "\033[36;40m";

   private static final String WHITE_BLACK = "\033[37;40m";

   // private static final String WHITE_RED = "\033[37;41m";
   // private static final String BLACK_RED = "\033[30;41m";
   // private static final String BLACK_GREEN = "\033[40;42m";
   // private static final String BLACK_PINK = "\033[40;45m";
   // private static final String BLACK_LTGREEN= "\033[40;46m";

   private final String severe;

   private final String warning;

   private final String info;

   private final String fine;

   private final String finer;

   private final String finest;

   /** Output text for different logging levels */
   public static final String severeX = new String("SEVERE");

   public static final String warningX = new String("WARN  ");

   public static final String infoX = new String("INFO  ");

   public static final String fineX = new String("FINE  ");

   public static final String finerX = new String("FINER ");

   public static final String finestX = new String("FINEST");

   public static final String severeE = new String(RED_BLACK + "SEVERE" + ESC);

   public static final String warningE = new String(YELLOW_BLACK + "WARN  "
         + ESC);

   public static final String infoE = new String(GREEN_BLACK + "INFO  " + ESC);

   public static final String fineE = new String(LTGREEN_BLACK + "FINE  " + ESC);

   public static final String finerE = new String(PINK_BLACK + "FINER " + ESC);

   public static final String finestE = new String(WHITE_BLACK + "TRACE " + ESC);

   public XbFormatter() {
      this("default");
   }

   public XbFormatter(String id) {
      this.id = id + "-" + instanceCounter++;
      String osName = System.getProperty("os.name"); // "Linux" "Windows NT"...
      if (osName == null || osName.startsWith("Window")) {
         this.withXtermEscapeColor = false;
         this.severe = severeX;
         this.warning = warningX;
         this.info = infoX;
         this.fine = fineX;
         this.finer = finerX;
         this.finest = finestX;
      } else {
         this.withXtermEscapeColor = true;
         this.severe = severeE;
         this.warning = warningE;
         this.info = infoE;
         this.fine = fineE;
         this.finer = finerE;
         this.finest = finestE;
      }
      this.lineSeparator = System.getProperty("line.separator", "\n");
   }

   public String convertLevelToString(int level) {
      if (level == Level.INFO.intValue())
         return info;
      else if (level == Level.WARNING.intValue())
         return warning;
      else if (level == Level.SEVERE.intValue())
         return severe;
      else if (level == Level.FINE.intValue())
         return fine;
      else if (level == Level.FINER.intValue())
         return finer;
      else if (level == Level.FINEST.intValue())
         return finest;
      else
         return "LEVEL" + level;
   }

   /*
    * (non-Javadoc)
    * 
    * @see java.util.logging.Formatter#format(java.util.logging.LogRecord)
    */
   public String format(LogRecord record) {
      if (record == null) {
         return "";
      }
      String levelString = convertLevelToString(record.getLevel().intValue());
      StringBuffer sb = new StringBuffer();
      // Minimize memory allocations here.
      dat.setTime(record.getMillis());
      args[0] = dat;
      StringBuffer text = new StringBuffer();
      if (formatter == null) {
         formatter = new MessageFormat(format);
      }
      formatter.format(args, text, null);
      sb.append(text);
      sb.append(" ");
      sb.append(levelString);//record.getLevel().getLocalizedName());
      sb.append(" ");
      if (record.getThreadID() > 0) {
         sb.append(""+record.getThreadID()).append("-");
         sb.append(Thread.currentThread().getName());
         sb.append(" ");
      }
      if (record.getSourceClassName() != null) {
         sb.append(record.getSourceClassName());
      } else {
         sb.append(record.getLoggerName());
      }
      if (record.getSourceMethodName() != null) {
         sb.append(" ");
         sb.append(record.getSourceMethodName());
      }
      String message = formatMessage(record);
      sb.append(": ");
      sb.append(message);
      sb.append(lineSeparator);
      if (record.getThrown() != null) {
         try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            record.getThrown().printStackTrace(pw);
            pw.close();
            sb.append(sw.toString());
         } catch (Exception ex) {
         }
      }
      return sb.toString();
   }

   /**
    * @return Returns the id.
    */
   public String getId() {
      return this.id;
   }

   /**
    * @return Returns the withXtermEscapeColor.
    */
   public boolean isWithXtermEscapeColor() {
      return this.withXtermEscapeColor;
   }

   /**
    * @param args
    */
   public static void main(String[] args) {
   }
}
