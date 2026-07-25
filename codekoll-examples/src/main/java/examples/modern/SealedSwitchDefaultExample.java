package examples.modern;

/**
 * Example for rule {@code CK-SEALED-SWITCH-DEFAULT}.
 *
 * <p><b>What is wrong:</b> {@link #buggy} switches over a sealed interface but keeps a
 * {@code default} branch.
 *
 * <p><b>What happens at runtime:</b> nothing — until a {@code Triangle} is added to the
 * sealed hierarchy. Without default, the compiler would flag every non-exhaustive switch
 * as an ERROR, pointing at exactly the code to update. With default, the new shape
 * silently routes to the fallback (area 0.0) and ships that way.
 *
 * <p><b>How to fix it:</b> enumerate all permitted subtypes and delete default, as
 * {@link #fixed} does — the compiler then enforces completeness forever.
 */
public class SealedSwitchDefaultExample {

  sealed interface Shape permits Circle, Square {}

  record Circle(double radius) implements Shape {}

  record Square(double side) implements Shape {}

  public double buggy(Shape shape) {
    return switch (shape) { // :: CK-SEALED-SWITCH-DEFAULT
      case Circle c -> Math.PI * c.radius() * c.radius();
      default -> 0.0;
    };
  }

  public double fixed(Shape shape) {
    return switch (shape) {
      case Circle c -> Math.PI * c.radius() * c.radius();
      case Square s -> s.side() * s.side();
    };
  }
}
