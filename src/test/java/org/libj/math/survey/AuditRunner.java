/* Copyright (c) 2020 LibJ
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * You should have received a copy of The MIT License (MIT) along with this
 * program. If not, see <http://opensource.org/licenses/MIT/>.
 */

package org.libj.math.survey;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import org.jboss.byteman.agent.Main;
import org.junit.internal.runners.model.ReflectiveCallable;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.TestClass;
import org.libj.util.ArrayUtil;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.loading.ClassInjector;

public class AuditRunner extends BlockJUnit4ClassRunner {
  private static final String classpath = System.getProperty("java.class.path");

  static {
    try {
      loadClassesInBootstrap("org.libj.math.survey.Rule", "org.libj.math.survey.Result", "org.libj.math.survey.AuditReport");
    }
    catch (final IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Repeatable(Instruments.class)
  public @interface Instrument {
    Class<?>[] value();
  }

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface Instruments {
    Instrument[] value();
  }

  /**
   * Recursively process each sub-path of the specified directory.
   *
   * @param dir The directory to process.
   * @param predicate The predicate defining the test process.
   * @return {@code true} if the specified predicate returned {@code true} for
   *         each sub-path to which it was applied, otherwise {@code false}.
   */
  public static boolean recurseDir(final File dir, final Predicate<File> predicate) {
    final File[] files = dir.listFiles();
    if (files != null)
      for (final File file : files)
        if (!recurseDir(file, predicate))
          return false;

    return predicate.test(dir);
  }

  static JarFile createTempJarFile(final File dir) throws IOException {
    final Path dirPath = dir.toPath();
    final Path zipPath = Files.createTempFile("specialagent", ".jar");
    try (final JarOutputStream jos = new JarOutputStream(new FileOutputStream(zipPath.toFile()))) {
      recurseDir(dir, new Predicate<File>() {
        @Override
        public boolean test(final File t) {
          if (t.isFile()) {
            final Path filePath = t.toPath();
            final String name = dirPath.relativize(filePath).toString();
            try {
              jos.putNextEntry(new ZipEntry(name));
              jos.write(Files.readAllBytes(filePath));
              jos.closeEntry();
            }
            catch (final IOException e) {
              throw new IllegalStateException(e);
            }
          }

          return true;
        }
      });
    }

    final File file = zipPath.toFile();
    file.deleteOnExit();
    return new JarFile(file);
  }

  private static JarFile createJarFileOfSource(final File file) throws IOException {
    final String path = file.getAbsolutePath();
    if (file.isDirectory()) {
      if ("classes".equals(file.getName()))
        return createTempJarFile(file);

      if ("test-classes".equals(file.getName()))
        return createTempJarFile(new File(file.getParent(), "classes"));
    }
    else {
      if (path.endsWith(".jar"))
        return new JarFile(file);

      if (path.endsWith("-tests.jar"))
        return new JarFile(new File(path.substring(0, path.length() - 10) + ".jar"));
    }

    throw new UnsupportedOperationException("Unsupported source path: " + path);
  }

  private static void loadJarsInBootstrap(final Instrumentation instr, final String ... names) throws IOException {
    for (final String name : names) {
      int start = classpath.indexOf("/" + name);
      start = classpath.lastIndexOf(File.pathSeparatorChar, start) + 1;
      int end = classpath.indexOf(File.pathSeparatorChar, start);
      if (end == -1)
        end = classpath.length();

      final String bytemanJarPath = classpath.substring(start, end);
      final JarFile jarFile = createJarFileOfSource(new File(bytemanJarPath));
      instr.appendToBootstrapClassLoaderSearch(jarFile);
    }
  }

  private static void loadClassesInBootstrap(final String ... classNames) throws IOException {
    final Map<String,byte[]> nameToBytes = new HashMap<>();
    for (final String className : classNames) {
      try (final InputStream in = AuditRunner.class.getClassLoader().getResource(className.replace('.', '/').concat(".class")).openStream()) {
        final byte[] bytes = new byte[in.available()];
        in.read(bytes);
        nameToBytes.put(className, bytes);
      }
    }

    ClassInjector.UsingUnsafe.ofBootLoader().injectRaw(nameToBytes);
  }

  private static void loadByteman(final Instrumentation instr, final AuditReport report) throws Exception {
    loadJarsInBootstrap(instr, "byteman", "lang");
    final File rulesFile = report.getRulesFile();
    Main.premain("script:" + rulesFile.getAbsolutePath(), instr);
  }

  private final AuditReport report;

  public AuditRunner(final Class<?> cls) throws Exception {
    super(cls);
//  System.err.println(Class.forName("org.libj.math.survey.Result").getClassLoader());
//  System.err.println(Class.forName("org.libj.math.survey.AuditReport").getClassLoader());
//  System.err.println(Class.forName("org.libj.math.survey.Rule").getClassLoader());
    final Instruments instrs = cls.getAnnotation(Instruments.class);
    if (instrs == null) {
      report = null;
      return;
    }

    final Instrumentation instr = ByteBuddyAgent.install();
    loadJarsInBootstrap(instr, "lang");
    final Instrument[] instruments = instrs.value();
    final Result[] results = new Result[instruments.length + 1];
    final Class<?>[] allTrackedClasses = collateTrackedClasses(results, instruments, 0, 0);
    results[results.length - 1] = new Result(cls, allTrackedClasses);
    this.report = new AuditReport(results, trim(allTrackedClasses, 0, 0));
    loadByteman(instr, report);
  }

  private static Class<?>[] trim(final Class<?>[] classes, final int index, final int depth) {
    if (index == classes.length)
      return new Class<?>[depth];

    final Class<?> cls = classes[index];
    if (cls == null)
      return trim(classes, index + 1, depth);

    final Class<?>[] result = trim(classes, index + 1, depth + 1);
    result[depth] = cls;
    return result;
  }

  private static Class<?>[] collateTrackedClasses(final Result[] results, final Instrument[] instruments, final int index, final int depth) {
    if (index == instruments.length)
      return new Class<?>[depth];

    final Instrument instrument = instruments[index];
    final Class<?>[] trackedClasses = instrument.value();

    results[index] = new Result(trackedClasses[0], trackedClasses);
    final Class<?>[] allClasses = collateTrackedClasses(results, instruments, index + 1, depth + trackedClasses.length);
    for (int i = 0; i < trackedClasses.length; ++i) {
      final Class<?> t = trackedClasses[i];
      if (!ArrayUtil.contains(allClasses, depth, allClasses.length - depth, t))
        allClasses[depth + trackedClasses.length - i - 1] = trackedClasses[i];
    }

    return allClasses;
  }

  @Override
  protected void validateTestMethods(List<Throwable> errors) {
  }

  private void beforeInvoke() {
    if (report != null)
      report.reset();
  }

  @Override
  @Deprecated
  protected TestClass createTestClass(final Class<?> testClass) {
    return new TestClass(testClass) {
      @Override
      protected void scanAnnotatedMembers(final Map<Class<? extends Annotation>,List<FrameworkMethod>> methodsForAnnotations, final Map<Class<? extends Annotation>,List<FrameworkField>> fieldsForAnnotations) {
        super.scanAnnotatedMembers(methodsForAnnotations, fieldsForAnnotations);
        for (final Map.Entry<Class<? extends Annotation>,List<FrameworkMethod>> entry : methodsForAnnotations.entrySet()) {
          final List<FrameworkMethod> value = entry.getValue();
          for (int i = 0; i < value.size(); ++i) {
            value.set(i, new FrameworkMethod(value.get(i).getMethod()) {
              @Override
              public Object invokeExplosively(final Object target, final Object ... params) throws Throwable {
                return new ReflectiveCallable() {
                  @Override
                  protected Object runReflectiveCall() throws Throwable {
                    beforeInvoke();
                    final Method method = getMethod();
                    return method.getParameterCount() == 1 ? method.invoke(target, report) : method.invoke(target);
                  }
                }.run();
              }
            });
          }
        }
      }
    };
  }
}