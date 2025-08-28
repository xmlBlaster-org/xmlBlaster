package org.xmlBlaster.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;
import java.io.IOException;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

public abstract class JacksonUtils {
   // Wir wollen nur im Fall Invalid Json eine exception werfen
   public static final ObjectMapper MAPPER = new ObjectMapper()
         .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
//         .disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)          // deserilaziation features not needed
//         .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
//         .disable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT)
//         .disable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
//         .disable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
         .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)         
         .setVisibility(PropertyAccessor.ALL,Visibility.NONE)
         .setVisibility(PropertyAccessor.FIELD, Visibility.ANY)
//         .addMixIn(ThreadPoolExecutor.class, TimoutMixin.class)             // these mixins where just for testing what causes the query to fail
//         .addMixIn(Timeout.class, TimoutMixin.class)
//         .addMixIn(HttpIORServer.class, TimoutMixin.class)
         ;
   

   // for map parsing
   public static Map<String, Object> parseJakson(String jsonStr) throws IOException {
      return MAPPER.readValue(jsonStr, new TypeReference<Map<String, Object>>() { } );
  }

  public static String toJson(Map<String, Object> jsonMap) throws IOException {
      return MAPPER.writeValueAsString(jsonMap);
  }

}
