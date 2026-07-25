/**
 * Pack {@code frameworks}: silently ignored code — annotations and logging contracts that
 * compile, run without error, and quietly do nothing. Annotation matching is by simple name
 * so the pack works without the framework on the analysis classpath.
 */
@NullMarked
package io.codekoll.rules.frameworks;

import org.jspecify.annotations.NullMarked;
