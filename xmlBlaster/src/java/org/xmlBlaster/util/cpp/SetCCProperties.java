/*--------------------------------------------------------------------------
Name:      SetCCProperties.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Used to recreate the platform-specific header file PropertyDef.h
Version:   $Id: SetCCProperties.java,v 1.1 2000/07/14 02:11:02 laghi Exp $
---------------------------------------------------------------------------*/

package org.xmlBlaster.util.cpp;

import java.util.Properties;

public class SetCCProperties 
{
    
    static void main(String args[]) {
	String quote = new String("") + (char)34;

	Properties prop = System.getProperties();

	// write out the header
	System.out.println("/*----------------------------------------------" +
			   "----------------------------");
	System.out.println("Name:      PropertyDef.h");
	System.out.println("Project:   xmlBlaster.org");
	System.out.println("Copyright: xmlBlaster.org, see xmlBlaster-" +
			   "LICENSE file");
	System.out.println("Comment:   Handling the Client data");
	System.out.println("Version:   $Id: SetCCProperties.java,v 1.1 2000/07/14 02:11:02 laghi Exp $");
	System.out.println("------------------------------------------------" +
			   "---------------------------*/");
	System.out.println("");
	System.out.println("#ifndef _UTIL_PROPERTYDEF_H");
	System.out.println("#define _UTIL_PROPERTYDEF_H");
	System.out.println("");
	System.out.println("#define FILE_SEP " + quote +
			   prop.getProperty("file.separator") + quote);
	System.out.println("#define PATH_SEP " + quote + 
			   prop.getProperty("path.separator") + quote);

	System.out.println("");
	System.out.println("#endif");
	
	// prop.list(System.out);
    }

};

