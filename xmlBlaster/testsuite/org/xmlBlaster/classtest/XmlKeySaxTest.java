package classtest;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.xml2java.XmlKeySax;
import org.xmlBlaster.engine.helper.AccessFilterQos;

import junit.framework.*;

/**
 * Test XmlKeySax. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * @see org.xmlBlaster.engine.xml2java.XmlKeySax
 */
public class XmlKeySaxTest extends TestCase {
   protected Global glob;

   public XmlKeySaxTest(String name) {
      super(name);
   }

   protected void setUp() {
   }

   public void testXPath() {
      System.out.println("TestXPath ...");
      try {
         Global glob = new Global();
         String xpath = "//STOCK";
         String xml =
            "  <key queryType='XPATH'>\n" +
            "     " + xpath + "\n" +
            "     <filter type='ContentLength'>\n" +
            "       8000\n" +
            "     </filter>\n" +
            "     <filter type='ContainsChecker' version='7.1' xy='true'>\n" +
            "       bug\n" +
            "     </filter>\n" +
            "  </key>";

         XmlKey xmlKey = new XmlKey(glob, xml);
         AccessFilterQos[] qosArr = xmlKey.getFilterQos();
         assertEquals("Missing filters", 2, qosArr.length);
         assertEquals("Wrong filter", "8000", qosArr[0].getQuery().toString());
         assertEquals("Wrong filter", "bug", qosArr[1].getQuery().toString());
         assertEquals("XPath is different", xmlKey.getQueryString(), xpath);
      }
      catch(Throwable e) {
         fail("Exception thrown: " + e.toString());
      }
   }
}
