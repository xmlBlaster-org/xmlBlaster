package authentication;

import org.xmlBlaster.util.*;
import org.xmlBlaster.client.*;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.jutils.io.FileUtil;


import junit.framework.*;

public class TestAuthenticationHtPassWd extends TestCase
{
  private ServerThread serverThread = null;
  private final String RIGHT_USERNAME = "existingUser";
  private final String START_WITH_USERNAME = "existing";
  private final String RIGHT_PASSWORD = "existingUserPW";
  private final String WRONG_USERNAME = "notExistingUser";
  private final String WRONG_PASSWORD = "notExistingUserPW";
  private String userhome = "";
  private Global glob = null;
  private XmlBlasterConnection con = null;

  public final String ME = "TestAuthenticationHtPassWd";



  public TestAuthenticationHtPassWd (String name)
  { super(name);
    this.glob = new Global();
    this.userhome = glob.getProperty().get("${user.home}","/home/astelzl")+glob.getProperty().get("${file.separator}","/");
    try
    { FileUtil.writeFile(userhome+"test.htpasswd","existingUser:yZum5CYzDk.EE\n");
      FileUtil.writeFile(userhome+"test.htpasswd1","*");
    }
    catch(Exception ex)
    { assertTrue("Could not create password files. Tests won't work!",false);
    }
  }                                     

  protected void setUp()
  {
  }

  private void testCaseSetup(int testcase)
  { String[] args = new String[4];
    switch (testcase)
    { case 1: args[0] = "-Security.Server.Plugin.htpasswd.secretfile";      
              args[1] = userhome+"test.htpasswd";
              args[2] = "-Security.Server.Plugin.htpasswd.allowPartialUsername";
              args[3] = "true";
              break;
      case 2: args[0] = "-Security.Server.Plugin.htpasswd.secretfile";
              args[1] = userhome+"test.htpasswd";
              args[2] = "-Security.Server.Plugin.htpasswd.allowPartialUsername";
              args[3] = "false";
              break;  
      case 3: args[0] = "-Security.Server.Plugin.htpasswd.secretfile";
              args[1] = userhome+"test.htpasswd";
              args[2] = "-Security.Server.Plugin.htpasswd.allowPartialUsername";
              args[3] = "false";
              break;  
      case 4: args[0] = "-Security.Server.Plugin.htpasswd.secretfile";
              args[1] = userhome+"test.htpasswd1";
              args[2] = "-Security.Server.Plugin.htpasswd.allowPartialUsername";
              args[3] = "false";
              break;  
    }
    serverThread = ServerThread.startXmlBlaster(new Global(args));
    try { Thread.currentThread().sleep(4000);} catch(Exception ex) {}
  }

  protected void tearDown()
  { try { Thread.currentThread().sleep(4000);} catch(Exception ex) {} 
    if (serverThread != null)
      serverThread.stopServer(true);
    try { Thread.currentThread().sleep(4000);} catch(Exception ex) {} 
  }

  public void testAuthHtPassWordCase1()
  { Log.info(ME,"Testcase1");
    testCaseSetup(1);
    boolean isValue=true;
    try
    { con = new XmlBlasterConnection(new Global(new String[0]));
    }
    catch(Exception ex)
    { Log.error(ME,"Could not initialize XmlBlasterConnection: " + ex.toString());
      ex.printStackTrace();
    }
    try
    { ConnectQos qos = new ConnectQos(glob,START_WITH_USERNAME, RIGHT_PASSWORD);
      ConnectReturnQos conRetQos = con.connect(qos, null);
      Thread.currentThread().sleep(4000);
      con.disconnect(null);
    }
    catch(Exception ex)
    { Log.info(ME,"Could not connect: " + ex.toString());
      ex.printStackTrace();
      isValue = false;
    }
    assertTrue("Could not connect although it should have been possible with the specified beginning of username and password",isValue);
                 
  }
  
  public void testAuthHtPassWordCase2()
  { Log.info(ME,"Testcase2");
    testCaseSetup(2);
    boolean isValue = true;
    try
    { con = new XmlBlasterConnection(new Global(new String[0]));
    }
    catch(Exception ex)
    { Log.error(ME,"Could not initialize XmlBlasterConnection");
    }
    try
    { ConnectQos qos = new ConnectQos(glob,RIGHT_USERNAME, RIGHT_PASSWORD);
      ConnectReturnQos conRetQos = con.connect(qos, null);
      con.disconnect(null);
    }
    catch(Exception ex)
    { Log.info(ME,"Could not connect");
      isValue = false;
      ex.printStackTrace();
    }
    assertTrue("Could not connect although it should have been possible with the specified username and password",isValue);

  }

  public void testAuthHtPassWordCaseWrongPassword()
  { Log.info(ME,"Testcase3");
    testCaseSetup(3);
    boolean isValue = false;
    try
    { con = new XmlBlasterConnection(new Global(new String[0]));
    }
    catch(Exception ex)
    { Log.error(ME,"Could not initialize XmlBlasterConnection");
    }
    try
    { ConnectQos qos = new ConnectQos(glob,WRONG_USERNAME, WRONG_PASSWORD);
      ConnectReturnQos conRetQos = con.connect(qos, null);
      con.disconnect(null);
    }
    catch(Exception ex)
    { isValue = true;
      Log.info(ME,"Could not connect");
    }
    assertTrue("Could connect although it should not have been possible with the specified username and password",isValue);
  }

  public void testAuthHtPassWordCase3()
  { Log.info(ME,"Testcase4");
    testCaseSetup(4);
    boolean isValue = true;
    try
    { con = new XmlBlasterConnection(new Global(new String[0]));
    }
    catch(Exception ex)
    { Log.error(ME,"Could not initialize XmlBlasterConnection");
    }
    try
    { ConnectQos qos = new ConnectQos(glob,WRONG_USERNAME, WRONG_PASSWORD);
      ConnectReturnQos conRetQos = con.connect(qos, null);
      con.disconnect(null);
    }
    catch(Exception ex)
    { Log.info(ME,"Could not connect");
      isValue = false;
    }
    assertTrue("Could not connect although it should have been possible as any username and password is authenticated",isValue);
  }
  
  
}
