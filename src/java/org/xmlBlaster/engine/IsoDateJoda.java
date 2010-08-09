package org.xmlBlaster.engine;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.joda.time.format.ISOPeriodFormat;
import org.joda.time.format.PeriodFormatter;
import org.xmlBlaster.util.ReplaceVariable;

/**
 * Some Joda time utitility methods, depends on lib/joda-time.jar
 * http://www.w3.org/TR/1998/NOTE-datetime-19980827
 */
public class IsoDateJoda {
   
   /**
    * Calculate the difference from the given time to now. 
    * ISO 8601 states: Durations are represented by the format P[n]Y[n]M[n]DT[n]H[n]M[n]S
    * @param utc Given time, e.g. "1997-07-16T19:20:30.45+01:00"
    * @return The ISO 8601 Period like "P3Y6M4DT12H30M17S"
    */
   public static String getDifferenceToNow(String utc) {
	   if (utc == null) return "";
	   utc = ReplaceVariable.replaceAll(utc, " ", "T");
	   DateTime now = new DateTime();
	   DateTimeFormatter f = ISODateTimeFormat.dateTimeParser();
	   DateTime other = f.parseDateTime(utc);
	   Period period = new Period(other, now); // Period(ReadableInstant startInstant, ReadableInstant endInstant)
	   return period.toString();
   }

   /**
    * Calculate the difference of given millis.
    * @param diffMillis The elapsed time
    * @param trimDateIfPossible true:
    * <pre> 
    * 3000->00:00:03
    * 380000->00:06:20
    * 5692439078->1581:13:59.078
    * </pre>
    * false: 
    * <pre> 
	*   3000->P0000-00-00T00:00:03
	*   5692439078->P0000-00-00T1581:13:59.078
    * </pre>
    * 
    * @return The ISO 8601 Period like "P3Y6M4DT12H30M17S"
    */
   public static String getDifference(long diffMillis, boolean trimDateIfPossible) {
	   if (diffMillis == 0) return "";
	   Period period = new Period(diffMillis);
	   /*
	   PeriodFormatter myFormatter = new PeriodFormatterBuilder()
	     .printZeroAlways()
	     .appendYears()
	     .appendSuffix(" year", " years")
	     .appendSeparator(" and ")
	     .appendMonths()
	     .appendSuffix(" month", " months")
	     .toFormatter();
	   */
	   
	   /*if (true) */{
	//	   3000->P0000-00-00T00:00:03
	//	   5692439078->P0000-00-00T1581:13:59.078
	   PeriodFormatter formatter = ISOPeriodFormat.alternateExtended();
	   String periodStr = formatter.print(period);
	   if (trimDateIfPossible) {
		   if (periodStr.startsWith("P0000-00-00T"))
			   periodStr = periodStr.substring("P0000-00-00T".length());
	   }
	   return periodStr;
	   }
	   /*
	   else {
	   // 3000->PT3S
	   // 5692439078->PT1581H13M59.078S
	   return period.toString();
	   }
	   */
   }

    
    // java org.xmlBlaster.util.IsoDateParser
   public static void main(String args[]) {
	  {
         String utc = "1997-07-16T19:20:30.45+01:00";
         String period = getDifferenceToNow(utc);
         System.out.println("now - " + utc + " = " + period);
	  }
      {
         String utc = "1997-07-16 19:20:30.45+01:00";
         String period = getDifferenceToNow(utc);
         System.out.println("now - " + utc + " = " + period);
      }
      System.out.println("3000->" + getDifference(3000, true));
      System.out.println("380000->" + getDifference(380000, true));
      System.out.println("5692439078->" + getDifference(5692439078L, true));
   }
}

