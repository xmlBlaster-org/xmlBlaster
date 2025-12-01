package org.xmlBlaster.test.util;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.JacksonUtils;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;

import junit.framework.*;

public class JacksonUtilsTest extends TestCase {

   private Global glob = null;

   public JacksonUtilsTest(String name) {
      super(name);
   }

   public void setUp() {
      this.glob = Global.instance();
   }

   private JsonParser parser(String json) {
      try {
         JsonFactory f = new JsonFactory();
         return f.createParser(new StringReader(json));
      } catch (Exception e) {
         fail("Failed to create parser: " + e.getMessage());
         return null;
      }
   }

   // ----------------------------------------------------------------------
   // OBJECT TESTS
   // ----------------------------------------------------------------------

   public void testSafeObjectLoop_ok() {
      String json = " { \"a\": 123, \"b\": \"hello\" }";
      try {
         JsonParser p = parser(json);
         p.nextToken(); // START_OBJECT

         List<String> collected = new ArrayList<>();

         JacksonUtils.safeObjectLoop(glob, p, "root", field -> {
            collected.add(field);
            try {
               p.skipChildren();
            } catch (Exception e) {
               /* ignore */ }
         });

         assertEquals(List.of("a", "b"), collected);

      } catch (XmlBlasterException | IOException e) {
         fail("Unexpected exception: " + e.getMessage());
      }
   }

   public void testSafeObjectLoop_missingStartObject() {
      String json = "\"notAnObject\"";
      try {
         JsonParser p = parser(json);
         p.nextToken(); // VALUE_STRING

         JacksonUtils.safeObjectLoop(glob, p, "root", f -> {
         });
         fail("Expected XmlBlasterException not thrown");
      } catch (XmlBlasterException | IOException e) {
         assertTrue(e.getMessage().contains("Expected START_OBJECT"));
      }
   }

   public void testSafeObjectLoop_truncatedObject() {
      String json = "{ \"field\": 123";
      try {
         JsonParser p = parser(json);
         p.nextToken(); // START_OBJECT

         JacksonUtils.safeObjectLoop(glob, p, "root", f -> {
            try {
               p.skipChildren();
            } catch (Exception ex) {
               /* ignore */ }
         });
         fail("Expected XmlBlasterException not thrown");
      } catch (XmlBlasterException | IOException e) {
         // expected
      }
   }

   // ----------------------------------------------------------------------
   // ARRAY TESTS
   // ----------------------------------------------------------------------

   public void testSafeArrayLoop_ok() {
      String json = "[ {\"a\": 1}, {\"b\": 2} ]";
      try {
         JsonParser p = parser(json);
         p.nextToken(); // START_ARRAY

         List<String> elements = new ArrayList<>();

         JacksonUtils.safeArrayLoop(glob, p, "arr", () -> {
            JacksonUtils.safeObjectLoop(glob, p, "element", field -> {
               elements.add(field);
               try {
                  p.skipChildren();
               } catch (Exception e) {
                  /* ignore */ }
            });
         });

         assertEquals(List.of("a", "b"), elements);

      } catch (XmlBlasterException | IOException e) {
         fail("Unexpected exception: " + e.getMessage());
      }
   }

   public void testSafeArrayLoop_missingStartArray() {
      String json = "{ \"not\": \"array\" }";
      try {
         JsonParser p = parser(json);
         p.nextToken(); // START_OBJECT
         JacksonUtils.safeArrayLoop(glob, p, "arr", () -> {
         });
         fail("Expected XmlBlasterException not thrown");
      } catch (XmlBlasterException | IOException e) {
         assertTrue(e.getMessage().contains("Expected START_ARRAY"));
      }
   }

   public void testSafeArrayLoop_truncatedArray() {
      String json = "[ {\"x\":1}";
      try {
         JsonParser p = parser(json);
         p.nextToken(); // START_ARRAY

         JacksonUtils.safeArrayLoop(glob, p, "arr", () -> {
            JacksonUtils.safeObjectLoop(glob, p, "element", f -> {
               try {
                  p.skipChildren();
               } catch (Exception e) {
                  /* ignore */ }
            });
         });
         fail("Expected XmlBlasterException not thrown");
      } catch (XmlBlasterException | IOException e) {
         // expected
      }
   }

   public void testSafeArrayLoop_skipsInvalidElements() {
      String json = "[ {\"a\":1}, 123, {\"b\":2} ]";
      try {
         JsonParser p = parser(json);
         p.nextToken(); // START_ARRAY

         List<String> result = new ArrayList<>();

         JacksonUtils.safeArrayLoop(glob, p, "arr", () -> {
            JacksonUtils.safeObjectLoop(glob, p, "elem", f -> {
               result.add(f);
               try {
                  p.skipChildren();
               } catch (Exception e) {
                  /* ignore */ }
            });
         });

         assertEquals(List.of("a", "b"), result);

      } catch (XmlBlasterException | IOException e) {
         fail("Unexpected exception: " + e.getMessage());
      }
   }

   public void testSafeArrayLoop_callbackThrows() {
      String json = "[ {\"a\": 1} ]";
      try {
         JsonParser p = parser(json);
         p.nextToken(); // START_ARRAY

         JacksonUtils.safeArrayLoop(glob, p, "arr", () -> {
            throw new XmlBlasterException(glob, ErrorCode.USER_WRONG_API_USAGE, "TEST");
         });
         fail("Expected XmlBlasterException not thrown");
      } catch (XmlBlasterException | IOException e) {
         // expected
         assertEquals(
               "XmlBlasterException errorCode=[user.wrongApiUsage] serverSideException=false node=[xmlBlaster] location=[TEST] message=[#2.2.1 Please check your client code. -> http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.errorcodes.listing.html#user.wrongApiUsage : ]",
               e.getMessage());
      }
   }

   public static void main(String args[]) {
      JacksonUtilsTest testSub = new JacksonUtilsTest("JacksonUtilsTest");
      testSub.setUp();
      testSub.testSafeArrayLoop_callbackThrows();
      testSub.testSafeArrayLoop_missingStartArray();
      testSub.testSafeArrayLoop_ok();
      testSub.testSafeArrayLoop_skipsInvalidElements();
      testSub.testSafeObjectLoop_missingStartObject();
      testSub.testSafeObjectLoop_ok();
      testSub.testSafeObjectLoop_truncatedObject();

   }
}
