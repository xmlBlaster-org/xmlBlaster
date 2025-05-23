export class AccessFilterQos {
  constructor(type, version, query) {
    this.type = type;
    this.version = version || "1.0";
    this.query = query || "";
  }
  getType() { return this.type; }
  getVersion() { return this.version; }
  getQuery() { return this.query; }
  toXml() {
    var str = "<filter type='" + this.getType() + "' version='" + this.getVersion() + "'>";
    str += org.xmlBlaster.util.escapeXml(this.getQuery());
    str += "</filter>";
    return str;
  }
}