package org.xmlBlaster.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;
import java.util.logging.Logger;

import org.xmlBlaster.util.def.ErrorCode;

import java.io.IOException;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

public abstract class JacksonUtils {

   protected static final Logger log = Logger.getLogger(JacksonUtils.class.getName());

   // Wir wollen nur im Fall Invalid Json eine exception werfen
   public static final ObjectMapper MAPPER = new ObjectMapper()
         .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
//         .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)          // deserilaziation features not needed
//         .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
//         .disable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT)
//         .disable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
//         .disable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
         .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS).setVisibility(PropertyAccessor.ALL, Visibility.NONE)
         .setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
//         .addMixIn(ThreadPoolExecutor.class, TimoutMixin.class)             // these mixins where just for testing what causes the query to fail
//         .addMixIn(Timeout.class, TimoutMixin.class)
//         .addMixIn(HttpIORServer.class, TimoutMixin.class)
   ;

   /**
    * Safely iterates over a JSON array using a streaming {@link JsonParser}.
    * <p>
    * This method guarantees that the parser always advances exactly one token per
    * loop iteration and therefore cannot become stuck in an infinite loop, even
    * when the input JSON is malformed, truncated, or contains unexpected
    * structures.
    * </p>
    *
    * <p>
    * Expected usage:
    * 
    * <pre>
    * safeArrayLoop(parser, "myArray", elementParser -&gt; {
    *    // parser is positioned at START_OBJECT of the array element
    *    // parse your JSON Object here
    *    parser.skipChildren();
    * });
    * </pre>
    * </p>
    *
    * <h3>Error Handling</h3>
    * <ul>
    * <li>If the current token is not {@code START_ARRAY}, the method calls
    * {@code parser.skipChildren()} and throws an {@link XmlBlasterException}.</li>
    * <li>If the array ends unexpectedly (parser.nextToken() returns {@code null}),
    * an {@link XmlBlasterException} is thrown.</li>
    * <li>If an element is not an object, it is skipped safely without stopping the
    * loop.</li>
    * </ul>
    *
    * <p>
    * The provided {@code elementParser} receives the parser positioned at the
    * beginning of each array element and is responsible for consuming that
    * element’s complete structure. Malformed elements are isolated and do not
    * affect the outer loop.
    * </p>
    *
    * @param parser        The streaming JSON parser positioned at
    *                      {@code START_ARRAY}.
    * 
    * @param arrayName     name of the Array that is beeing looped through
    * 
    * @param elementParser A callback invoked for each element inside the array.
    *                      The callback must consume the full element (typically an
    *                      object), but even if it fails, the loop remains safe.
    *
    * @throws IOException   If a low-level parsing error occurs.
    *
    * @throws IOEcxception, XmlBlasterException If the structure is invalid or the
    *                       array terminates unexpectedly.
    */
   public static void safeArrayLoop(Global glob, JsonParser parser, String arrayName, I_ThrowingRunnable elementParser)
         throws IOException, XmlBlasterException {

      if (parser.currentToken() != JsonToken.START_ARRAY) {
         parser.skipChildren();
         log.severe("Expected START_ARRAY for '" + arrayName + "'" + location(parser));
         throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE,
               "Expected START_ARRAY '" + arrayName + "'" + location(parser));
      }

      JsonToken tok;
      while ((tok = parser.nextToken()) != JsonToken.END_ARRAY) {
         if (tok == null) {
            log.severe("unexpected end of JSON in '" + arrayName + "'" + location(parser));
            throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE,
                  "Unexpected end of JSON inside array '" + arrayName + "'" + location(parser));
         }

         if (tok != JsonToken.START_OBJECT) {
            log.severe("Expected START_OBJECT for '" + arrayName + "'" + location(parser));
            log.severe("Skipping parsing the malformed Array");
            parser.skipChildren();
            continue;
         }

         try {
            elementParser.run();
         } catch (IOException | XmlBlasterException e) {
            log.severe("Error parsing element in '" + arrayName + "': " + e.getMessage());
            parser.skipChildren();
            throw e; // or wrap in XmlBlasterException
         }
      }
   }

   /**
    * Safely iterates over a JSON object using a streaming {@link JsonParser}.
    * <p>
    * This method provides a robust, fault-tolerant loop over object fields,
    * ensuring that the parser always advances forward and cannot enter an infinite
    * loop, even when encountering malformed or incomplete JSON.
    * </p>
    *
    * <p>
    * Expected usage:
    * 
    * <pre>
    * safeObjectLoop(parser, (fieldName) -&gt; {
    *    switch (fieldName) {
    *    case "foo":
    *       parseFoo(parser);
    *       break;
    *    case "bar":
    *       parseBar(parser);
    *       break;
    *    default:
    *       parser.skipChildren();
    *    }
    * });
    * </pre>
    * </p>
    *
    * <h3>Behavior and Guarantees</h3>
    * <ul>
    * <li>Ensures one {@code parser.nextToken()} call per iteration.</li>
    * <li>Handles unknown fields by letting the caller skip or parse them.</li>
    * <li>If a field is malformed, the loop still continues safely.</li>
    * <li>If the object ends unexpectedly (nextToken returns {@code null}), an
    * {@link XmlBlasterException} is thrown.</li>
    * <li>If the current token is not {@code START_OBJECT}, the method attempts to
    * skip the structure and throws an {@link XmlBlasterException}.</li>
    * </ul>
    *
    * <p>
    * The supplied {@code fieldHandler} receives the field name and a parser
    * positioned at the value token. The handler must consume the value (via
    * parsing or {@code skipChildren()}), but even if it fails, the outer loop
    * remains safe.
    * </p>
    *
    * @param parser       The streaming JSON parser positioned at
    *                     {@code START_OBJECT}.
    * 
    * @param contextName  name of the Object that is beeing looped thorugh
    *
    * @param fieldHandler Callback invoked for each field in the object. Receives
    *                     the field name.
    *
    * @throws IOException         If an I/O or low-level parsing error occurs.
    *
    * @throws XmlBlasterException If the object structure is invalid or prematurely
    *                             terminated.
    */
   public static void safeObjectLoop(Global glob, JsonParser parser, String contextName,
         I_ThrowingConsumer<String> fieldHandler) throws IOException, XmlBlasterException {

      if (parser.currentToken() != JsonToken.START_OBJECT) {
         log.severe("Expected START_OBJECT for '" + contextName + "'" + location(parser));
         throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE,
               "Expected START_OBJECT '" + contextName + "'" + location(parser));
      }

      try {
         JsonToken tok;
         while ((tok = parser.nextToken()) != JsonToken.END_OBJECT) {
            if (tok == null) {
               log.severe("Unexpected end of JSON in '" + contextName + "'" + location(parser));
               throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE,
                     "Unexpected end of JSON inside object '" + contextName + "'" + location(parser));
            }
            if (tok == JsonToken.START_OBJECT || tok == JsonToken.START_ARRAY) {
               log.severe("Unexpected StartObject Token in '" + contextName + "'" + location(parser));
               log.severe("Skipping this object...");
               parser.skipChildren();
            }

            if (tok != JsonToken.FIELD_NAME) {
               log.severe(
                     "Expected FIELD_NAME while parsing '" + contextName + "', but found " + tok + location(parser));
               continue;
            }
            String name = parser.currentName(); // safe due to if clause! above

            // Move to field value token
            parser.nextToken();

            // Proceed to lambda function consuming the field info
            fieldHandler.accept(name);

         }
      } catch (IOException | XmlBlasterException e) {
         log.severe("Error while parsing inside object '" + contextName + "': " + e.getMessage());
         throw e;
      }
   }

   /**
    * parse Value as String and throw exception if it is null
    * <p/>
    * Catching block should include field name of current token for clarity.
    * 
    * @param glob
    * @param parser
    * @return
    * @throws XmlBlasterException
    * @throws IOException
    */
   public static String notNullValueAsString(Global glob, JsonParser parser) throws XmlBlasterException, IOException {

      String value = parser.getValueAsString();

      if (value == null) {
         throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE,
               "Expected a STRING convertible value but found " + parser.currentToken() + " at " + location(parser));
      }

      return value;
   }

   /**
    * used to safely skip a unknown JSON field. <br>
    * Example:<br>
    * {<br>
    * "good": "field",<br>
    * "bad": { "field": "should be skipped"},<br>
    * "another": []<br>
    * }<br>
    * 
    * This function should be called if the parser is positioned on the
    * StartObjectToken of "bad" or on the StartArrayToken of "another"
    * 
    * @param parser
    * @throws IOException
    */
   public static void skipArrayOrObject(JsonParser parser) throws IOException {
      try {
         if (parser.currentToken() == JsonToken.START_OBJECT || parser.currentToken() == JsonToken.START_ARRAY) {
            parser.skipChildren(); // skip unknown Object/Array
         } 
      } catch (IOException e) {
         log.severe("Error: could not skip unknown field" + location(parser) + "becuase of: " + e.getMessage());
         throw e;
      }
   }

   private static String location(JsonParser parser) {
      JsonLocation loc = parser.currentLocation();
      return " at line " + loc.getLineNr() + ", column " + loc.getColumnNr() + " (char offset " + loc.getCharOffset()
            + ")";
   }

   // for map parsing
   public static Map<String, Object> parseJakson(String jsonStr) throws IOException {
      return MAPPER.readValue(jsonStr, new TypeReference<Map<String, Object>>() {
      });
   }

   public static String toJson(Map<String, Object> jsonMap) throws IOException {
      return MAPPER.writeValueAsString(jsonMap);
   }

}
