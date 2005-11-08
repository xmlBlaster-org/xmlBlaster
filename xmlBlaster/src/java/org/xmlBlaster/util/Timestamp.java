/*------------------------------------------------------------------------------
Name:      Timestamp.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Create unique timestamp
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

/**
 * High performing timestamp class, time elapsed since 1970, the nanos are simulated
 * as a unique counter. 
 * <br />
 * The counter is rewound on any millisecond step.
 * <br />
 * Timestamp objects are immutable - a Timstamp can not be changed once it is created.
 * <br />
 * Guarantees that any created Timestamp instance is unique in the current
 * Java Virtual Machine (and Classloader).
 * <br /><br />
 * Fails only if
 * <ul>
 *   <li>a CPU can create more than 999999 Timestamp instances per millisecond</li>
 *   <li>In ~ 288 years when Long.MAX_VALUE = 9223372036854775807 overflows (current value is 1013338358124000008)</li>
 * </ul>
 * A typical output is:<br />
 * <ul>
 *   <li>toString()=2002-02-10 11:57:51.804000001</li>
 *   <li>getTimestamp()=1013338671804000001</li>
 *   <li>getMillis()=1013338671804</li>
 *   <li>getMillisOnly()=804</li>
 *   <li>getNanosOnly()=804000001</li>
 * </ul>
 * Performance hints (600 MHz Intel PC, Linux 2.4.10, JDK 1.3.1):
 * <br />
 * <ul>
 *   <li>new Timestamp()  1.2 micro seconds</li>
 *   <li>toString()       55 micro seconds the first time, further access 0.1 micro seconds</li>
 *   <li>valueOf()        19 micro seconds</li>
 *   <li>toXml("", false) 16 micro seconds</li>
 *   <li>toXml("", true)  17 micro seconds</li>
 * </ul>
 * XML representation:
 * <pre>
 *  &lt;timestamp nanos='1013346248150000001'>
 *     2002-02-10 14:04:08.150000001
 *  &lt;/timestamp>
 * </pre>
 * or
 * <pre>
 *  &lt;timestamp nanos='1013346248150000001'/>
 * </pre>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see org.xmlBlaster.test.classtest.TimestampTest
 */
public class Timestamp implements Comparable, java.io.Serializable
{
   private static final long serialVersionUID = 1L;
   public static final int MILLION = 1000000;
   public static final int BILLION = 1000000000;
   //private static Object SYNCER = new Object();
   private static int nanoCounter = 0;
   private static long lastMillis = 0L;
   
   /** The timestamp in nanoseconds */
   private final long timestamp;
   private transient Long timestampLong; // cached for Long retrieval

   /** Cache for string representation */
   private transient String strFormat = null;

   /** You may overwrite the tag name for XML dumps in derived classes, defaults to &lt;timestamp ... */
   protected String tagName = "timestamp";

   /**
    * Constructs a current timestamp which is guaranteed to be unique in time for this JVM
    * @exception RuntimeException on overflow (never happens :-=)
    */
   public Timestamp() {
      synchronized (Timestamp.class) {
         long timeMillis = System.currentTimeMillis();
         if (lastMillis < timeMillis) {
            nanoCounter = 0; // rewind counter
            lastMillis = timeMillis;
            this.timestamp = timeMillis*MILLION;
            return;
         }
         else if (lastMillis == timeMillis) {
            nanoCounter++;
            if (nanoCounter >= MILLION)
               throw new RuntimeException("org.xmlBlaster.util.Timestamp nanoCounter overflow - internal error");
            this.timestamp = timeMillis*MILLION + nanoCounter;
            return;
         }
         else { // Time goes backwards - this should not happen
            // NOTE from ruff 2002/12: This happens on my DELL notebook once and again (jumps to past time with 2-3 msec).
            // CAUTION: If a sysadmin changes time of the server hardware for say 1 hour backwards
            // The server should run without day time saving with e.g. GMT
            nanoCounter++;
            if (nanoCounter >= MILLION) {
               throw new RuntimeException("org.xmlBlaster.util.Timestamp nanoCounter overflow - the system time seems to go back into past, giving up after " + MILLION + " times: System.currentTimeMillis() is not ascending old=" + lastMillis + " new=" + timeMillis);
            }
            this.timestamp = lastMillis*MILLION + nanoCounter;
            System.err.println("WARNING: org.xmlBlaster.util.Timestamp System.currentTimeMillis() is not ascending old=" +
                   lastMillis + " new=" + timeMillis + " created timestamp=" + this.timestamp);
            return;
         }
      }
   }

   /**
    * Create a Timestamp with given nanoseconds since 1970
    * @see java.util.Date
    */
   public Timestamp(long nanos) {
      this.timestamp = nanos;
   }

   /**
    * @return The exact timestamp in nanoseconds
    */
   public final long getTimestamp() {
      return timestamp;
   }

