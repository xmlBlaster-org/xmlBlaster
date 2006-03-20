package org.xmlBlaster.util.property;

import java.io.NotSerializableException;

/**
 * The immutable event object when a property was created or has changed. 
 * @author Marcel Ruff
 * @see testsuite.TestProperty
 */
public class PropertyChangeEvent extends java.util.EventObject
{
   private static final long serialVersionUID = 1L;
   private String key;
   private String oldValue;
   private String newValue;

   /**
    * Constructs a new <code>PropertyChangeEvent</code> instance.
    *
    * @param key     The property key
    * @param oldValue The old value
    * @param newValue The new or changed value
    */
   public PropertyChangeEvent(String key, String oldValue, String newValue) {
       super(key);
       this.key = key;
       this.oldValue = oldValue;
       this.newValue = newValue;
   }

   /**
    * The unique key of the property
    */
   public String getKey() {
      return this.key;
   }

   /**
    * The previous value of this property
    */
   public String getOldValue() {
      return this.oldValue;
   }

   /**
    * The new value of this property
    */
   public String getNewValue() {
      return this.newValue;
   }

   public String toXml() {
      StringBuffer buf = new StringBuffer();
      buf.append("<property key='").append(key).append("'>");
      buf.append("  <old>").append(oldValue).append("</old>");
      buf.append("  <new>").append(newValue).append("</new>");
      buf.append("</property>");
      return buf.toString();
   }

   public String toString() {
      return key + "=" + newValue + " [old=" + oldValue + "]";
   }

   /**
    * Throws NotSerializableException, since PropertyChangeEvent objects are not
    * intended to be serializable.
    */
    private void writeObject(java.io.ObjectOutputStream out) throws NotSerializableException {
        throw new NotSerializableException("Not serializable.");
    }

   /**
    * Throws NotSerializableException, since PropertyChangeEvent objects are not
    * intended to be serializable.
    */
    private void readObject(java.io.ObjectInputStream in) throws NotSerializableException {
        throw new NotSerializableException("Not serializable.");
    }
}
