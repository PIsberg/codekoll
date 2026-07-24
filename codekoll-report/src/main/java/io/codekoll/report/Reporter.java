package io.codekoll.report;

import io.codekoll.api.Finding;
import java.io.PrintWriter;
import java.util.List;

/** Renders findings to an output writer. */
@FunctionalInterface
public interface Reporter {
  void report(List<Finding> findings, PrintWriter out);
}
