// IsoDateParser.java
// $Id: IsoDateParser.java,v 1.5 2005/05/16 10:19:19 ylafon Exp $
// (c) COPYRIGHT MIT, INRIA and Keio, 2000.
// Please first read the full copyright statement in file COPYRIGHT.html
//package org.w3c.util;
package org.xmlBlaster.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.TimeZone;

/*
 * ISO 8601 date time formatting.
 * http://www.w3.org/TR/NOTE-datetime
 * http://en.wikipedia.org/wiki/ISO_8601
 * http://www.probabilityof.com/ISO8601.shtml
 * <pre>
 * YYYY-MM-DDThh:mm:ss.fTZD (eg 1997-07-16T19:20:30.45+01:00)
 * f can be one f to ffffffff (fraction of seconds)
 * 1994-11-05T13:15:30Z  (Z stands for UTC==GMT)
 * </pre>
 * If no time zone information is given with a time, the time zone is assumed to be in some conventional local timezone.
 * <br />
 * Note that the "T" appears literally in the string, to indicate the beginning of the time element, as specified in ISO 8601.
 * <br />
 * The standard allows the replacement of T with a space if no misunderstanding arises.
 * @return The ISO 8601 UTC-time string, precision is currently millis (three fraction digits)
 * "2006-02-21 14:05:51.703+0000"
 */

/**
 * Date parser for ISO 8601 format
 * http://www.w3.org/TR/1998/NOTE-datetime-19980827
 * @version $Revision: 1.5 $
 * @author  Beno�t Mah� (bmahe@w3.org)
 * @author  Yves Lafon (ylafon@w3.org)
 */
public class IsoDateParser {

   public final static SimpleDateFormat utcFmt;
   public final static SimpleDateFormat utcFmtT;
   static {
      // Creates 2006-07-16 21:20:30.450+0000: illegal , must be +00:00
      //utcFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SZ", Locale.US);
      utcFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S", Locale.US);
      utcFmt.setTimeZone(TimeZone.getTimeZone("GMT"));
      utcFmtT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S", Locale.US);
      utcFmtT.setTimeZone(TimeZone.getTimeZone("GMT"));
      //Calendar utcCal = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
   }

   /**
    * @return The ISO 8601 UTC-time string
    * "2006-02-21"
    */
   public static String getCurrentUTCDate() {
      return getUTCTimestamp(new Date()).substring(0, 10);
   }

   /**
    * @return The ISO 8601 UTC-time string
    * "2006-02-21"
    */
   public static String getCurrentUTCTime() {
      return getUTCTimestamp(new Date()).substring(11);
   }

   /**
    * @return The ISO 8601 UTC-time string, precision is currently millis (three fraction digits)
    * "2006-02-21 14:05:51.703Z"
    */
   public static String getCurrentUTCTimestamp() {
      return getUTCTimestamp(new Date());
   }

   /**
    * Returns currently milli precission or an increment counter if called more than once per milli.
    * @return e.g. "2007-07-09 10:35:43.946Z" or "2007-07-09 10:36:38.180000005Z"
    */
   public static String getCurrentUTCTimestampNanos() {
		//Timestamp ts = new Timestamp(1183977398180000005L); // org.xmlBlaster.util.Timestamp
		Timestamp ts = new Timestamp();
		String tmp = getUTCTimestamp(ts.getMillis());
		int i = tmp.indexOf(".");
		if (i<0) return tmp;
		if ((ts.getNanosOnly() % 1000) > 0) {
			tmp = tmp.substring(0,i);
			tmp += "."+ts.getNanosOnly();
			tmp += "Z";
		}
	    return tmp;
   }

   public static String getUTCTimestamp(Date date) {
      synchronized (utcFmt) {
         return utcFmt.format(date)+"Z";
      }
   }

   public static String getUTCTimestamp(long millis) {
      return IsoDateParser.getUTCTimestamp(new Date(millis));
   }

   /**
    * @return The ISO 8601 UTC-time string, precision is currently millis (three fraction digits)
    * "2006-02-21T14:05:51.703Z"
    */
   public static String getCurrentUTCTimestampT() {
      return getUTCTimestampT(new Date());
   }

   public static String getUTCTimestampT(Date date) {
      synchronized (utcFmtT) {
         return utcFmtT.format(date)+"Z";
      }
   }
   
// See IsoDateJoda (our IsoDateParser shall not have joda dependency to keep clients slim)   
//   public static String getDifferenceToNow(String utc) {
//	   if (utc == null) return "";
//	   utc = ReplaceVariable.replaceAll(utc, " ", "T");
//	   DateTime now = new DateTime();
//	   DateTimeFormatter f = ISODateTimeFormat.dateTimeParser();
//	   DateTime other = f.parseDateTime(utc);
//	   Period period = new Period(other, now); // Period(ReadableInstant startInstant, ReadableInstant endInstant)
//	   return period.toString();
//   }
//
//   public static String getDifference(long diffMillis, boolean trimDateIfPossible) {
//	   if (diffMillis == 0) return "";
//	   Period period = new Period(diffMillis);
//	   
//	   /*if (true) */{
//	//	   3000->P0000-00-00T00:00:03
//	//	   5692439078->P0000-00-00T1581:13:59.078
//	   PeriodFormatter formatter = ISOPeriodFormat.alternateExtended();
//	   String periodStr = formatter.print(period);
//	   if (trimDateIfPossible) {
//		   if (periodStr.startsWith("P0000-00-00T"))
//			   periodStr = periodStr.substring("P0000-00-00T".length());
//	   }
//	   return periodStr;
//	   }
//   }