   /**
    * We cache a Long object for reuse (helpful when used as a key in a map). 
    * @return The exact timestamp in nanoseconds
    */
   public final Long getTimestampLong() {
      if (this.timestampLong == null) {
         this.timestampLong = new Long(this.timestamp);
      }
      return timestampLong;
   }

   /**
    * The nano part only
    * @return The nano part only
    */
   public final int getNanosOnly() {
      return (int)(timestamp % BILLION);
   }

   /**
    * The milli part only
    * @return The milli part only
    */
   public final int getMillisOnly() {
      return getNanosOnly() / MILLION;
   }

   /**
    * You can use this value for java.util.Date(millis)
    * @return Rounded to millis
    * @see #getTime
    * @see java.util.Date
    */
   public final long getMillis() {
      return timestamp / MILLION;
   }

   /**
    * You can use this value for java.util.Date(millis)
    * @return Rounded to millis
    * @see #getMillis
    * @see java.util.Date
    */
   public final long getTime() {
      return getMillis();
   }

   /**
    * Timestamp in JDBC Timestamp escape format (human readable). 
    * @return The Timestamp in JDBC Timestamp escape format: "2002-02-10 10:52:40.879456789"
    */
   public String toString() {
      if (strFormat == null) {
         java.sql.Timestamp ts = new java.sql.Timestamp(getMillis());
         //if (ts.getTime() != getMillis()) {
         //   System.out.println("PANIC:java.sql.Timestamp failes: sqlMillis=" + ts.getTime() + " givenMillis=" + getMillis()); 
         //}
         ts.setNanos(getNanosOnly());
         //System.out.println("ts.getTime=" + ts.getTime() + " givenMillis=" + getMillis() + " nanos=" + getNanosOnly()); 
         strFormat = ts.toString();
      }
      return strFormat;
   }

   /**
    * Converts a <code>String</code> object in JDBC timestamp escape format to a
    * <code>Timestamp</code> value.
    *
    * @param s timestamp in format <code>yyyy-mm-dd hh:mm:ss.fffffffff</code>
    * @return corresponding <code>Timestamp</code> value
    * @exception java.lang.IllegalArgumentException if the given argument
    * does not have the format <code>yyyy-mm-dd hh:mm:ss.fffffffff</code>
    */
   public static Timestamp valueOf(String s) {
      java.sql.Timestamp tsSql = java.sql.Timestamp.valueOf(s);
      return new Timestamp(((tsSql.getTime()/1000L)*1000L) * MILLION + tsSql.getNanos());
   }

   /**
    * Compares two Timestamps for ordering.
    *
    * @param   ts The <code>Timestamp</code> to be compared.
    * @return  the value <code>0</code> if the argument Timestamp is equal to
    *          this Timestamp; a value less than <code>0</code> if this 
    *          Timestamp is before the Timestamp argument; and a value greater than
    *           <code>0</code> if this Timestamp is after the Timestamp argument.
    */
   public int compareTo(Object obj) {
      Timestamp ts = (Timestamp)obj;
      if (timestamp > ts.getTimestamp())
         return 1;
      else if (timestamp < ts.getTimestamp())
         return -1;
      else
         return 0;
   }

   /**
    * Tests to see if this <code>Timestamp</code> object is
    * equal to the given <code>Timestamp</code> object.
    *
    * @param stamp the <code>Timestamp</code> value to compare with
    * @return <code>true</code> if the given <code>Timestamp</code>
    *         object is equal to this <code>Timestamp</code> object;
    *         <code>false</code> otherwise
    */
   public boolean equals(Timestamp ts) {
      return timestamp == ts.getTimestamp();
   }


    /**
     * Computes a hashcode for this Long. The result is the exclusive 
     * OR of the two halves of the primitive <code>long</code> value 
     * represented by this <code>Long</code> object. That is, the hashcode 
     * is the value of the expression: 
     * <blockquote><pre>
     * (int)(this.longValue()^(this.longValue()>>>32))
     * </pre></blockquote>
     *
     * @return  a hash code value for this object.
     */
    /*
    public int hashCode() {
        return (int)(timestamp ^ (timestamp >> 32));
    }
    */

    /**
     * Compares this object against the specified object.
     * The result is <code>true</code> if and only if the argument is
     * not <code>null</code> and is a <code>Long</code> object that
     * contains the same <code>long</code> value as this object.
     *
     * @param   obj   the object to compare with.
     * @return  <code>true</code> if the objects are the same;
     *          <code>false</code> otherwise.
     */
    public boolean equals(Object obj) {
        if (obj instanceof Timestamp) {
            return equals((Timestamp)obj);
        }
        return false;
    }

