package io.codekoll.rules.pqc;

import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import io.codekoll.rules.pqc.Classification.Vulnerable;
import io.codekoll.rules.support.RuleContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.jspecify.annotations.Nullable;

/**
 * Recognises places where code selects an algorithm: JCA/JCE factory calls, parameter
 * specifications, TLS configuration and XML signature methods. Only names that are compile-time
 * constants are considered; a name held in a variable is not guessed at.
 */
final class PqcSites {

  /** One algorithm name as written at a site, with its verdict. */
  record Named(String written, Classification classification) {}

  /** A matched site and every classified name it selects. */
  record Request(Tree tree, RequestType type, List<Named> names) {
    Request {
      names = List.copyOf(names);
    }

    List<Named> vulnerable() {
      return names.stream().filter(named -> named.classification() instanceof Vulnerable).toList();
    }

    List<String> vulnerableNames() {
      return vulnerable().stream().map(Named::written).toList();
    }

    /** Role of the first vulnerable name; sites mixing roles do not exist in the site table. */
    Role role() {
      return vulnerable().stream()
          .map(named -> ((Vulnerable) named.classification()).role())
          .findFirst()
          .orElse(Role.KEY_MATERIAL);
    }

    boolean selectsPostQuantum(Family family) {
      return names.stream().anyMatch(named -> named.classification()
          instanceof Classification.PostQuantum pq && pq.family() == family);
    }
  }

  private static final Map<String, RequestType> FACTORIES = Map.of(
      "java.security.Signature", RequestType.SIGNATURE,
      "javax.crypto.KeyAgreement", RequestType.KEY_AGREEMENT,
      "javax.crypto.KEM", RequestType.KEM,
      "javax.crypto.Cipher", RequestType.CIPHER,
      "java.security.KeyPairGenerator", RequestType.KEY_PAIR_GENERATOR,
      "java.security.KeyFactory", RequestType.KEY_FACTORY,
      "java.security.AlgorithmParameters", RequestType.ALGORITHM_PARAMETERS,
      "java.security.AlgorithmParameterGenerator", RequestType.ALGORITHM_PARAMETER_GENERATOR);

  /** Names verified against the JDK 26 implementation classes, not documentation. */
  private static final Map<String, RequestType> TLS_PROPERTIES = Map.of(
      "jdk.tls.namedGroups", RequestType.TLS_NAMED_GROUP,
      "jdk.tls.client.SignatureSchemes", RequestType.TLS_SIGNATURE_SCHEME,
      "jdk.tls.server.SignatureSchemes", RequestType.TLS_SIGNATURE_SCHEME,
      "jdk.tls.client.cipherSuites", RequestType.TLS_CIPHER_SUITE,
      "jdk.tls.server.cipherSuites", RequestType.TLS_CIPHER_SUITE,
      "https.cipherSuites", RequestType.TLS_CIPHER_SUITE);

  private static final String SSL_PARAMETERS = "javax.net.ssl.SSLParameters";
  private static final List<String> SSL_ENDPOINTS =
      List.of("javax.net.ssl.SSLSocket", "javax.net.ssl.SSLServerSocket", "javax.net.ssl.SSLEngine");
  private static final String XML_SIGNATURE_FACTORY = "javax.xml.crypto.dsig.XMLSignatureFactory";
  private static final String NAMED_PARAMETER_SPEC = "java.security.spec.NamedParameterSpec";

  private static final List<String> NAMED_SPEC_FIELDS = List.of("X25519", "X448", "ED25519", "ED448");

  /**
   * Types whose constants name a JOSE algorithm, matched by simple name the way the frameworks pack
   * matches annotations: the analyzed project has the library on its classpath, codekoll does not.
   * {@code SIG} is JJWT's nested registry ({@code Jwts.SIG.RS256}); the {@code JW*Algorithm} names
   * are Nimbus'.
   */
  private static final List<String> JOSE_TYPES =
      List.of("SignatureAlgorithm", "JWSAlgorithm", "JWEAlgorithm", "SIG", "KEY");

