class Logger {
  constructor() {
  }

  /**
   * Shows up in console.info() with a stack trace
   * @param {string} msg The log message
   */
  trace(msg, opts = null) {
    msg = msg || "";
    console.trace(msg);
  }

  /**
   * Shows in debugger console as "verbose"
   * @param {string} msg The log message
   */
  debug(msg, opts = null) {
    msg = msg || "";
    if (console.debug)
      console.debug(msg);
    else
      this.trace(msg);
  }

  /**
   * @param {string} msg The log message
   */
  info(msg, opts = null) {
    msg = msg || "";
    console.info(msg); //  [, obj2, ..., objN]
  }

  /**
   * @param {string} msg The log message
   */
  warn(msg, opts = null) {
    msg = msg || "";
    if (console.warn)
      console.warn(msg);
    else
      this.info("WARN: " + msg);
  }

  /**
   * @param {string} msg The log message
   */
  error(msg, opts = null) {
    msg = msg || "";
    console.error(msg);
  }
}

export const log = new Logger();