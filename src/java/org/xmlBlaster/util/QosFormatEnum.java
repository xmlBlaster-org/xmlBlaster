package org.xmlBlaster.util;

/**
 * -qosFormat json
 */
public enum QosFormatEnum {
   JSON, XML;

   /**
    * Create enum from string (case-insensitive)
    * 
    * @param value the string value
    * @return corresponding QosFormatEnum
    * @throws IllegalArgumentException if invalid
    */
   public static QosFormatEnum fromString(String value) {
      if (value == null) {
         throw new IllegalArgumentException("Value cannot be null");
      }
      return switch (value.trim().toUpperCase()) {
      case "JSON" -> JSON;
      case "XML" -> XML;
      default -> throw new IllegalArgumentException("Unknown QosFormatEnum: " + value);
      };
   }

   public static QosFormatEnum fromString(String value, QosFormatEnum defaultEnum) {
      if (value == null) {
         return defaultEnum;
      }
      return switch (value.trim().toUpperCase()) {
      case "JSON" -> JSON;
      case "XML" -> XML;
      default -> defaultEnum;
      };
   }

   /** Convenience method */
   public boolean isJson() {
      return this == JSON;
   }

   /** Convenience method */
   public boolean isXml() {
      return this == XML;
   }

   @Override
   public String toString() {
      return name(); // returns "JSON" or "XML"
   }
}