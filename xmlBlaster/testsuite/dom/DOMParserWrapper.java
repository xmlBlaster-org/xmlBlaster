/*
 * (C) Copyright IBM Corp. 1999  All rights reserved.
 *
 * US Government Users Restricted Rights Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 *
 * The program is provided "as is" without any warranty express or
 * implied, including the warranty of non-infringement and the implied
 * warranties of merchantibility and fitness for a particular purpose.
 * IBM will not be liable for any damages suffered by you as a result
 * of using the Program. In no event will IBM be liable for any
 * special, indirect or consequential damages or lost profits even if
 * IBM has been advised of the possibility of their occurrence. IBM
 * will not be liable for any third party claims against you.
 */

package testsuite.dom;

import org.w3c.dom.Document;

/**
 * Encapsulates a DOM parser.
 *
 * @version Revision: 09 1.1 samples/dom/DOMParserWrapper.java, samples, xml4j2, xml4j2_0_15
 */
public interface DOMParserWrapper {

    //
    // DOMParserWrapper methods
    //

    /** Parses the specified URI and returns the document. */
    public Document parse(String uri) throws Exception;

} // interface DOMParserWrapper
