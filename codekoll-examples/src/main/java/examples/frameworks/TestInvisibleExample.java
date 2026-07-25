package examples.frameworks;

/**
 * Example for rule {@code CK-TEST-INVISIBLE}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} is a {@code @Test} method declared private.
 * (The annotation is a stand-in for JUnit's; codekoll matches by name.)
 *
 * <p><b>What happens at runtime:</b> JUnit discovers tests reflectively and requires them
 * non-private, non-static, void. A private @Test is simply not discovered — the suite
 * reports green with this test executing ZERO times. The regression it guarded ships.
 *
 * <p><b>How to fix it:</b> package-private, non-static, void — as {@link #fixed()} is.
 */
public class TestInvisibleExample {

  @interface Test {}

  @Test // :: CK-TEST-INVISIBLE
  private void buggy() {
    assert 1 + 1 == 2;
  }

  @Test
  void fixed() {
    assert 1 + 1 == 2;
  }
}
