package fixture;

/** Unregistered fixture used to test rejection before output is written. */
public final class IncompatibleTextProvider extends TextProvider {
    @Override public String id() { return "example-incompatible"; }
    @Override public String contractVersion() { return "2.0"; }
}
