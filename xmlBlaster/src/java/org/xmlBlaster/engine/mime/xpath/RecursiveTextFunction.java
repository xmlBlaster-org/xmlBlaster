/*------------------------------------------------------------------------------
Name:      RecursiveTextFunction.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Support check of message content with XPath expressions.
------------------------------------------------------------------------------*/package org.xmlBlaster.engine.mime.xpath;

import org.jaxen.Function;
import org.jaxen.FunctionCallException;
import org.jaxen.Context;
import org.jaxen.Navigator;
import org.jaxen.function.StringFunction;

import java.util.List;
import java.util.Iterator;

/**
 * Jaxen XPath extension function: recursive-text(node)
 *
 * <p>Recursivly concat and return all descending text nodes of the node. Mat
 * for example be used with ContainsIgnoreCaseFunction: </p>
 * <pre>
 * contains-ignore-case(recursive-text(//body.content), 'XmlBlaster')
 * </pre>
 * @author Jens Askengren
 * @version $Id$
 */
public class RecursiveTextFunction
	implements Function
{

	public Object call(Context context, List args)
		throws FunctionCallException
	{
		if (args.size() != 1) {
			throw new FunctionCallException("recursive-text() requires one argument.");
		}
		return evaluate(args.get(0), context.getNavigator());
	}

	public static String evaluate(Object strArg, Navigator nav)
	{
		if (strArg instanceof List) {

			StringBuffer buff = new StringBuffer();
			List list = (List) strArg;
			Iterator iter = list.iterator();

			while ( iter.hasNext()) {
				buff.append(evaluate(iter.next(), nav));
			}

			return buff.toString();
		}

		return StringFunction.evaluate(strArg, nav);
	}
}
