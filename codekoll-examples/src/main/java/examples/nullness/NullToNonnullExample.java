package examples.nullness;

import org.jspecify.annotations.NonNull;

/**
 * Example for rule {@code CK-NULL-TO-NONNULL}.
 *
 * <p><b>What is wrong:</b> {@link #buggy()} passes {@code null} to {@link #register(String)},
 * whose parameter is annotated {@code @NonNull}.
 *
 * <p><b>What happens at runtime:</b> the {@code @NonNull} annotation documents that the
 * method never expects null, so it (and everything it calls) skips null checks by design.
 * The null flows in and surfaces as a {@code NullPointerException} somewhere downstream, far
 * from this call.
 *
 * <p><b>How to fix it:</b> pass a real value, as {@link #fixed()} does.
 */
public class NullToNonnullExample {

  void register(@NonNull String name) {
    // name is guaranteed non-null by contract; no defensive check here.
  }

  public void buggy() {
    register(null); // :: CK-NULL-TO-NONNULL
  }

  public void fixed() {
    register("anonymous");
  }
}