   /**
    * @param utc "2001-02-03 04:05:06.7"
   public static Date getParseUTC(String utc) {
      synchronized (utcFmt) {
         try {
            return utcFmt.parse(utc);
         } catch (ParseException e) {
            throw new IllegalArgumentException("getParseUTC("+utc+"): " + e.toString());
         }
      }
   }
    */

    private static boolean check(StringTokenizer st, String token)
   throws IllegalArgumentException
    {
   try {
       if (st.nextToken().equals(token)) {
      return true;
       } else {
      throw new IllegalArgumentException("Missing ["+token+"]");
       }
   } catch (NoSuchElementException ex) {
       return false;
   }
    }

    public static Calendar getCalendar(String isodate)
    	throws IllegalArgumentException
    {
   // YYYY-MM-DDThh:mm:ss.sTZD
       // Marcel: Added ' ' to T replacement (ISO allows blanks)
   int index = isodate.indexOf(' ');
   if (index > 0)
      isodate = isodate.substring(0, index) + "T" + isodate.substring(index+1);
   StringTokenizer st = new StringTokenizer(isodate, "-T:.+Z", true);

   Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
   calendar.clear();
   try {
       // Year
       if (st.hasMoreTokens()) {
      int year = Integer.parseInt(st.nextToken());
      calendar.set(Calendar.YEAR, year);
       } else {
      return calendar;
       }
       // Month
       if (check(st, "-") && (st.hasMoreTokens())) {
      int month = Integer.parseInt(st.nextToken()) -1;
      calendar.set(Calendar.MONTH, month);
       } else {
      return calendar;
       }
       // Day
       if (check(st, "-") && (st.hasMoreTokens())) {
      int day = Integer.parseInt(st.nextToken());
      calendar.set(Calendar.DAY_OF_MONTH, day);
       } else {
      return calendar;
       }
       // Hour
       if (check(st, "T") && (st.hasMoreTokens())) {
      int hour = Integer.parseInt(st.nextToken());
      calendar.set(Calendar.HOUR_OF_DAY, hour);
       } else {
      calendar.set(Calendar.HOUR_OF_DAY, 0);
      calendar.set(Calendar.MINUTE, 0);
      calendar.set(Calendar.SECOND, 0);
      calendar.set(Calendar.MILLISECOND, 0);
      return calendar;
       }
       // Minutes
       if (check(st, ":") && (st.hasMoreTokens())) {
      int minutes = Integer.parseInt(st.nextToken());
      calendar.set(Calendar.MINUTE, minutes);
       } else {
      calendar.set(Calendar.MINUTE, 0);
      calendar.set(Calendar.SECOND, 0);
      calendar.set(Calendar.MILLISECOND, 0);
      return calendar;
       }

       //
       // Not mandatory now
       //

       // Secondes
       if (! st.hasMoreTokens()) {
      return calendar;
       }
       String tok = st.nextToken();
       if (tok.equals(":")) { // secondes
      if (st.hasMoreTokens()) {
          int secondes = Integer.parseInt(st.nextToken());
          calendar.set(Calendar.SECOND, secondes);
          if (! st.hasMoreTokens()) {
         return calendar;
          }
          // frac sec
          tok = st.nextToken();
          if (tok.equals(".")) {
         // bug fixed, thx to Martin Bottcher
         String nt = st.nextToken();
         while(nt.length() < 3) {
             nt += "0";
         }
         nt = nt.substring( 0, 3 ); //Cut trailing chars..
         int millisec = Integer.parseInt(nt);
         //int millisec = Integer.parseInt(st.nextToken()) * 10;
         calendar.set(Calendar.MILLISECOND, millisec);
         if (! st.hasMoreTokens()) {
             return calendar;
         }
         tok = st.nextToken();
          } else {
         calendar.set(Calendar.MILLISECOND, 0);
          }
      } else {
          throw new IllegalArgumentException("No secondes specified");
      }
       } else {
      calendar.set(Calendar.SECOND, 0);
      calendar.set(Calendar.MILLISECOND, 0);
       }
       // Timezone
       if (! tok.equals("Z")) { // UTC
      if (! (tok.equals("+") || tok.equals("-"))) {
          throw new IllegalArgumentException("only Z, + or - allowed");
      }
      boolean plus = tok.equals("+");
      if (! st.hasMoreTokens()) {
          throw new IllegalArgumentException("Missing hour field");
      }
      int tzhour = Integer.parseInt(st.nextToken());
      int tzmin  = 0;
      if (check(st, ":") && (st.hasMoreTokens())) {
          tzmin = Integer.parseInt(st.nextToken());
      } else {
          throw new IllegalArgumentException("Missing minute field");
      }
      if (plus) {
          calendar.add(Calendar.HOUR, -tzhour);
          calendar.add(Calendar.MINUTE, -tzmin);
      } else {
          calendar.add(Calendar.HOUR, tzhour);
          calendar.add(Calendar.MINUTE, tzmin);
      }
       }
   } catch (NumberFormatException ex) {
       throw new IllegalArgumentException("["+ex.getMessage()+
                  "] is not an integer");
   }
   return calendar;
    }

