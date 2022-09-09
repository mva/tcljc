module tinyclj.rt {
  requires static java.xml;     // for clojure.lang.XMLHandler
  
  exports clojure.lang;
  exports tinyclj.lang;
}
