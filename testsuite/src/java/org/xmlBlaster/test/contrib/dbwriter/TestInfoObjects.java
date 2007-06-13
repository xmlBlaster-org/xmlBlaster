/*------------------------------------------------------------------------------
Name:      TestInfoObjects.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.dbwriter;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.Info;
import org.xmlBlaster.contrib.dbwriter.info.SqlRow;
import org.xmlBlaster.util.qos.ClientProperty;

import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * 
 */
public class TestInfoObjects extends XMLTestCase {
    private static Logger log = Logger.getLogger(TestInfoObjects.class.getName());
    private I_Info info;
    
    public static void main(String[] args) {
        // junit.swingui.TestRunner.run(TestInfoObjects.class);

       TestInfoObjects test = new TestInfoObjects();
       try {
          test.setUp();
          test.testSqlRowClone();
          test.tearDown();

       }
       catch (Exception ex) {
          ex.printStackTrace();
       }
    }

    /**
     * Default ctor. 
     */
    public TestInfoObjects() {
       super();
       Preferences prefs = Preferences.userRoot();
       this.info = new Info(prefs);
       XMLUnit.setIgnoreWhitespace(true);
    }

   /**
    * Constructor for TestInfoObjects.
    * @param arg0
    */
    public TestInfoObjects(String arg0) {
       super(arg0);
       XMLUnit.setIgnoreWhitespace(true);
    }

    /**
     * Configure database access. 
     * @see TestCase#setUp()
     */
   protected void setUp() throws Exception {
      super.setUp();
   }
   
   /*
    * @see TestCase#tearDown()
    */
   protected void tearDown() throws Exception {
      super.tearDown();
   }

   /**
    * If the table does not exist we expect a null ResultSet
    * @throws Exception Any type is possible
    */
   public final void testSqlRowClone() throws Exception {

      SqlRow sqlRow = new SqlRow(this.info, 0);
      
      sqlRow.setAttribute("attr1", "value1");
      sqlRow.setAttribute("attr2", "value2");
      sqlRow.setAttribute("attr3", "value3");
      sqlRow.setCaseSensitive(true);
      ClientProperty col1 = new ClientProperty("dummy1", null, null);
      col1.setValue("valDummy1");
      sqlRow.setColumn(col1);
      ClientProperty col2 = new ClientProperty("dummy2", null, null);
      col2.setValue("valDummy2");
      sqlRow.setColumn(col2);
      
      assertEquals("the caseSensitive has been modified", true, sqlRow.isCaseSensitive());
      assertEquals("The value of the attribute has been modified", "value1", sqlRow.getAttribute("attr1").getStringValue());
      assertEquals("The value of the attribute has been modified", "value2", sqlRow.getAttribute("attr2").getStringValue());
      assertEquals("The value of the attribute has been modified", "value3", sqlRow.getAttribute("attr3").getStringValue());
      assertEquals("The value of the row has been modified", "valDummy1", sqlRow.getColumn("dummy1").getStringValue());
      assertEquals("The value of the row has been modified", "valDummy2", sqlRow.getColumn("dummy2").getStringValue());
      assertEquals("The number of attributes has been modified", 3, sqlRow.getAttributeNames().length);
      assertEquals("The value of columns has been modified", 2, sqlRow.getColumnNames().length);
      
      SqlRow row2 = sqlRow.cloneRow();

      assertEquals("the caseSensitive has been modified", true, row2.isCaseSensitive());
      assertEquals("The value of the attribute has been modified", "value1", row2.getAttribute("attr1").getStringValue());
      assertEquals("The value of the attribute has been modified", "value2", row2.getAttribute("attr2").getStringValue());
      assertEquals("The value of the attribute has been modified", "value3", row2.getAttribute("attr3").getStringValue());
      assertEquals("The value of the row has been modified", "valDummy1", row2.getColumn("dummy1").getStringValue());
      assertEquals("The value of the row has been modified", "valDummy2", row2.getColumn("dummy2").getStringValue());
      assertEquals("The number of attributes has been modified", 3, row2.getAttributeNames().length);
      assertEquals("The value of columns has been modified", 2, row2.getColumnNames().length);
      
      row2.setCaseSensitive(false);
      row2.setAttribute("attr1", "newValue1");
      row2.setAttribute("attr4", "newValue4");
      row2.renameColumn("dummy1", "newDummy1");
      
      assertEquals("the caseSensitive has been modified", true, sqlRow.isCaseSensitive());
      assertEquals("The value of the attribute has been modified", "value1", sqlRow.getAttribute("attr1").getStringValue());
      assertEquals("The value of the row has been modified", "valDummy1", sqlRow.getColumn("dummy1").getStringValue());
      
      log.info(row2.toXml(""));
      assertEquals("the caseSensitive has been modified", false, row2.isCaseSensitive());
      assertEquals("The value of the attribute has been modified", "newValue1", row2.getAttribute("attr1").getStringValue());
      Object obj = row2.getColumn("dummy1");
      assertNull("The value of the row has been modified", obj);
      assertEquals("The value of the row has been modified", "valDummy1", row2.getColumn("newDummy1").getStringValue());
      
      ClientProperty tmpCol = row2.getColumn("dummy2");
      tmpCol.setValue("newValueDummy2");
      assertEquals("the content of the column has been modified", "valDummy2", sqlRow.getColumn("dummy2").getStringValue());
      assertEquals("the content of the column has been modified", "newValueDummy2", row2.getColumn("dummy2").getStringValue());
      
      ClientProperty tmpAttr = row2.getAttribute("attr3");
      tmpAttr.setValue("newValueAttr3");
      assertEquals("the content of the attribute has been modified", "value3", sqlRow.getAttribute("attr3").getStringValue());
      assertEquals("the content of the attribute has been modified", "newValueAttr3", row2.getAttribute("attr3").getStringValue());
      
      
      log.info("SUCCESS");
   }
   

}
