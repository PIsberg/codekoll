package examples.modern;

/**
 * Example for rule {@code CK-STRUCTURED-GET-BEFORE-JOIN}.
 *
 * <p>The nested {@code StructuredTaskScope}/{@code Subtask} types mirror
 * {@code java.util.concurrent.StructuredTaskScope} (a preview API) so this example compiles
 * on a released JDK without {@code --enable-preview}; the rule matches by name.
 *
 * <p><b>What is wrong:</b> {@code buggy} calls {@code task.get()} before {@code scope.join()}.
 *
 * <p><b>What happens at runtime:</b> a structured-concurrency subtask has no result until the
 * scope has joined. Calling {@code get()} first throws {@code IllegalStateException} every
 * time — fork/join/get ordering is the whole contract.
 *
 * <p><b>How to fix it:</b> join before reading results, as {@code fixed} does.
 */
public class StructuredGetBeforeJoinExample {

  static final class StructuredTaskScope {
    void join() {
    }

    static final class Subtask<T> {
      T get() {
        return null;
      }
    }
  }

  public String buggy(StructuredTaskScope scope, StructuredTaskScope.Subtask<String> task) {
    String result = task.get(); // :: CK-STRUCTURED-GET-BEFORE-JOIN
    scope.join();
    return result;
  }

  public String fixed(StructuredTaskScope scope, StructuredTaskScope.Subtask<String> task) {
    scope.join();
    return task.get();
  }
}