  /** Method names a site can have; checked on the javac {@code Name} so non-matches allocate nothing. */
  private static final List<String> SITE_METHODS = List.of("getInstance", "setNamedGroups",
      "setSignatureSchemes", "setCipherSuites", "setEnabledCipherSuites", "setProperty",
      "newSignatureMethod");

  private static final List<String> SPEC_SIMPLE_NAMES = List.of("NamedParameterSpec",
      "ECGenParameterSpec", "ECParameterSpec", "RSAKeyGenParameterSpec", "DSAParameterSpec",
      "DHParameterSpec", "DHGenParameterSpec");

  private static final Vulnerable EC_PARAMETERS = new Vulnerable(Family.EC, Role.KEY_MATERIAL, "EC");
  private static final Vulnerable DH_PARAMETERS =
      new Vulnerable(Family.DH, Role.KEY_ESTABLISHMENT, "DiffieHellman");

  private static final Map<String, Vulnerable> PARAMETER_SPECS = Map.of(
      "java.security.spec.ECGenParameterSpec", EC_PARAMETERS,
      "java.security.spec.ECParameterSpec", EC_PARAMETERS,
      "java.security.spec.RSAKeyGenParameterSpec", new Vulnerable(Family.RSA, Role.KEY_MATERIAL, "RSA"),
      "java.security.spec.DSAParameterSpec", new Vulnerable(Family.DSA, Role.SIGNATURE, "DSA"),
      "javax.crypto.spec.DHParameterSpec", DH_PARAMETERS,
      "javax.crypto.spec.DHGenParameterSpec", DH_PARAMETERS);

  private PqcSites() {}

  /** The request selected at {@code path}'s leaf, if it selects at least one classified name. */
  static Optional<Request> match(TreePath path, RuleContext ctx) {
    Tree leaf = path.getLeaf();
    if (leaf instanceof MethodInvocationTree call) {
      return matchCall(path, call, ctx);
    }
    if (leaf instanceof NewClassTree creation) {
      return matchCreation(path, creation, ctx);
    }
    if (leaf instanceof MemberSelectTree select) {
      return matchConstant(path, select, ctx);
    }
    return Optional.empty();
  }

