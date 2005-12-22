/*------------------------------------------------------------------------------
Name:      PriorityEnum.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.def;


/**
 * This class simulates an enumeration for queue priorities, 
 * it ensures that only valid priorities are used. 
 * <p>
 * Note that this implementation has a fixed number of priorities, namely from 0 (lowest) to 9 (highest).
 * For performance reasons we have not used an interface in the queue implementation to hide
 * this priority implementation (calling a method through an interface takes 20 CPU cycles instead of 2).
 * </p>
 * <p>
 * If you need a different set of priorities, replace this implementation or change it to
 * support an adjustable amount of priorities.
 * </p>
 * @author xmlBlaster@marcelruff.info
 */
public final class PriorityEnum implements java.io.Serializable
{
   private static final long serialVersionUID = -7570828192018997712L;

   private final int priority;

   private PriorityEnum(int priority) {
      this.priority = priority;
   }

   private PriorityEnum() {
      this.priority = -1;
      System.out.println("DEFAULT PriorityEnum " + toString() + " hashCode=" + this.hashCode());
   }

   /**
    * Return a human readable string of the priority
    */
   public String toString() {
      switch(this.priority) {
         case 0: return "MIN";
         case 3: return "LOW";
         case 5: return "NORM";
         case 7: return "HIGH";
         case 9: return "MAX";
         default: return ""+this.priority;
      }
   }

   /**
    * Returns the int representation of this priority
    */
   public final int getInt() {
      return priority;
   }

   /**
    * Returns the Integer representation of this priority
    */
   public final Integer getInteger() {
      return integerArr[priority];
   }

   /**
    * Checks the given int and returns the corresponding PriorityEnum instance. 
    * @param prio For example 7
    * @return The enumeration object for this priority
    * @exception IllegalArgumentException if the given priority is invalid
    */
   public static final PriorityEnum toPriorityEnum(int priority) throws IllegalArgumentException {
      if (priority < 0 || priority > 9) {
         throw new IllegalArgumentException("PriorityEnum: The given priority=" + priority + " is illegal");
      }
      return priorityEnumArr[priority];
   }

   /**
    * Parses given string to extract the priority of a message
    * @param prio For example "HIGH" or 7
    * @return The PriorityEnum instance for the message priority
    * @exception IllegalArgumentException if the given priority is invalid
    */
   public static final PriorityEnum parsePriority(String prio) throws IllegalArgumentException {
      if (prio == null) {
         throw new IllegalArgumentException("PriorityEnum: Given priority is null");
      }
      prio = prio.trim();
      try {
         int priority = new Integer(prio).intValue();
         return toPriorityEnum(priority); // may throw IllegalArgumentException
      } catch (NumberFormatException e) {
         prio = prio.toUpperCase();
         if (prio.startsWith("MIN"))
            return MIN_PRIORITY;
         else if (prio.startsWith("LOW"))
            return LOW_PRIORITY;
         else if (prio.startsWith("NORM"))
            return NORM_PRIORITY;
         else if (prio.startsWith("HIGH"))
            return HIGH_PRIORITY;
         else if (prio.startsWith("MAX"))
            return MAX_PRIORITY;
      }
      throw new IllegalArgumentException("PriorityEnum:  Wrong format of <priority>" + prio +
                    "</priority>, expected one of MIN|LOW|NORM|HIGH|MAX");
   }

   /**
    * Parses given string to extract the priority of a message
    * @param prio For example "HIGH" or 7
    * @param defaultPriority Value to use if not parsable
    * @return The PriorityEnum instance for the message priority
    */
   public static final PriorityEnum parsePriority(String priority, PriorityEnum defaultPriority) {
      try {
         return parsePriority(priority);
      }
      catch (IllegalArgumentException e) {
         System.out.println("PriorityEnum: " + e.toString() + ": Setting priority to " + defaultPriority);
         return defaultPriority;
      }
   }

   /**
    * The minimum priority of a message (0 or MIN).
    */
   public static final PriorityEnum MIN_PRIORITY = new PriorityEnum(0);

   /**
    * The minimum priority of a message (1).
    */
   public static final PriorityEnum MIN1_PRIORITY = new PriorityEnum(1);

   /**
    * The minimum priority of a message (2).
    */
   public static final PriorityEnum MIN2_PRIORITY = new PriorityEnum(2);

