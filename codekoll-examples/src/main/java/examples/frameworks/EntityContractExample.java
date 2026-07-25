package examples.frameworks;

/**
 * Example for rule {@code CK-ENTITY-CONTRACT}.
 *
 * <p><b>What is wrong:</b> the {@code buggy} JPA entity is {@code final}. (The annotation
 * stands in for {@code jakarta.persistence.Entity}; codekoll matches by name.)
 *
 * <p><b>What happens at runtime:</b> Hibernate subclasses entities to create lazy-loading
 * proxies — impossible for a final class. It fails at runtime with a provider-specific
 * error far from this declaration, typically the first time a lazy association is accessed.
 *
 * <p><b>How to fix it:</b> make the entity non-final (and keep an accessible no-arg
 * constructor), as {@code fixed} does.
 */
public class EntityContractExample {

  @interface Entity {}

  @Entity // :: CK-ENTITY-CONTRACT
  static final class buggy {
    int id;
  }

  @Entity
  static class fixed {
    int id;

    protected fixed() {
    }
  }
}
