/*------------------------------------------------------------------------------
Name:      StopParseException.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Throw this exception to stop SAX parsing
Version:   $Id: StopParseException.java,v 1.1 1999/12/16 11:29:09 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


/**
 * Throw this exception to stop SAX parsing. 
 * <p />
 * Usually thrown in startElement() or endElement() if
 * you are not interested in the following tags anymore.<br />
 * Note that this exception extends RuntimeException,
 * so we don't need to declare it with a throws clause.
 */
public class StopParseException extends RuntimeException
{
}
