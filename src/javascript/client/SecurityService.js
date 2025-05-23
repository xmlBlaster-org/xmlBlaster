
/**
 * Holds security QoS informations inside a ConnectQos
 */
export class SecurityService {
  constructor() {
    this.user = null;
    this.passwd = null;
    this.type = "htpasswd";
    this.version = "1.0";
  }

  getType() { return this.type; }
  getVersion() { return this.version; }
  getUser() { return this.user; }
  getPasswd() { return this.passwd; }

  dump() {
    var str = "<securityService type='" + this.type + "' version='" + this.version + "'>";
    str += "    <user>" + this.user + "</user>";
    str += "    <passwd>" + this.passwd + "</passwd>";
    str += "  </securityService>";
    return str;
  }
}