   /**
    * The lower priority of a message (3 or LOW).
    */
   public static final PriorityEnum LOW_PRIORITY = new PriorityEnum(3);

   /**
    * The lower priority of a message (4).
    */
   public static final PriorityEnum LOW4_PRIORITY = new PriorityEnum(4);

   /**
    * The default priority of a message (5 or NORM).
    */
   public static final PriorityEnum NORM_PRIORITY = new PriorityEnum(5);

   /**
    * The default priority of a message (6).
    */
   public static final PriorityEnum NORM6_PRIORITY = new PriorityEnum(6);

   /**
    * The higher priority of a message (7 or HIGH).
    */
   public static final PriorityEnum HIGH_PRIORITY = new PriorityEnum(7);

   /**
    * The higher priority of a message (8).
    */
   public static final PriorityEnum HIGH8_PRIORITY = new PriorityEnum(8);

   /**
    * The maximum priority of a message (9 or MAX).
    */
   public static final PriorityEnum MAX_PRIORITY = new PriorityEnum(9);

   /**
    * For good performance have a static array of all priorities
    */
   private static final PriorityEnum[] priorityEnumArr = {
      MIN_PRIORITY,
      MIN1_PRIORITY,
      MIN2_PRIORITY,
      LOW_PRIORITY,
      LOW4_PRIORITY,
      NORM_PRIORITY,
      NORM6_PRIORITY,
      HIGH_PRIORITY,
      HIGH8_PRIORITY,
      MAX_PRIORITY
   };

   /**
    * Create Integer instances for each priority, for performance reasons only. 
    */
   private static final Integer[] integerArr = {
      new Integer(0),
      new Integer(1),
      new Integer(2),
      new Integer(3),
      new Integer(4),
      new Integer(5),
      new Integer(6),
      new Integer(7),
      new Integer(8),
      new Integer(9)
   };

   ///////////////
   // This code is a helper for serialization so that after
   // deserial the check
   //   PriortiyEnum.MAX == priorityInstance
   // is still usable (the singleton is assured when deserializing)
   public Object writeReplace() throws java.io.ObjectStreamException {
      return new SerializedForm(this.getInt());
   }
   private static class SerializedForm implements java.io.Serializable {
      private static final long serialVersionUID = -2592650624578504586L;
      int prio;
      SerializedForm(int prio) { this.prio = prio; }
      Object readResolve() throws java.io.ObjectStreamException {
         return PriorityEnum.toPriorityEnum(prio);
      }
   }
   ///////////////END

   /** java org.xmlBlaster.util.def.PriorityEnum */
   public static void main(String[] args) {
      // Verifiy serialization:
      String fileName = "PriorityEnum.ser";
      PriorityEnum pOrig = PriorityEnum.MAX_PRIORITY;
      {

         try {
            java.io.FileOutputStream f = new java.io.FileOutputStream(fileName);
            java.io.ObjectOutputStream objStream = new java.io.ObjectOutputStream(f);
            objStream.writeObject(pOrig);
            objStream.flush();
            System.out.println("SUCCESS written " + pOrig.toString());
         }
         catch (Exception e) {
            System.err.println("ERROR: " + e.toString());
         }
      }

      PriorityEnum pNew = null;
      {

         try {
            java.io.FileInputStream f = new java.io.FileInputStream(fileName);
            java.io.ObjectInputStream objStream = new java.io.ObjectInputStream(f);
            pNew = (PriorityEnum)objStream.readObject();
            System.out.println("SUCCESS loaded " + pNew.toString());
         }
         catch (Exception e) {
            System.err.println("ERROR: " + e.toString());
         }
      }

      if (pNew.toString().equals(pOrig.toString())) {
         System.out.println("SUCCESS, string form is equals " + pNew.toString());
      }
      else {
         System.out.println("ERROR, string form is different " + pNew.toString());
      }

      int hashOrig = pOrig.hashCode();
      int hashNew = pNew.hashCode();

      if (pNew == pOrig) {
         System.out.println("SUCCESS, hash is same, the objects are identical");
      }
      else {
         System.out.println("ERROR, hashCode is different hashOrig=" + hashOrig + " hashNew=" + hashNew);
      }
   }
}