    /**
     * Parse the given string in ISO 8601 format and build a Date object.
     * @param isodate the date in ISO 8601 format
     * @return a Date instance
     * @exception IllegalArgumentException if the date is not valid
     */
    public static Date parse(String isodate)
    	throws IllegalArgumentException
    {
   Calendar calendar = getCalendar(isodate);
   return calendar.getTime();
    }

    private static String twoDigit(int i) {
   if (i >=0 && i < 10) {
       return "0"+String.valueOf(i);
   }
   return String.valueOf(i);
    }

    /**
     * Generate a ISO 8601 date
     * @param date a Date instance
     * @return a string representing the date in the ISO 8601 format
     */
    public static String getIsoDate(Date date) {
   Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
   calendar.setTime(date);
   StringBuffer buffer = new StringBuffer();
   buffer.append(calendar.get(Calendar.YEAR));
   buffer.append("-");
   buffer.append(twoDigit(calendar.get(Calendar.MONTH) + 1));
   buffer.append("-");
   buffer.append(twoDigit(calendar.get(Calendar.DAY_OF_MONTH)));
   buffer.append("T");
   buffer.append(twoDigit(calendar.get(Calendar.HOUR_OF_DAY)));
   buffer.append(":");
   buffer.append(twoDigit(calendar.get(Calendar.MINUTE)));
   buffer.append(":");
   buffer.append(twoDigit(calendar.get(Calendar.SECOND)));
   buffer.append(".");
   buffer.append(twoDigit(calendar.get(Calendar.MILLISECOND) / 10));
   buffer.append("Z");
   return buffer.toString();
    }

    /**
     * Generate a ISO 8601 date
     * @param date a Date instance
     * @return a string representing the date in the ISO 8601 format
     */
    public static String getIsoDateNoMillis(Date date) {
   Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
   calendar.setTime(date);
   StringBuffer buffer = new StringBuffer();
   buffer.append(calendar.get(Calendar.YEAR));
   buffer.append("-");
   buffer.append(twoDigit(calendar.get(Calendar.MONTH) + 1));
   buffer.append("-");
   buffer.append(twoDigit(calendar.get(Calendar.DAY_OF_MONTH)));
   buffer.append("T");
   buffer.append(twoDigit(calendar.get(Calendar.HOUR_OF_DAY)));
   buffer.append(":");
   buffer.append(twoDigit(calendar.get(Calendar.MINUTE)));
   buffer.append(":");
   buffer.append(twoDigit(calendar.get(Calendar.SECOND)));
   buffer.append("Z");
   return buffer.toString();
    }

    public static void test(String isodate) {
   System.out.println("----------------------------------");
   try {
       Date date = parse(isodate);
       System.out.println("GIVEN >> "+isodate);
       System.out.println("LOCAL >> "+date.toString()+" ["+date.getTime()+"]");
       System.out.println("UTC   >> "+ getUTCTimestamp(date));
       System.out.println("UTCT  >> "+ getUTCTimestampT(date));
       System.out.println("ISO   >> "+getIsoDate(date));
   } catch (IllegalArgumentException ex) {
       System.err.println(isodate+" is invalid");
       System.err.println(ex.getMessage());
   }
   System.out.println("----------------------------------");
    }

    public static void test(Date date) {
   String isodate = null;
   System.out.println("----------------------------------");
   try {
       System.out.println("GIVEN >> "+date.toString()+" ["+date.getTime()+"]");
       isodate = getIsoDate(date);
       System.out.println("ISO   >> "+isodate);
       date = parse(isodate);
       System.out.println("PARSED>> "+date.toString()+" ["+date.getTime()+"]");
   } catch (IllegalArgumentException ex) {
       System.err.println(isodate+" is invalid");
       System.err.println(ex.getMessage());
   }
   System.out.println("----------------------------------");
    }

    // java org.xmlBlaster.util.IsoDateParser
   public static void main(String args[]) {
      test("1997-07-16T19:20:30.45-02:00");
      test("1997-07-16T19:20:30.678Z");
      test("2006-07-16T21:20:30.450+00:00");
      //test("2006-07-16T21:20:30.450+0000"); invalid

      test("1997-07-16 19:20:30.45-02:00");
      test("1997-07-16 19:20:30.678Z");
      test("2006-07-16 21:20:30.450+00:00");

      test("1997-07-16T19:20:30+01:00");
      test("1997-07-16T19:20:30+01:00");
      test("1997-07-16T12:20:30-06:00");
      test("1997-07-16T19:20");
      test("1997-07-16");
      test("1997-07");
      test("1997");
      test(new Date());
   }
}

