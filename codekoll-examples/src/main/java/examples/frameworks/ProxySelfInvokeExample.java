package examples.frameworks;

/**
 * Example for rule {@code CK-PROXY-SELF-INVOKE}.
 *
 * <p><b>What is wrong:</b> {@link #placeOrder()} calls the {@code @Transactional}
 * {@link #chargeCard()} of the same class through plain {@code this}-dispatch. (The
 * annotation stands in for Spring's; codekoll matches by name.)
 *
 * <p><b>What happens at runtime:</b> Spring's transactional proxy wraps the bean, not
 * {@code this}. An internal call goes straight to the implementation and never touches the
 * proxy, so {@code chargeCard} runs with NO transaction — the rollback that should undo a
 * partial charge on failure never happens. Called from outside the bean it works; called
 * internally it silently doesn't.
 *
 * <p><b>How to fix it:</b> move the annotated method to a separate bean and inject it, as
 * {@link #fixed()} sketches with an injected collaborator.
 */
public class ProxySelfInvokeExample {

  @interface Transactional {}

  private PaymentService payments;

  public void placeOrder() {
    chargeCard(); // :: CK-PROXY-SELF-INVOKE
  }

  @Transactional
  void chargeCard() {
    // debit + record; relies on a surrounding transaction that self-invocation skips.
  }

  public void fixed() {
    payments.chargeCard(); // through an injected bean → proxy applies
  }

  interface PaymentService {
    void chargeCard();
  }
}
