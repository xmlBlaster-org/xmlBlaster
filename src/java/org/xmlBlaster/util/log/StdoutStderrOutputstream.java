package org.xmlBlaster.util.log;

import java.io.ByteArrayOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StdoutStderrOutputstream extends ByteArrayOutputStream {
	private StdoutStderrRedirector redirector;
	private String lineSeparator;
	private Logger logger;
	private Level level;

	/**
	 * @param logger The logger to write to
	 * @param level The level at which to write
	 */
	public StdoutStderrOutputstream(StdoutStderrRedirector redirector, Logger logger, Level level) {
		super();
		this.redirector = redirector;
		this.logger = logger;
		this.level = level;
		lineSeparator = System.getProperty("line.separator");
	}

	/**
	 * on flush write the contents of the OutputStream to the logger
	 */
	public void flush() {
		try {
			synchronized (this) {
				super.flush();
				String msg = this.toString();
				super.reset();

				if (msg.length() == 0 || msg.equals(lineSeparator)) {
					return; // no empty records
				}

				String clazz;
				String sourceMethod = "";
				if (level == Level.INFO) {
					if (this.redirector.doFilterOut(msg)) {
						return;
					}
					clazz = "stdout";
					if (msg.indexOf("INFO") != -1) {
						// 09:00:04,820  INFO JdbcDeviceLookup:94 - Established DB connection to jdbc:postgresql://localhost:5432/watchee dbName=watchee
						// Could be from a ConsoleAppender -> stdout -> ending up here
						// e.g. from Checkpoint.java
						// We could check stack hierarchy
						sourceMethod = "ConsoleAppender";
					}
				}
				else {
					if (this.redirector.doFilterErr(msg)) {
						return;
					}
					clazz = "stderr";
				}
				logger.logp(level, clazz, sourceMethod, msg);
			}
		} catch (Throwable e) {
			//int i=0;//  how notify user?
		}
	}
}
