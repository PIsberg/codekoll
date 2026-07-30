package io.codekoll.rules.resources;

import io.codekoll.engine.testing.RuleTestHarness;
import org.junit.jupiter.api.Test;

class ResourceLeakRuleTest {

  private final ResourceLeakRule rule = new ResourceLeakRule();

  @Test
  void flagsNeverClosedStream() {
    RuleTestHarness.assertFixture(rule, "P1", """
        import java.io.FileInputStream;
        import java.io.IOException;
        class P1 {
          int m(String path) throws IOException {
            FileInputStream stream = new FileInputStream(path); // :: CK-RESOURCE-LEAK
            return stream.read();
          }
        }
        """);
  }

  @Test
  void flagsBareExpressionStatement() {
    RuleTestHarness.assertFixture(rule, "P2", """
        import java.io.FileOutputStream;
        import java.io.IOException;
        class P2 {
          void m(String path) throws IOException {
            new FileOutputStream(path); // :: CK-RESOURCE-LEAK
          }
        }
        """);
  }

  @Test
  void allowsTryWithResources() {
    RuleTestHarness.assertFixture(rule, "N1", """
        import java.io.FileInputStream;
        import java.io.IOException;
        class N1 {
          int m(String path) throws IOException {
            try (FileInputStream stream = new FileInputStream(path)) {
              return stream.read();
            }
          }
        }
        """);
  }

  @Test
  void allowsExplicitCloseInFinally() {
    RuleTestHarness.assertFixture(rule, "N2", """
        import java.io.FileInputStream;
        import java.io.IOException;
        class N2 {
          int m(String path) throws IOException {
            FileInputStream stream = new FileInputStream(path);
            try {
              return stream.read();
            } finally {
              stream.close();
            }
          }
        }
        """);
  }

  @Test
  void allowsDecoratorConsumption() {
    RuleTestHarness.assertFixture(rule, "N3", """
        import java.io.BufferedReader;
        import java.io.FileReader;
        import java.io.IOException;
        class N3 {
          String m(String path) throws IOException {
            try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
              return reader.readLine();
            }
          }
        }
        """);
  }

  @Test
  void allowsReturnedResource() {
    RuleTestHarness.assertFixture(rule, "N4", """
        import java.io.FileInputStream;
        import java.io.IOException;
        class N4 {
          FileInputStream open(String path) throws IOException {
            return new FileInputStream(path);
          }
        }
        """);
  }

  @Test
  void allowsFieldAssignment() {
    RuleTestHarness.assertFixture(rule, "N5", """
        import java.io.FileInputStream;
        import java.io.IOException;
        class N5 implements AutoCloseable {
          private FileInputStream stream;
          void open(String path) throws IOException {
            this.stream = new FileInputStream(path);
          }
          @Override
          public void close() throws IOException {
            stream.close();
          }
        }
        """);
  }

  /**
   * Found in the wild: async-test-lib's TelemetryBridge.activate() registers the bridge and
   * returns it. The caller closes it; the factory is not the owner.
   */
  @Test
  void allowsALocalThatIsReturned() {
    RuleTestHarness.assertFixture(rule, "N10", """
        import java.io.FileInputStream;
        import java.io.IOException;
        class N10 {
          FileInputStream open(String path) throws IOException {
            FileInputStream stream = new FileInputStream(path);
            register(stream);
            return stream;
          }
          void register(FileInputStream stream) {
          }
        }
        """);
  }

  @Test
  void allowsNoOpCloseables() {
    RuleTestHarness.assertFixture(rule, "N6", """
        import java.io.ByteArrayInputStream;
        import java.io.StringWriter;
        class N6 {
          void m(byte[] data) {
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            StringWriter writer = new StringWriter();
            System.out.println(in.available() + writer.toString());
          }
        }
        """);
  }
}
