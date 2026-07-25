package examples.modern;

import java.util.List;

/**
 * Example for rule {@code CK-RECORD-ARRAY-COMPONENT}.
 *
 * <p><b>What is wrong:</b> the {@code buggy} record holds its digest as {@code byte[]}.
 *
 * <p><b>What happens at runtime:</b> records generate equals/hashCode from components —
 * but arrays only have identity equality. Two checksums with byte-for-byte identical
 * digests are NOT equal, hash into different buckets, and print as {@code [B@1a2b3c}. The
 * cache keyed on this record never hits.
 *
 * <p><b>How to fix it:</b> hold an immutable {@code List} (value semantics for free), as
 * the {@code fixed} record does.
 */
public class RecordArrayComponentExample {

  record buggy(String algorithm, byte[] digest) {} // :: CK-RECORD-ARRAY-COMPONENT

  record fixed(String algorithm, List<Byte> digest) {
    fixed {
      digest = List.copyOf(digest);
    }
  }
}
