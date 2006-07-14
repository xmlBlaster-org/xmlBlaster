package org.xmlBlaster.test.classtest;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

import junit.framework.*;

/**
 * Test XmlBlasterException. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.XmlBlasterExceptionTest
 * @see org.xmlBlaster.util.XmlBlasterException
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html" target="others">the interface.subscribe requirement</a>
 */
public class XmlBlasterExceptionTest extends TestCase {
   protected Global glob;
   int counter = 0;

   public XmlBlasterExceptionTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();
   }

   public void testConstructor() {
      System.out.println("***XmlBlasterExceptionTest: testConstructor ...");
      
      XmlBlasterException ex = new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_BYTES, "LOC", "Bla bla");

      assertEquals("", ErrorCode.RESOURCE_OVERFLOW_QUEUE_BYTES, ex.getErrorCode());
      assertEquals("", ErrorCode.RESOURCE_OVERFLOW_QUEUE_BYTES.getErrorCode(), ex.getErrorCodeStr());
      assertEquals("", glob.getId(), ex.getNode());
      assertEquals("", "LOC", ex.getLocation());
      assertEquals("", "en", ex.getLang());
      assertTrue("Bla bla" + " <-> " + ex.getRawMessage(), ex.getRawMessage().indexOf("Bla bla") != -1);
      //assertEquals("", ex.getMessage(), ex.getMessage());
      //assertEquals("", ex.getVersionInfo(), ex.getVersionInfo());
      //assertEquals("", ?, ex.getTimestamp().getTimestamp());
      assertEquals("", null, ex.getEmbeddedException());
      assertEquals("", "", ex.getEmbeddedMessage());
      assertEquals("", "<transaction/>", ex.getTransactionInfo());
      assertEquals("", false, ex.isInternal());
      assertEquals("", true, ex.isResource());
      assertEquals("", false, ex.isCommunication());
      assertEquals("", false, ex.isUser());
      assertEquals("", false, ex.isTransaction());

      System.out.println("***XmlBlasterExceptionTest: testConstructor [SUCCESS]");
   }

   public void testParse() {
      System.out.println("***XmlBlasterExceptionTest: testParse ...");
      
      XmlBlasterException ex = new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_BYTES, "LOC", "Bla bla");
      byte[] serial = ex.toByteArr();
      XmlBlasterException back = XmlBlasterException.parseByteArr(glob, serial);

      assertEquals("", ex.getErrorCode().toString(), back.getErrorCode().toString());
      assertEquals("", ex.getErrorCodeStr(), back.getErrorCodeStr());
      assertEquals("", ex.getNode(), back.getNode());
      assertEquals("", ex.getLocation(), back.getLocation());
      assertEquals("", ex.getLang(), back.getLang());
      assertEquals(ex.getRawMessage() + " <-> " + back.getRawMessage(), ex.getRawMessage(), back.getRawMessage());
      assertEquals(ex.getMessage() + " <-> " + back.getMessage(), ex.getMessage(), back.getMessage());
      assertEquals("", ex.getVersionInfo(), back.getVersionInfo());
      assertEquals("", ex.getTimestamp().getTimestamp(), back.getTimestamp().getTimestamp());
      assertEquals("", ex.getEmbeddedException(), back.getEmbeddedException());
      assertEquals("", ex.getStackTraceStr(), back.getStackTraceStr());
      assertEquals("", ex.getEmbeddedMessage(), back.getEmbeddedMessage());
      assertEquals("", ex.getTransactionInfo(), back.getTransactionInfo());
      assertEquals("", ex.isInternal(), back.isInternal());
      assertEquals("", ex.isResource(), back.isResource());
      assertEquals("", ex.isCommunication(), back.isCommunication());
      assertEquals("", ex.isUser(), back.isUser());
      assertEquals("", ex.isTransaction(), back.isTransaction());
      assertEquals("", ex.toString(), back.toString());

      System.out.println("***XmlBlasterExceptionTest: testParse [SUCCESS]");
   }

   public void testParseToString() {
      System.out.println("***XmlBlasterExceptionTest: testParseToString ...");
      
      XmlBlasterException ex = new XmlBlasterException(glob, ErrorCode.RESOURCE_OVERFLOW_QUEUE_BYTES, "LOC", "Bla bla");
      String serial = ex.toString();
      XmlBlasterException back = XmlBlasterException.parseToString(glob, serial, ErrorCode.INTERNAL_UNKNOWN);

      System.out.println("ORIGINAL:\n" + ex.toXml());
      System.out.println("BACK:\n" + back.toXml());

      assertEquals("", ex.getErrorCode().toString(), back.getErrorCode().toString());
      assertEquals("", ex.getErrorCodeStr(), back.getErrorCodeStr());
      //assertEquals("", ex.getNode(), back.getNode());
      //assertEquals("", ex.getLocation(), back.getLocation());
      //assertEquals("", ex.getLang(), back.getLang());
      //assertEquals("", ex.getRawMessage(), back.getRawMessage());
      //assertEquals("", ex.getMessage(), back.getMessage());
      //assertEquals("", ex.getVersionInfo(), back.getVersionInfo());
      //assertEquals("", ex.getTimestamp().getTimestamp(), back.getTimestamp().getTimestamp());
      //assertEquals("", ex.getEmbeddedException(), back.getEmbeddedException());
      //assertEquals("", ex.getStackTraceStr(), back.getStackTraceStr());
      //assertEquals("", ex.getEmbeddedMessage(), back.getEmbeddedMessage());
      //assertEquals("", ex.getTransactionInfo(), back.getTransactionInfo());
      assertEquals("", ex.isInternal(), back.isInternal());
      assertEquals("", ex.isResource(), back.isResource());
      assertEquals("", ex.isCommunication(), back.isCommunication());
      assertEquals("", ex.isUser(), back.isUser());
      assertEquals("", ex.isTransaction(), back.isTransaction());
      assertEquals("", ex.toString(), back.toString());

      System.out.println("***XmlBlasterExceptionTest: testParseToString [SUCCESS]");
   }

   public void testEmbeddedException() {
      System.out.println("***XmlBlasterExceptionTest: testEmbeddedException ...");
      
      IllegalArgumentException il = new IllegalArgumentException("SUPER");
      XmlBlasterException ex = new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, "HERE", "OK", il);
      byte[] serial = ex.toByteArr();
      XmlBlasterException back = XmlBlasterException.parseByteArr(glob, serial);

      System.out.println(back.getStackTraceStr());
      System.out.println(back.toXml());

      assertEquals("", "HERE", back.getLocation());
      assertEquals("", "java.lang.IllegalArgumentException: SUPER", back.getEmbeddedMessage());
      assertEquals("", true, back.isInternal());
      assertEquals("", false, back.isResource());
      assertEquals("", false, back.isCommunication());
      assertEquals("", false, back.isUser());
      assertEquals("", false, back.isTransaction());

      assertEquals("", ex.getErrorCode().toString(), back.getErrorCode().toString());
      assertEquals("", ex.getErrorCodeStr(), back.getErrorCodeStr());
      assertEquals("", ex.getNode(), back.getNode());
      assertEquals("", ex.getLocation(), back.getLocation());
      assertEquals("", ex.getLang(), back.getLang());
      assertEquals("", ex.getRawMessage(), back.getRawMessage());
      assertEquals("", ex.getMessage(), back.getMessage());
      assertEquals("", ex.getVersionInfo(), back.getVersionInfo());
      assertEquals("", ex.getTimestamp().getTimestamp(), back.getTimestamp().getTimestamp());
      assertEquals("", il, ex.getEmbeddedException());
      assertEquals("", null, back.getEmbeddedException());
      assertEquals("", ex.getStackTraceStr(), back.getStackTraceStr());
      assertEquals("", ex.getEmbeddedMessage(), back.getEmbeddedMessage());
      assertEquals("", ex.getTransactionInfo(), back.getTransactionInfo());
      assertEquals("", ex.isInternal(), back.isInternal());
      assertEquals("", ex.isResource(), back.isResource());
      assertEquals("", ex.isCommunication(), back.isCommunication());
      assertEquals("", ex.isUser(), back.isUser());
      assertEquals("", ex.isTransaction(), back.isTransaction());
      assertEquals("", ex.toString(), back.toString());

      System.out.println("***XmlBlasterExceptionTest: testEmbeddedException [SUCCESS]");
   }

   public void testIllegalFormat() {
      System.out.println("***XmlBlasterExceptionTest: testIllegalFormat ...");
      
      String logFormat = "errorCode=[{0}] node=[{-100}] location=[{2}] message=[{4} : {99}]";
      Global globTmp = glob.getClone(null);

      try {
         globTmp.getProperty().set("XmlBlasterException.logFormat", logFormat);
         globTmp.getProperty().set("XmlBlasterException.logFormat.internal", logFormat);
      }
      catch (XmlBlasterException e) {
         fail(e.toString());
      }

      IllegalArgumentException il = new IllegalArgumentException("SUPER");
      XmlBlasterException ex = new XmlBlasterException(globTmp, ErrorCode.INTERNAL_UNKNOWN, "HERE", "OK", il);
      ex.setLogFormatInternal(logFormat);
      byte[] serial = ex.toByteArr();
      XmlBlasterException back = XmlBlasterException.parseByteArr(globTmp, serial);

      System.out.println(back.getStackTraceStr());
      System.out.println(back.toXml());

      assertEquals("", "HERE", back.getLocation());
      assertEquals("", "java.lang.IllegalArgumentException: SUPER", back.getEmbeddedMessage());
      assertEquals("", true, back.isInternal());
      assertEquals("", false, back.isResource());
      assertEquals("", false, back.isCommunication());
      assertEquals("", false, back.isUser());
      assertEquals("", false, back.isTransaction());

      assertEquals("", ex.getErrorCode().toString(), back.getErrorCode().toString());
      assertEquals("", ex.getErrorCodeStr(), back.getErrorCodeStr());
      assertEquals("", ex.getNode(), back.getNode());
      assertEquals("", ex.getLocation(), back.getLocation());
      assertEquals("", ex.getLang(), back.getLang());
      assertEquals("", ex.getRawMessage(), back.getRawMessage());
      assertEquals("", ex.getMessage(), back.getMessage());
      assertEquals("", ex.getVersionInfo(), back.getVersionInfo());
      assertEquals("", ex.getTimestamp().getTimestamp(), back.getTimestamp().getTimestamp());
      assertEquals("", il, ex.getEmbeddedException());
      assertEquals("", null, back.getEmbeddedException());
      assertEquals("", ex.getStackTraceStr(), back.getStackTraceStr());
      assertEquals("", ex.getEmbeddedMessage(), back.getEmbeddedMessage());
      assertEquals("", ex.getTransactionInfo(), back.getTransactionInfo());
      assertEquals("", ex.isInternal(), back.isInternal());
      assertEquals("", ex.isResource(), back.isResource());
      assertEquals("", ex.isCommunication(), back.isCommunication());
      assertEquals("", ex.isUser(), back.isUser());
      assertEquals("", ex.isTransaction(), back.isTransaction());
      assertEquals("", ex.toString(), back.toString());

      System.out.println("***XmlBlasterExceptionTest: testIllegalFormat [SUCCESS]");
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.XmlBlasterExceptionTest
    * </pre>
    */
   public static void main(String args[]) {
      XmlBlasterExceptionTest testSub = new XmlBlasterExceptionTest("XmlBlasterExceptionTest");
      testSub.setUp();
      testSub.testConstructor();
      testSub.testParse();
      testSub.testParseToString();
      testSub.testEmbeddedException();
      testSub.testIllegalFormat();
      //testSub.tearDown();
   }
}
