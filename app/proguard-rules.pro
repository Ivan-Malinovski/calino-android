# The DAV parsers publish consumer rules where reflection is required. Keep
# this app file intentionally narrow; add rules only for verified R8 failures.

# Biweekly's parser and serializer register component/property scribes by
# concrete runtime Class identity. R8's release optimizations can otherwise
# rewrite that type registry: the minified app then treats valid VEVENTs as
# unreadable and cannot serialize a newly-created event. The debug build does
# not shrink, which is why this must be covered explicitly here.
-keep class biweekly.* { *; }
-keep class biweekly.component.** { *; }
-keep class biweekly.parameter.** { *; }
-keep class biweekly.property.** { *; }
-keep class biweekly.io.* { *; }
-keep class biweekly.io.scribe.** { *; }
-keep class biweekly.io.text.** { *; }
-keep class biweekly.util.** { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# ez-vcard discovers parameter constants and constructs extension values by
# reflection. In particular, Nextcloud emits TYPE=HOME,VOICE on landlines.
-keep class ezvcard.parameter.** extends ezvcard.parameter.VCardParameter {
    public static <fields>;
    <init>(...);
}
