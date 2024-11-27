@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
open module jssc {
    requires transitive org.scijava.nativelib;
    requires org.slf4j;
    exports jssc;
}
