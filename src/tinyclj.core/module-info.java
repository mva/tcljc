module tinyclj.core {
  requires transitive tinyclj.rt;
  // requires transitive java.sql;  // for resultseq-seq
  requires static java.xml;  // for tinyclj.xml
  requires static java.sql;  // for tinyclj.instant
  
  exports tinyclj.core;
  exports tinyclj.core.protocols;
  exports tinyclj.string;
  exports tinyclj.uuid;
  exports tinyclj.math;
  exports tinyclj.java.io;

  exports tinyclj.datafy;
  exports tinyclj.edn;
  exports tinyclj.set;
  exports tinyclj.test;
  exports tinyclj.stacktrace;
  exports tinyclj.walk;
  exports tinyclj.template;
  exports tinyclj.instant;
  exports tinyclj.xml;
  exports tinyclj.zip;

  exports tinyclj.alpha.ptest__term;
  exports tinyclj.alpha.ptest__align;
  exports tinyclj.alpha.ptest__impl;
  exports tinyclj.alpha.ptest__pp;
  exports tinyclj.alpha.ptest;
  exports tinyclj.alpha.pp;
}
