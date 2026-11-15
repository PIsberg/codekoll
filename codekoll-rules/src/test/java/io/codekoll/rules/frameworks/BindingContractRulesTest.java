package io.codekoll.rules.frameworks;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

/**
 * CK-SEALED-SUBTYPES-DRIFT and CK-RECORD-BINDING-TRAP (issue #118). Jackson, JAXB, JPA and Spring
 * Boot annotations are local stand-ins: the pack matches by simple name.
 */
class BindingContractRulesTest {

  private static final String JACKSON = """
        @interface JsonSubTypes {
          Type[] value();
          @interface Type {
            Class<?> value();
            String name() default "";
          }
        }
        @interface JsonTypeInfo {
          Id use();
          enum Id { NAME, CLASS, MINIMAL_CLASS, DEDUCTION }
        }
        @interface XmlSeeAlso {
          Class<?>[] value();
        }
      """;

  @Test
  void annotationListMissingAPermittedSubtypeFlagged() {
    RuleTestHarness.assertFixture(new SealedSubtypesDriftRule(), "P1", """
        class P1 {
        %s
          @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
          @JsonSubTypes({ // :: CK-SEALED-SUBTYPES-DRIFT
              @JsonSubTypes.Type(value = Circle.class, name = "circle"),
              @JsonSubTypes.Type(Square.class)
          })
          sealed interface Shape permits Circle, Square, Triangle {}
          record Circle(double r) implements Shape {}
          record Square(double side) implements Shape {}
          record Triangle(double base, double height) implements Shape {}

          @XmlSeeAlso({Card.class}) // :: CK-SEALED-SUBTYPES-DRIFT
          abstract static sealed class Payment permits Card, Wallet {}
          static final class Card extends Payment {}
          static final class Wallet extends Payment {}

          @JsonSubTypes({@JsonSubTypes.Type(Cat.class)}) // :: CK-SEALED-SUBTYPES-DRIFT
          sealed interface Animal permits Mammal {}
          sealed interface Mammal extends Animal permits Cat, Dog {}
          record Cat() implements Mammal {}
          record Dog() implements Mammal {}
        }
        """.formatted(JACKSON));
  }

  @Test
  void completeListsAndClassIdsAllowed() {
    RuleTestHarness.assertFixture(new SealedSubtypesDriftRule(), "N1", """
        class N1 {
        %s
          @JsonSubTypes({@JsonSubTypes.Type(Circle.class), @JsonSubTypes.Type(Square.class)})
          sealed interface Shape permits Circle, Square {}
          record Circle(double r) implements Shape {}
          record Square(double side) implements Shape {}

          @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
          @JsonSubTypes({@JsonSubTypes.Type(Card.class)})
          sealed interface Payment permits Card, Wallet {}
          record Card() implements Payment {}
          record Wallet() implements Payment {}

          @JsonSubTypes({@JsonSubTypes.Type(Cat.class), @JsonSubTypes.Type(Dog.class)})
          sealed interface Animal permits Mammal {}
          sealed interface Mammal extends Animal permits Cat, Dog {}
          record Cat() implements Mammal {}
          record Dog() implements Mammal {}

          @JsonSubTypes({@JsonSubTypes.Type(Base.class)})
          sealed interface Open permits Base, Plugin {}
          record Base() implements Open {}
          non-sealed interface Plugin extends Open {}

          @JsonSubTypes({@JsonSubTypes.Type(Leaf.class)})
          interface NotSealed {}
          record Leaf() implements NotSealed {}
          record Other() implements NotSealed {}
        }
        """.formatted(JACKSON));
  }

  @Test
  void bindingContractTheTypeCannotMeetFlagged() {
    RuleTestHarness.assertFixture(new RecordBindingTrapRule(), "P2", """
        class P2 {
          @interface Entity {}
          @interface ConfigurationProperties {
            String value() default "";
          }
          @Entity // :: CK-RECORD-BINDING-TRAP
          record Customer(long id, String name) {}

          @ConfigurationProperties("app")
          static class AppProperties {
            private final int port = 8080; // :: CK-RECORD-BINDING-TRAP
            private String host = "localhost";
            public String getHost() {
              return host;
            }
            public void setHost(String host) {
              this.host = host;
            }
            public int getPort() {
              return port;
            }
          }

          @ConfigurationProperties("mail")
          static class MailProperties {
            private final String from; // :: CK-RECORD-BINDING-TRAP
            MailProperties() {
              this("noreply@example.com");
            }
            MailProperties(String from) {
              this.from = from;
            }
            public String getFrom() {
              return from;
            }
          }
        }
        """);
  }

  @Test
  void bindableTypesAllowed() {
    RuleTestHarness.assertFixture(new RecordBindingTrapRule(), "N2", """
        import java.util.ArrayList;
        import java.util.List;
        class N2 {
          @interface Entity {}
          @interface Embeddable {}
          @interface ConfigurationProperties {
            String value() default "";
          }
          @interface ConstructorBinding {}
          @Embeddable
          record Address(String street, String city) {}

          @Entity
          static class Customer {
            private long id;
            protected Customer() {}
          }

          @ConfigurationProperties("app")
          record AppProperties(int port, String host) {}

          @ConfigurationProperties("server")
          static class ServerProperties {
            private final int port;
            ServerProperties(int port) {
              this.port = port;
            }
          }

          @ConfigurationProperties("mail")
          static class MailProperties {
            private final String from;
            MailProperties() {
              this("noreply@example.com");
            }
            @ConstructorBinding
            MailProperties(String from) {
              this.from = from;
            }
          }

          @ConfigurationProperties("cache")
          static class CacheProperties {
            private static final int DEFAULT_SIZE = 10;
            private final List<String> names = new ArrayList<>();
            private int size = DEFAULT_SIZE;
            public List<String> getNames() {
              return names;
            }
            public void setSize(int size) {
              this.size = size;
            }
          }
        }
        """);
  }
}