   /**
    * @return internal state of the Timestamp as an XML ASCII string
    */
   public final String toXml() {
      return toXml((String)null);
   }
   /**
    * @return internal state of the Timestamp as an XML ASCII string
    * without human readable JDBC formatting
    */
   public final String toXml(String extraOffset) {
      return toXml(extraOffset, false);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @param literal true -> show human readable format as well (JDBC escape format)
    *               "2002-02-10 10:52:40.879456789"
    * @return internal state of the Timestamp as a XML ASCII string
    */
   public final String toXml(String extraOffset, boolean literal)
   {
      StringBuffer sb = new StringBuffer(200);
      String offset = "\n ";
      if (extraOffset != null)
         offset += extraOffset;
      if (literal) {
         sb.append(offset).append("<").append(tagName).append(" nanos='").append(getTimestamp()).append("'>");
         sb.append(offset).append(" ").append(toString());
         sb.append(offset).append("</").append(tagName).append(">");
      }
      else {
         sb.append(offset).append("<").append(tagName).append(" nanos='").append(getTimestamp()).append("'/>");
      }
      return sb.toString();
   }



   /**
    * Test only. 
    * <pre>
    * javac -g -d $XMLBLASTER_HOME/classes Timestamp.java
    *
    *   java org.xmlBlaster.util.Timestamp
    *
    * Dump a nanosecond 'long' to a string representation:
    *    
    *   java org.xmlBlaster.util.Timestamp 1076677832527000001
    *
    * Dump a a string representation to a nanosecond 'long':
    *
    *   java org.xmlBlaster.util.Timestamp "2004-02-13 14:10:32.527000001"
    * </pre>
    */
   public static void main(String[] args) {

      if (args.length > 0) {
         if (args[0].indexOf(":") != -1) {
            // 2004-02-13 14:10:32.527000001
            Timestamp tt = Timestamp.valueOf(args[0]);
            System.out.println(tt.toXml("", true));
         }
         else {
            long nanos = Long.valueOf(args[0]).longValue();
            Timestamp tt = new Timestamp(nanos);
            System.out.println(tt.toXml("", true));
         }
         System.exit(0);
      }

      int count = 5;
      StringBuffer buf = new StringBuffer(count * 120);
      for (int ii=0; ii<count; ii++)
         test(buf);
      System.out.println(buf);

      testToString();
      testToString();
      testToString();

      System.out.println("TEST 1");
      
      testValueOf();
      testValueOf();
      testValueOf();

      System.out.println("TEST 2");

      testToXml(false);
      testToXml(false);
      testToXml(false);

      System.out.println("TEST 3");

      testToXml(true);
      testToXml(true);
      testToXml(true);

      Timestamp ts1 = new Timestamp();
      Timestamp ts2 = new Timestamp();
      if (ts1.equals(ts2))
         System.out.println("ERROR: equals()");
      if (ts2.compareTo(ts1) < 1)
         System.out.println("ERROR: compareTo()");
      if (ts2.toString().equals(Timestamp.valueOf(ts2.toString()).toString()) == false)
         System.out.println("ERROR: valueOf() ts2.toString()=" + ts2.toString() + " Timestamp.valueOf(ts2.toString()).toString()=" + Timestamp.valueOf(ts2.toString()).toString());

      System.out.println(ts2.toXml(""));
      System.out.println(ts2.toXml("", true));
   }
   /** Test only */
   private static final Timestamp test(StringBuffer buf) {
      Timestamp ts = new Timestamp();
      buf.append("Timestamp toString()=" + ts.toString() + 
                 " getTimestamp()=" + ts.getTimestamp() + 
                 " getMillis()=" + ts.getMillis() + 
                 " getMillisOnly()=" + ts.getMillisOnly() + 
                 " getNanosOnly()=" + ts.getNanosOnly() +
                 " getScannedAndDumped()=" + Timestamp.valueOf(ts.toString()).toString() +
                 "\n");
      return ts;
   }
   /** Test only */
   private static final void testToString()
   {
      int count = 10000;
      long start = System.currentTimeMillis();
      Timestamp ts = new Timestamp();
      for (int ii=0; ii<count; ii++) {
         ts.toString();
      }
      long elapsed = System.currentTimeMillis() - start;
      System.out.println("toString(): " + count + " toString " + elapsed + " millisec -> " + ((((double)elapsed)*1000.*1000.)/((double)count)) + " nanosec/toString()");
   }
   /** Test only */
   private static final void testValueOf()
   {
      int count = 10000;
      Timestamp ts1 = new Timestamp();
      String val = ts1.toString();
      long start = System.currentTimeMillis();
      for (int ii=0; ii<count; ii++) {
         Timestamp.valueOf(val);
      }
      long elapsed = System.currentTimeMillis() - start;
      System.out.println("valueOf(): " + count + " valueOf " + elapsed + " millisec -> " + ((((double)elapsed)*1000.*1000.)/((double)count)) + " nanosec/valueOf()");
   }
   /** Test only */
   private static final void testToXml(boolean literal)
   {
      int count = 10000;
      long start = System.currentTimeMillis();
      Timestamp ts = new Timestamp();
      for (int ii=0; ii<count; ii++) {
         ts.toXml(null, literal);
      }
      long elapsed = System.currentTimeMillis() - start;
      System.out.println("toXml(" + literal + "): " + count + " toXml " + elapsed + " millisec -> " + ((((double)elapsed)*1000.*1000.)/((double)count)) + " nanosec/toXml()");
   }
}


