package examples.frameworks;

/**
 * Example for rule {@code CK-SEALED-SUBTYPES-DRIFT}.
 *
 * <p><b>What is wrong:</b> {@link Event} is sealed and serialized polymorphically with Jackson.
 * {@code Refunded} was added to its {@code permits} clause, and every {@code switch} over
 * {@code Event} was updated because the compiler demanded it; the {@code @JsonSubTypes} list was
 * not, because nothing demanded it. (The annotations are stand-ins for Jackson's; codekoll matches
 * by name.)
 *
 * <p><b>What happens at runtime:</b> writing a {@code Refunded} event works. Reading it back fails
 * with {@code InvalidTypeIdException: Could not resolve type id 'refunded'}, usually in a consumer
 * that was deployed later, the first time a refund is published.
 *
 * <p><b>How to fix it:</b> list every permitted subtype, as {@link FixedEvent} does in
 * {@link #fixed()}.
 */
public class SealedSubtypesDriftExample {

  @interface JsonSubTypes {
    Type[] value();

    @interface Type {
      Class<?> value();

      String name() default "";
    }
  }

  @JsonSubTypes({ // :: CK-SEALED-SUBTYPES-DRIFT
      @JsonSubTypes.Type(value = Placed.class, name = "placed"),
      @JsonSubTypes.Type(value = Shipped.class, name = "shipped")
  })
  sealed interface Event permits Placed, Shipped, Refunded {}

  record Placed(String orderId) implements Event {}

  record Shipped(String orderId) implements Event {}

  record Refunded(String orderId) implements Event {}

  @JsonSubTypes({
      @JsonSubTypes.Type(value = FixedPlaced.class, name = "placed"),
      @JsonSubTypes.Type(value = FixedRefunded.class, name = "refunded")
  })
  sealed interface FixedEvent permits FixedPlaced, FixedRefunded {}

  record FixedPlaced(String orderId) implements FixedEvent {}

  record FixedRefunded(String orderId) implements FixedEvent {}

  public Event buggy() {
    return new Refunded("o-1");
  }

  public FixedEvent fixed() {
    return new FixedRefunded("o-1");
  }
}
