package org.xmlBlaster.util.log;

import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.util.StringPairTokenizer;

/**
 * Rebind System.out and System.err to java.util.logging.
 * <p>
 * Activate with
 * -xmlBlaster/stdoutStderrToLogging true -xmlBlaster/stdoutSuppressSet sometoken;someothertoken  -xmlBlaster/stderrSuppressSet sometoken;someothertoken
 * <br>
 * The -xmlBlaster/stdoutSuppressSet allows to suppress some messages which flood your log file
 * <p>
 * Caution: A ConsoleAppender will log to System.out and end up here as well and will be redirected to the log file 
 * 
 * @author Marcel Ruff
 */
public final class StdoutStderrRedirector {
	private static Logger log = Logger.getLogger(StdoutStderrRedirector.class
			.getName());
	private static PrintStream stdout = System.out;
	private static PrintStream stderr = System.err;
	private StdoutStderrOutputstream out;
	private StdoutStderrOutputstream err;
	private Logger loggerOut;
	private Logger loggerErr;
	private final String[] filterSetOut;
	private final String[] filterSetErr;

	/**
	 * @param filterString "Property;Startup"
	 * @param filterSeperator ";"
	 */
	public StdoutStderrRedirector(String filterStringOut, String filterStringErr, String filterSeperator) {
		if (filterStringOut != null) {
			this.filterSetOut = StringPairTokenizer.toArray(filterStringOut, filterSeperator);
		}
		else {
			this.filterSetOut = null;
		}
		if (filterStringErr != null) {
			this.filterSetErr = StringPairTokenizer.toArray(filterStringErr, filterSeperator);
		}
		else {
			this.filterSetErr = null;
		}
	}

	public StdoutStderrRedirector(String[] filterSetOut, String[] filterSetErr) {
		this.filterSetOut = filterSetOut;
		this.filterSetErr = filterSetErr;
	}

	public boolean redirect() {
		if (stdout != System.out) {
			log.severe("No redirecting done, is already redirected");
			return false;
		}
		loggerOut = Logger.getLogger("stdout");
		out = new StdoutStderrOutputstream(this, loggerOut, Level.INFO);
		System.setOut(new PrintStream(out, true));

		loggerErr = Logger.getLogger("stderr");
		err = new StdoutStderrOutputstream(this, loggerErr, Level.SEVERE);
		System.setErr(new PrintStream(err, true));
		return true;
	}
	
	boolean doFilterOut(String msg) {
		if (this.filterSetOut == null || msg == null)
			return false;
		for (String filter: this.filterSetOut) {
			if (msg.contains(filter))
				return true;
		}
		return false;
	}

	boolean doFilterErr(String msg) {
		if (this.filterSetErr == null || msg == null)
			return false;
		for (String filter: this.filterSetErr) {
			if (msg.contains(filter))
				return true;
		}
		return false;
	}

	public void reset() {
		System.setOut(stdout);
		System.setErr(stderr);
	}
}
