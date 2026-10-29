# Room, WorkManager and the Play Billing library ship their own consumer rules.
# Keep readable crash traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Alert delivery builds an SMTP session reflectively inside the mail library.
-dontwarn javax.naming.**
-dontwarn java.beans.**
-dontwarn org.apache.harmony.**
