/*------------------------------------------------------------------------------
Name:      ContainsIgnoreCaseFunction.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Support check of message content with XPath expressions.
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime.xpath;

import org.jaxen.Function;
import org.jaxen.FunctionCallException;
import org.jaxen.Context;
import org.jaxen.Navigator;
import org.jaxen.function.StringFunction;

import java.util.List;

/**
 * Jaxen XPath extension function: contains-ignore-case(str, match).
 * <p>XPATH contains function that ignores case. Example:</p>
 * <pre>
 * contains-ignore-case(//body.content, 'XmlBlaster')
 * </pre>
 *
 * @author Jens Askengren
 * @version $Id$
 */
public class ContainsIgnoreCaseFunction
	implements Function
{

	public Object call(Context context, List args)
		throws FunctionCallException
	{
		if (args.size() != 2) {
			throw new FunctionCallException("contains-ignore-case() requires two arguments.");
		}

		return evaluate(args.get(0),
				args.get(1),
				context.getNavigator());
	}

	public static Boolean evaluate(Object strArg,
				       Object matchArg,
				       Navigator nav)
	{
		String str = StringFunction.evaluate(strArg,
						     nav).toLowerCase();

		String match = StringFunction.evaluate(matchArg,
						       nav).toLowerCase();

		return ((str.indexOf(match) >= 0)
			? Boolean.TRUE : Boolean.FALSE);
	}

}