  /** Whether the method enclosing {@code path} contains a factory call satisfying {@code test}. */
  static boolean enclosingMethodContains(TreePath path, RuleContext ctx, Predicate<Request> test) {
    TreePath method = path;
    while (method != null && !(method.getLeaf() instanceof MethodTree)) {
      method = method.getParentPath();
    }
    if (method == null) {
      return false;
    }
    AtomicBoolean found = new AtomicBoolean();
    new TreePathScanner<Void, Void>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        if (!found.get() && match(getCurrentPath(), ctx).filter(test).isPresent()) {
          found.set(true);
        }
        return super.visitMethodInvocation(node, unused);
      }
    }.scan(method, null);
    return found.get();
  }

  private static Optional<Request> matchCall(TreePath path, MethodInvocationTree call, RuleContext ctx) {
    if (!(call.getMethodSelect() instanceof MemberSelectTree select) || call.getArguments().isEmpty()
        || !isOneOf(select.getIdentifier(), SITE_METHODS)) {
      return Optional.empty();
    }
    List<? extends ExpressionTree> args = call.getArguments();
    TreePath receiver = new TreePath(path, select.getExpression());
    TreePath first = new TreePath(path, args.get(0));
    switch (select.getIdentifier().toString()) {
      case "getInstance" -> {
        RequestType type = FACTORIES.get(staticOwner(receiver, ctx));
        return type == null ? Optional.empty() : request(call, type, single(first, ctx));
      }
      case "setNamedGroups" -> {
        return receiverIs(receiver, ctx, List.of(SSL_PARAMETERS))
            ? request(call, RequestType.TLS_NAMED_GROUP, stringArray(first, ctx))
            : Optional.empty();
      }
      case "setSignatureSchemes" -> {
        return receiverIs(receiver, ctx, List.of(SSL_PARAMETERS))
            ? request(call, RequestType.TLS_SIGNATURE_SCHEME, stringArray(first, ctx))
            : Optional.empty();
      }
      case "setCipherSuites" -> {
        return receiverIs(receiver, ctx, List.of(SSL_PARAMETERS))
            ? request(call, RequestType.TLS_CIPHER_SUITE, stringArray(first, ctx))
            : Optional.empty();
      }
      case "setEnabledCipherSuites" -> {
        return receiverIs(receiver, ctx, SSL_ENDPOINTS)
            ? request(call, RequestType.TLS_CIPHER_SUITE, stringArray(first, ctx))
            : Optional.empty();
      }
      case "setProperty" -> {
        return matchProperty(path, call, receiver, first, ctx);
      }
      case "newSignatureMethod" -> {
        return receiverIs(receiver, ctx, List.of(XML_SIGNATURE_FACTORY))
            ? request(call, RequestType.XML_SIGNATURE_METHOD, single(first, ctx))
            : Optional.empty();
      }
      default -> {
        return Optional.empty();
      }
    }
  }

  private static Optional<Request> matchProperty(TreePath path, MethodInvocationTree call,
      TreePath receiver, TreePath key, RuleContext ctx) {
    String owner = staticOwner(receiver, ctx);
    if (call.getArguments().size() != 2
        || !("java.lang.System".equals(owner) || "java.security.Security".equals(owner))) {
      return Optional.empty();
    }
    @Nullable String property = constantString(key, ctx);
    RequestType type = property == null ? null : TLS_PROPERTIES.get(property);
    @Nullable String value = constantString(new TreePath(path, call.getArguments().get(1)), ctx);
    if (type == null || value == null) {
      return Optional.empty();
    }
    return request(call, type, PqcCatalog.splitList(value));
  }

  private static Optional<Request> matchCreation(TreePath path, NewClassTree creation, RuleContext ctx) {
    Tree created = creation.getIdentifier();
    Name simpleName = created instanceof MemberSelectTree qualified ? qualified.getIdentifier()
        : created instanceof IdentifierTree plain ? plain.getName() : null;
    if (simpleName == null || !isOneOf(simpleName, SPEC_SIMPLE_NAMES)) {
      return Optional.empty();
    }
    String type = ctx.qualifiedNameOf(ctx.typeOf(path));
    if (NAMED_PARAMETER_SPEC.equals(type)) {
      return creation.getArguments().size() == 1
          ? request(creation, RequestType.PARAMETER_SPEC, RequestType.KEY_PAIR_GENERATOR,
              single(new TreePath(path, creation.getArguments().get(0)), ctx))
          : Optional.empty();
    }
    Vulnerable family = PARAMETER_SPECS.get(type);
    if (family == null) {
      return Optional.empty();
    }
    String written = family.canonicalName();
    if ("java.security.spec.ECGenParameterSpec".equals(type) && creation.getArguments().size() == 1) {
      @Nullable String curve = constantString(new TreePath(path, creation.getArguments().get(0)), ctx);
      written = curve == null ? written : curve;
    }
    return Optional.of(new Request(creation, RequestType.PARAMETER_SPEC,
        List.of(new Named(written, family))));
  }

  private static Optional<Request> matchConstant(TreePath path, MemberSelectTree select, RuleContext ctx) {
    Optional<Request> jose = matchJoseConstant(select);
    if (jose.isPresent()) {
      return jose;
    }
    if (!isOneOf(select.getIdentifier(), NAMED_SPEC_FIELDS)) {
      return Optional.empty();
    }
    String field = select.getIdentifier().toString();
    Element element = ctx.trees().getElement(path);
    if (!(element instanceof VariableElement)
        || !(element.getEnclosingElement() instanceof TypeElement owner)
        || !owner.getQualifiedName().contentEquals(NAMED_PARAMETER_SPEC)) {
      return Optional.empty();
    }
    return PqcCatalog.classify(RequestType.KEY_PAIR_GENERATOR, field)
        .map(c -> new Request(select, RequestType.PARAMETER_SPEC, List.of(new Named(
            c instanceof Vulnerable v ? v.canonicalName() : field, c))));
  }

  private static boolean isOneOf(Name name, List<String> candidates) {
    for (String candidate : candidates) {
      if (name.contentEquals(candidate)) {
        return true;
      }
    }
    return false;
  }

  /** {@code SignatureAlgorithm.RS256}, {@code Jwts.SIG.RS256}, {@code JWEAlgorithm.RSA_OAEP_256}. */
  private static Optional<Request> matchJoseConstant(MemberSelectTree select) {
    Name owner;
    if (select.getExpression() instanceof MemberSelectTree qualified) {
      owner = qualified.getIdentifier();
    } else if (select.getExpression() instanceof IdentifierTree plain) {
      owner = plain.getName();
    } else {
      return Optional.empty();
    }
    if (!isOneOf(owner, JOSE_TYPES)) {
      return Optional.empty();
    }
    String constant = select.getIdentifier().toString();
    return PqcCatalog.classify(RequestType.JOSE_SIGNATURE, constant)
        .map(c -> new Request(select, RequestType.JOSE_SIGNATURE, List.of(new Named(constant, c))))
        .or(() -> PqcCatalog.classify(RequestType.JOSE_KEY_MANAGEMENT, constant)
            .map(c -> new Request(select, RequestType.JOSE_KEY_MANAGEMENT,
                List.of(new Named(constant, c)))));
  }

  private static Optional<Request> request(Tree tree, RequestType type, List<String> written) {
    return request(tree, type, type, written);
  }

  /** Classifies {@code written} against {@code catalogType}'s table, reporting as {@code type}. */
  private static Optional<Request> request(Tree tree, RequestType type, RequestType catalogType,
      List<String> written) {
    List<Named> names = new ArrayList<>();
    for (String name : written) {
      PqcCatalog.classify(catalogType, name).ifPresent(c -> names.add(new Named(name, c)));
    }
    return names.isEmpty() ? Optional.empty() : Optional.of(new Request(tree, type, names));
  }

  private static String staticOwner(TreePath receiver, RuleContext ctx) {
    return ctx.trees().getElement(receiver) instanceof TypeElement type
        ? type.getQualifiedName().toString()
        : "";
  }

  private static boolean receiverIs(TreePath receiver, RuleContext ctx, List<String> types) {
    var type = ctx.typeOf(receiver);
    return types.stream().anyMatch(fqn -> ctx.isSubtypeOf(type, fqn));
  }

  private static List<String> single(TreePath arg, RuleContext ctx) {
    @Nullable String value = constantString(arg, ctx);
    return value == null ? List.of() : List.of(value);
  }

  private static @Nullable String constantString(TreePath arg, RuleContext ctx) {
    Tree leaf = arg.getLeaf();
    if (leaf instanceof LiteralTree literal && literal.getValue() instanceof String s) {
      return s;
    }
    if (leaf instanceof IdentifierTree || leaf instanceof MemberSelectTree) {
      if (ctx.trees().getElement(arg) instanceof VariableElement variable
          && variable.getConstantValue() instanceof String s) {
        return s;
      }
    }
    return null;
  }

  /** Constant elements of an inline array, or of a final array variable declared in this unit. */
  private static List<String> stringArray(TreePath arg, RuleContext ctx) {
    TreePath arrayPath = arg;
    if (arg.getLeaf() instanceof IdentifierTree || arg.getLeaf() instanceof MemberSelectTree) {
      if (!(ctx.trees().getElement(arg) instanceof VariableElement variable)
          || !variable.getModifiers().contains(Modifier.FINAL)) {
        return List.of();
      }
      @Nullable TreePath declaration = ctx.trees().getPath(variable);
      if (declaration == null || !(declaration.getLeaf() instanceof VariableTree tree)
          || tree.getInitializer() == null) {
        return List.of();
      }
      arrayPath = new TreePath(declaration, tree.getInitializer());
    }
    if (!(arrayPath.getLeaf() instanceof NewArrayTree array) || array.getInitializers() == null) {
      return List.of();
    }
    List<String> values = new ArrayList<>();
    for (ExpressionTree element : array.getInitializers()) {
      @Nullable String value = constantString(new TreePath(arrayPath, element), ctx);
      if (value != null) {
        values.add(value);
      }
    }
    return values;
  }
}
