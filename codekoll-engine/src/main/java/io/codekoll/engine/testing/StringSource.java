package io.codekoll.engine.testing;

import java.net.URI;
import javax.tools.SimpleJavaFileObject;

/** An in-memory {@code .java} source for the fixture harness. */
public final class StringSource extends SimpleJavaFileObject {

  private final String content;

  public StringSource(String className, String content) {
    super(URI.create("string:///" + className.replace('.', '/') + ".java"), Kind.SOURCE);
    this.content = content;
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) {
    return content;
  }
}
