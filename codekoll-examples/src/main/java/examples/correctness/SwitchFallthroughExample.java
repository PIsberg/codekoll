package examples.correctness;

/**
 * Example for rule {@code CK-SWITCH-FALLTHROUGH}.
 *
 * <p><b>What is wrong:</b> the {@code GOLD} case in {@link #buggy(String)} has statements
 * but no {@code break}, so it falls into {@code SILVER}.
 *
 * <p><b>What happens at runtime:</b> a GOLD member gets the GOLD discount AND the SILVER
 * discount — two branches run for one input. The bug hides until someone inspects a gold
 * member's total and finds it 5% too low.
 *
 * <p><b>How to fix it:</b> end each case with {@code break} (or use arrow-form switch), as
 * {@link #fixed(String)} does.
 */
public class SwitchFallthroughExample {

  public int buggy(String tier) {
    int discount = 0;
    switch (tier) {
      case "GOLD": // :: CK-SWITCH-FALLTHROUGH
        discount += 10;
      case "SILVER":
        discount += 5;
        break;
      default:
        discount = 0;
    }
    return discount;
  }

  public int fixed(String tier) {
    return switch (tier) {
      case "GOLD" -> 15;
      case "SILVER" -> 5;
      default -> 0;
    };
  }
}
